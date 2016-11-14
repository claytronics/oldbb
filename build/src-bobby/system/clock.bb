#include "clock.bbh"
#include "ensemble.bbh"
#include "data_link.bbh"
#include "log.bbh"
#include <math.h>

#ifndef BBSIM
#include "util/atomic.h"
#endif

#define PRINT_BBSIM(...) //printf(__VA_ARGS__)

// TEST & DEBUG:
//threaddef #define DISACTIVATE_TOPOLOGY_CHANGE_HANDLING
//threaddef #define NO_LINEAR_REGRESSION

// CLOCK/SYNCHRONIZATION MANAGEMENT:
threadtype typedef struct _syncData {Time globalTime; Time localTime;} syncData_t;
threadtype typedef struct _globalClock {syncData_t syncData[NUM_SYNC_DATA]; long int offset; double speedAvg; Time maxReach; long unsigned int numSync; Time lastSync; long int offsetHW; PRef syncChildren[NUM_PORTS];} globalClock_t;

static byte synchronizeNeighbors(void);
static void initGlobalClock(void);
static void initSyncData(syncData_t *d);
static double computeSpeedAvg(globalClock_t *g, Time gl, Time ll);
static Time getSendTime(Chunk *c);
static Time getReceiveTime(Chunk *c);

threadvar globalClock_t globalClock;
static byte synchronizeNeighbor(PRef p);

// TIME LEADER MANAGEMENT:
threaddef #define UNDEFINED_PARENT 255
threadtype typedef struct _election {byte leader; byte electing; uint16_t id; byte parent; uint16_t level; syncData_t maxSystemClock; byte nbNeededAnswers; Timeout timeOut; Time beginning; Time end; PRef children[NUM_PORTS];} election_t;

threadtype typedef struct _syncProcess {long unsigned int round; Timer timer;} syncProcess_t;

static void scheduleLeaderElection(void);
static void initLeaderElectionVariables(void);
static void initSTChildren(PRef *c);
static void startLeaderElection(void);
static byte sendBackMsg(PRef p, byte a, syncData_t m);
static byte broadcastGoMsg(PRef p, uint16_t id, uint16_t l, syncData_t m);
static void setLeader(void);
static void setSlave(void);
#if 0
static byte isALeaf(byte *c);
#endif

static syncData_t setMaxSystemClock(Chunk *c, syncData_t m);
static Time getChunkMaxSystemClock(Chunk *c);
static Time getMaxSystemClock(syncData_t m);
static void insertMaxSystemClock(byte *d, syncData_t m);

threadvar election_t election;
threadvar syncProcess_t syncProcess;

// CHUNK MANAGEMENT:
static byte broadcastClockChunk(PRef excludedPort, byte *d, byte s);
static void freeClockChunk(void);
static void initClockChunkPool(void);
static byte sendClockChunk(PRef p, byte *d, byte s);

void
initClock(void)
{
#ifndef BBSIM
  ATOMIC_BLOCK(ATOMIC_RESTORESTATE)
  {
#endif
    initGlobalClock();
    initClockChunkPool();
    initLeaderElectionVariables();    
    scheduleLeaderElection();
    PRINT_BBSIM("%u init \n", getGUID());
#ifndef BBSIM
  }
#endif
}

static Time
getClockForTime(globalClock_t *g,Time t)
{
  return  round((double)t*g->speedAvg) + g->offset;
}

static Time
getEstimatedGlobalClock(globalClock_t *g)
{
  return round((double)getTime()*g->speedAvg) + g->offset;
}

static Time
getAClock(globalClock_t *g) {
#ifdef CLOCK_SYNC
  return fmax(getEstimatedGlobalClock(g), g->maxReach);
#else
  return getTime();
#endif  
}

Time
getClock(void) {
  return getAClock(&globalClock);
}

byte
isTimeLeader(void)
{
  return election.leader;
}

uint16_t
getDistanceToTimeLeader(void)
{
  return election.level;
}

byte
isElecting(void) {
  return election.electing;
}

byte
handleClockSyncMessage(void)
{
  if (thisChunk == NULL) 
    {
      return 0;
    }

#ifndef BBSIM
  ATOMIC_BLOCK(ATOMIC_RESTORESTATE)
  {
#endif
	
    switch(thisChunk->data[1])
      {
      case CLOCK_INFO:
	{
	  Time sendTime = getSendTime(thisChunk);
	  Time receiveTime = getReceiveTime(thisChunk);
	  Time estimatedGlobalTime = sendTime + ESTIMATED_TRANSMISSION_DELAY;
	  globalClock_t *g = &globalClock;
          Time currentEstimation = getClockForTime(g,receiveTime);

	  //long int offset; double speedAvg; Time maxReach; long unsigned int numSync; Time lastSync; long int offsetHW; PRef syncChildren[NUM_PORTS];
	  // now, to avoid run backward	
	  g->maxReach = fmax(getAClock(g), g->maxReach);	  
	  g->numSync++;

	  //PRINT_BBSIM("block %u: clock info\n", getGUID());
#ifdef NO_LINEAR_REGRESSION
	  g->offset = estimatedGlobalTime - receiveTime;
	  g->speedAvg = 1.0;
#else
	  if (g->numSync == 1) {
	    g->offset = estimatedGlobalTime - receiveTime;
	    g->speedAvg = 1.0;
	    g->syncData[0].globalTime = estimatedGlobalTime;
	    g->syncData[0].localTime = receiveTime;
	  } else {
	    Time o = estimatedGlobalTime - receiveTime;
	    g->maxReach = fmax(getTime()+o, g->maxReach);
	    computeSpeedAvg(g, estimatedGlobalTime, receiveTime);
	  }
#endif
	  // constant offset, no skew
	  //g->offsetHW = estimatedGlobalTime - receiveTime;	
	  // constant skew, no drift
	  g->offsetHW = estimatedGlobalTime - round(g->speedAvg*(double)receiveTime);

	  synchronizeNeighbors();

#ifdef LOG_DEBUG
	  if ((rand() % 4) == 0) {
	    char s[90];
	    snprintf(s, 90*sizeof(char), "s:%lu,r:%lu,c:%lu,d:%lu,l:%u", estimatedGlobalTime, receiveTime, currentEstimation, g->lastSync,(byte)election.level);
	    s[89] = '\0';
	    printDebug(s);
	  }
#endif
          g->lastSync = receiveTime;
	  break;
	}
      case TIME_LEADER_ELECTION_GO_MSG :
	{
	  uint16_t id = charToGUID(&(thisChunk->data[ID_INDEX]));
	  uint16_t l = charToGUID(&(thisChunk->data[LEVEL_INDEX]));
	  
	  PRINT_BBSIM("%u GO MESSAGE FROM %u\n", getGUID(),id);
	  
	  election.maxSystemClock = setMaxSystemClock(thisChunk, election.maxSystemClock);
	  if (!election.electing)
	    {
	      deregisterTimeout(&election.timeOut);
	      if(getGUID() < id) {		
		startLeaderElection();
	      } else {
	        initLeaderElectionVariables();
	      	election.electing = 1;
	      }
	    }
			
	  if (id == election.id)
	    {
	      sendBackMsg(faceNum(thisChunk), 0, election.maxSystemClock);
	    }
	  if (id < election.id)
	    {
	      election.id = id;
	      election.parent = faceNum(thisChunk);
	      initSTChildren(election.children);
	      initSTChildren(globalClock.syncChildren);
	      
	      election.level = l;
	      election.nbNeededAnswers = broadcastGoMsg(faceNum(thisChunk), id, l, election.maxSystemClock);
	      if (election.nbNeededAnswers == 0)
		{
		  election.electing = 0;
		  if (election.id == getGUID())
		    {
		      setLeader();
		    }
		  else 
		    {
		      sendBackMsg(faceNum(thisChunk), 1, election.maxSystemClock);
		    }
		}
	    }
	  break;
	}
      case TIME_LEADER_ELECTION_BACK_MSG :
	{
	  uint16_t id = charToGUID(&(thisChunk->data[ID_INDEX]));
	  
	  PRINT_BBSIM("%u BACK MESSAGE FOR %u\n", getGUID(),id);

	  election.maxSystemClock = setMaxSystemClock(thisChunk, election.maxSystemClock);
	  if (id == election.id)
	    {
	      election.nbNeededAnswers--;
	      election.children[faceNum(thisChunk)] = thisChunk->data[ANSWER_INDEX];
	      globalClock.syncChildren[faceNum(thisChunk)] = thisChunk->data[ANSWER_INDEX];
	      
	      if (election.nbNeededAnswers == 0)
		{
		  election.electing = 0;
		  if (id == getGUID())
		    {
		      setLeader();
		    }
		  else
		    {
		      sendBackMsg(election.parent, 1, election.maxSystemClock);
		    }
		}
	    }
	  break;
	}
      default:
	PRINT_BBSIM("%u unknown clock message received\n", getGUID());
      }
    
#ifndef BBSIM
  }
#endif
  return 1;
}

byte
handleNeighborChange(PRef p)
{
  PRINT_BBSIM("Neighbor change at Time %u\n", getTime());

#ifndef DISACTIVATE_TOPOLOGY_CHANGE_HANDLING 
  election.electing = 0;
  if (!election.electing) {
    scheduleLeaderElection();
  }
#endif
  return 0;
}

byte
isAClockSyncMessage(Chunk *c)
{	
  if ((*((MsgHandler*)c->handler) == RES_SYS_HANDLER) && (c->data[0] == CLOCK_SYNC_MSG)) {
    switch(c->data[1]) {
    case CLOCK_INFO:
    case TIME_LEADER_ELECTION_BACK_MSG:
    case TIME_LEADER_ELECTION_GO_MSG:
      return 1;
      break;
    }
  }
  return 0;
}

/******************************************************
 * Clock (Time) Synchronization Functions
 *****************************************************/

static void
initGlobalClock() {
  
  globalClock_t *g = &globalClock;
  //long int offset; double speedAvg; Time maxReach; long unsigned int numSync; Time lastSync; long int offsetHW; PRef syncChildren[NUM_PORTS];
  g->offset = 0;
  g->speedAvg = 1.0;
  g->maxReach = getTime();
  g->numSync = 0;
  g->lastSync = 0;
  g->offsetHW = 0;
  initSTChildren(g->syncChildren);
  initSyncData(g->syncData);
}

void
insertTimeStamp(byte *d, Time t, byte i) {
  d[i+3] = (byte) (t & 0xFF);
  d[i+2] = (byte) ((t >>  8) & 0xFF);
  d[i+1] = (byte) ((t >> 16) & 0xFF);
  d[i] = (byte) ((t >> 24) & 0xFF);
}

Time
getTimeStamp(byte *d, byte i) {
	
  Time t = 0;
			
  t = (Time)(d[i+3]) & 0xFF;
  t |= ((Time)(d[i+2]) << 8) & 0xFF00;
  t |= ((Time)(d[i+1]) << 16) & 0xFF0000;
  t |= ((Time)(d[i]) << 24)  & 0xFF000000;
  return t;
}

void
insertReceiveTime(Chunk *c)
{
  insertTimeStamp(c->data, getTime(), RECEIVE_TIME_INDEX);
}

void
insertSendTime(Chunk *c)
{
  // Global Clock
  globalClock_t *g = &globalClock;
  
  // offset constant, no skew
  //Time e = getTime() + g->offsetHW;
  // skew constant, no drift
  Time e = round(g->speedAvg*(double)getTime()) + g->offsetHW;

  insertTimeStamp(c->data, e, SEND_TIME_INDEX);
}

static Time
getSendTime(Chunk *c)
{
  return getTimeStamp(c->data, SEND_TIME_INDEX);
}

static Time
getReceiveTime(Chunk *c)
{
  return getTimeStamp(c->data, RECEIVE_TIME_INDEX);
}

#if 0
byte
requestSync(PRef p)
{
  byte data[2];
	
  data[0] = CLOCK_SYNC_MSG;
  data[1] = REQUEST_CLOCK_SYNC;
	
  return sendClockChunk(p, data, 2);
}
#endif

static byte
synchronizeNeighbor(PRef p)
{
  byte size = 2;
  byte data[size];
  
  data[0] = CLOCK_SYNC_MSG;
  data[1] = CLOCK_INFO;

  if (thisNeighborhood.n[p] != VACANT)
    return sendClockChunk(p, data, size);
  else
    return 0;
}

static byte
synchronizeNeighbors(void)
{
  byte p;
  byte n = 0;

  PRINT_BBSIM("%u synchronize neighbors\n",getGUID());
  if (isTimeLeader()) {
    syncProcess.round++;
    if(syncProcess.round == NUM_CALIBRATION) {
      syncProcess.timer.period = CRUISE_PERIOD;
    }
  }

  globalClock_t *g = &globalClock;	

  for( p = 0; p < NUM_PORTS; p++)
    {
      if ((g->syncChildren[p] == 0) || (thisNeighborhood.n[p] == VACANT))
	{
	  continue;
	}
      synchronizeNeighbor(p);	
      n++;
    }
  return n;
}

#if 0
byte isALeaf(byte *c) {
  int p;
  for( p = 0; p < NUM_PORTS; p++) {
    if (c[p])
      {
	return 0;
      }
  }
  return 1;
}
#endif

byte
isSynchronized(void)
{
  globalClock_t *g = &globalClock;	
  return (isTimeLeader() || (g->numSync > 0));
}

static void
initSTChildren(PRef *c)
{
  byte p;
	
  for (p = 0; p < NUM_PORTS; p++)
    {
      c[p] = 0;
    }
}

/**********
 * LINEAR REGRESSION
 ***********/

static void
initSyncData(syncData_t *d)
{
  byte p;
	
  for (p = 0; p < NUM_SYNC_DATA; p++)
    {
      d[p].globalTime = 0;
      d[p].localTime = 0;
    }
}

static byte
insertSyncData(globalClock_t *g, Time gl, Time ll)
{
  byte i = 0;
  byte iMin = 0;
  // Insert if:
  // There is empty data points
  // Sum will not overflow (TODO)
	
  for (i=0; i<NUM_SYNC_DATA; i++) {
    if  (g->syncData[i].globalTime < g->syncData[iMin].globalTime) {
      iMin = i;
    }
  }
  g->syncData[iMin].globalTime = gl;
  g->syncData[iMin].localTime = ll;
  return 1;
}

#if 0
static void
printTable(globalClock_t *g)
{
  byte i = 0;
  PRINT_BBSIM("\n");
  for(i=0;i<NUM_SYNC_DATA; i++) {
#ifdef LOG_DEBUG
    char s[100];
    snprintf(s, 100*sizeof(char),"%lu, %lu\n", g->syncData[i].localTime, g->syncData[i].globalTime);
    s[99] = '\0';
    printDebug(s);
#endif
    PRINT_BBSIM("%u, %u\n", g->syncData[i].localTime, g->syncData[i].globalTime);
  }
  PRINT_BBSIM("\n");
}
#endif

static double
computeSpeedAvg(globalClock_t *g, Time gl, Time ll)
{
  // Linear Regression:
  // x: local time
  // y: global time
  byte i = 0;
  double xAvg = 0, yAvg = 0;
  double sum1 = 0, sum2 = 0;

  insertSyncData(g, gl, ll);
	
  for(i=0;i<NUM_SYNC_DATA; i++) {
    if (g->syncData[i].localTime == 0) {
      break;
    }
    xAvg += g->syncData[i].localTime;
    yAvg += g->syncData[i].globalTime;
  }

  xAvg = xAvg/i;
  yAvg = yAvg/i;
  for (i=0;i<NUM_SYNC_DATA; i++) {
    if (g->syncData[i].localTime == 0) {
      break;
    }
    sum1 += (g->syncData[i].localTime - xAvg) * (g->syncData[i].globalTime - yAvg);
    sum2 += powf(g->syncData[i].localTime - xAvg,2);
  }

  g->speedAvg = sum1/sum2;
  g->offset = yAvg - g->speedAvg * xAvg;
  return g->speedAvg;
}

/******************************************************
 * Time Leader Election
 *****************************************************/

static void
setLeader(void) {
  // Just in case, but should be the max value of the system (so the local max as well)
  //localClockMaxReach = fmax(getMaxSystemClock(maxSystemClock), getClock());
  //initGlobalClock();

  election.leader = 1;
  syncProcess.round = 0;

  syncProcess.timer.t.callback = (GenericHandler)&synchronizeNeighbors;
  syncProcess.timer.period = CALIBRATION_PERIOD;

  PRINT_BBSIM("block %u: Leader\n", getGUID());
  
  synchronizeNeighbors();
  registerTimer(&(syncProcess.timer));
  enableTimer(syncProcess.timer);

}

static void
setSlave(void) {
  election.leader = 0;
  disableTimer(syncProcess.timer);
  deregisterTimer(&syncProcess.timer);
  deregisterTimer(&syncProcess.timer);
  deregisterTimeout(&(syncProcess.timer.t));
}

static void
scheduleLeaderElection(void)
{
  if (!election.electing)
    {
      deregisterTimeout(&election.timeOut);
      //srand(getGUID()+getTime());
      election.timeOut.calltime = getTime() + LEADER_ELECTION_TIMEOUT; // + rand()%50;	
      election.timeOut.callback = (GenericHandler)(&startLeaderElection);
      registerTimeout(&election.timeOut);
    }
  else
    {
      //printf("too late!\n");
    }
}

static Time
getMaxSystemClock(syncData_t m)
{
  int32_t off = getTime() - m.localTime;
  return m.globalTime + off;
}

static Time
getChunkMaxSystemClock(Chunk *c)
{
  return getTimeStamp(c->data, MAX_CLOCK_INDEX);
}

static void
insertMaxSystemClock(byte *d, syncData_t m) {
  insertTimeStamp(d, getMaxSystemClock(m), MAX_CLOCK_INDEX);
}

static syncData_t
setMaxSystemClock(Chunk *c, syncData_t m)
{
  Time receiveTime = getReceiveTime(c);
  Time estimatedMaxSystemClock = getChunkMaxSystemClock(c) + ESTIMATED_TRANSMISSION_DELAY;
		
  // Assume that the clocks are going at the same speed. Realistic on a short interval
  if(estimatedMaxSystemClock > getMaxSystemClock(m)) {
    m.localTime = receiveTime;
    m.globalTime = estimatedMaxSystemClock;
  }
  return m;
}

static byte
sendBackMsg(PRef p, byte a, syncData_t m)
{
  byte data[DATA_SIZE];
	
  data[0] = CLOCK_SYNC_MSG;
  data[1] = TIME_LEADER_ELECTION_BACK_MSG;
  GUIDIntoChar(election.id, &(data[ID_INDEX]));
  data[ANSWER_INDEX] = a;
  insertMaxSystemClock(data, m);
  return sendClockChunk(p, data, DATA_SIZE);
}

static byte
broadcastGoMsg(PRef p, uint16_t id, uint16_t l, syncData_t m)
{
  byte data[DATA_SIZE];
	
  data[0] = CLOCK_SYNC_MSG;
  data[1] = TIME_LEADER_ELECTION_GO_MSG;
  GUIDIntoChar(id, &(data[ID_INDEX]));
  GUIDIntoChar(l+1, &(data[LEVEL_INDEX]));
  insertMaxSystemClock(data, m);
  return broadcastClockChunk(p, data, DATA_SIZE);
}


static void
initLeaderElectionVariables(void) {
  //if (!election.electing) {
  election.parent = UNDEFINED_PARENT;
  election.electing = 0;
  election.id = getGUID();
  election.level = 0;
  setSlave();
  initSTChildren(election.children);
  election.maxSystemClock.localTime = getTime();
  election.maxSystemClock.globalTime = getEstimatedGlobalClock(&globalClock);
  // reinit clock
  initGlobalClock();
  //}
}

static void
startLeaderElection(void)
{
  if (!election.electing) {
    initLeaderElectionVariables();
    election.electing = 1;
    election.nbNeededAnswers = broadcastGoMsg(255, getGUID(),0, election.maxSystemClock);
    if (election.nbNeededAnswers == 0) 
      {
	election.electing = 0;
	if (election.id == getGUID())
	  {
	    setLeader();
	  }
	else 
	  {
	    //sendBackMsg(parent, 1);
	  }
      }
  }
}

/******************************************************
 * Chunk Management Functions
 *****************************************************/
threaddef #define NUM_CLOCK_CHUNK 50

threadvar Chunk clockChunkPool[NUM_CLOCK_CHUNK];

static void
initClockChunkPool(void) {
  byte i = 0;
  for (i = 0; i < NUM_CLOCK_CHUNK; i++) {
    clockChunkPool[i].status = CHUNK_FREE;
    clockChunkPool[i].next = NULL;
  }
}

static void
freeClockChunk(void) {
  if(chunkResponseType(thisChunk) != MSG_RESP_ACK){
    //setColor(PINK);
  }
  freeChunk(thisChunk);
  thisChunk->status = CHUNK_FREE;
}

static Chunk*
getClockChunk(void) {
  byte i = 0;
  Chunk *c = NULL;
  for(i = 0; i < NUM_CLOCK_CHUNK ; i++) {
    c = &clockChunkPool[i];
    if(!chunkInUse(c)) {
      // indicate in use
      c->status = CHUNK_USED;
      c->next = NULL;
      return c;
    }
  }
  return NULL;
}

static byte
sendClockChunk(PRef p, byte *d, byte s)
{
  //Chunk *c=getSystemTXChunk();
  Chunk *c =getClockChunk();
  
  if (c == NULL) {
    return 0;
  }
  if (sendMessageToPort(c, p, d, s, (MsgHandler)RES_SYS_HANDLER, (GenericHandler)&freeClockChunk) == 0) {
    freeChunk(c);
    return 0;
  }
  
  return 1;
}


static byte
broadcastClockChunk(PRef excludedPort, byte *d, byte s)
{
  byte p;
  byte sent = 0;
  
  for( p = 0; p < NUM_PORTS; p++)
    {
      if ((p == excludedPort) || (thisNeighborhood.n[p] == VACANT))
	{
	  continue;
	}
      if(sendClockChunk(p, d, s)) {
	sent++;
      }
    }
  return sent;
}
