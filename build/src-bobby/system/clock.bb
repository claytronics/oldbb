#include "clock.bbh"
#include "ensemble.bbh"
#include "data_link.bbh"
#include "log.bbh"
#include <math.h>

#define PRINT_BBSIM(...) printf(__VA_ARGS__)

threadtype typedef struct _syncData {	Time globalTime;	Time localTime;} syncData_t;

// TIME LEADER MANAGEMENT
threadvar byte isLeader = 0;
threadvar byte electing = 0;
threadvar uint16_t STlevel = 0;
threadvar PRef minIdSender;
threadvar uint16_t minId;
threadvar byte nbNeededAnswers;
threadvar Timeout leaderElectionTimeOut;
threadvar syncData_t maxSystemClock;

static void setLeader(void);
static void setSlave(void);
static byte sendBackMsg(PRef p, byte a, syncData_t m);
static byte broadcastGoMsg(PRef p, uint16_t id, uint16_t l, syncData_t m);
static void startLeaderElection(void);
static void scheduleLeaderElection(void);
static syncData_t setMaxSystemClock(Chunk *c, syncData_t m);
static Time getChunkMaxSystemClock(Chunk *c);
static Time getMaxSystemClock(syncData_t m);
static void insertMaxSystemClock(byte *d, syncData_t m);

static Time getSendTime(Chunk *c);
static Time getReceiveTime(Chunk *c);

// CLOCK/SYNCHRONIZATION MANAGEMENT:
threadvar long int offset = 0;
threadvar Time localClockMaxReach = 0;
threadvar Timer syncTimer;
threadvar long unsigned int syncRound = 0;
threadvar long unsigned int numSync = 0;
threadvar PRef syncChildren[NUM_PORTS];

static void initSTChildren(void);
static void freeClockChunk(void);
static byte broadcastClockChunk(PRef excludedPort, byte *d, byte s);
static byte sendClockChunk(PRef p, byte *d, byte s);
static byte requestSync(PRef p);
static byte synchronizeNeighbor(PRef p);
static byte synchronizeNeighbors(void);

#ifdef SPEED_ESTIMATION
threaddef #define NUM_SYNC_DATA 6
#define SYNC_DATA_PERIOD 30000
threadvar syncData_t syncData[NUM_SYNC_DATA];
static void initSyncData(void);
static double computeSpeedAvg(Time gl, Time ll);
threadvar double speedAvg = 1.0;
#endif

void
initClock(void)
{
	offset = 0;
	localClockMaxReach = 0;
	syncRound = 0;
	numSync = 0;
	
	maxSystemClock.globalTime = 0;
	maxSystemClock.localTime = 0;
#ifdef SPEED_ESTIMATION
	speedAvg = 1.0;
#endif
	minIdSender = 255;
	minId = getGUID();
	electing = 0;
	STlevel = 0;
	setSlave();
	scheduleLeaderElection();
}

Time
getClockForTime(Time t)
{
#ifdef SPEED_ESTIMATION
	return ((double)t*speedAvg) + offset;
#else
	return t + offset;
#endif
}

Time
getEstimatedGlobalClock(void)
{
#ifdef SPEED_ESTIMATION
	return ((double)getTime()*speedAvg) + offset;
#else
	return getTime() + offset;
#endif
}

Time
getClock(void) {
#ifdef CLOCK_SYNC
	return fmax(getEstimatedGlobalClock(), localClockMaxReach);
#else
	return getTime();
#endif
}

byte
isTimeLeader(void)
{
	return isLeader;
}

byte
isElecting(void) {
	return electing;
}

byte
handleClockSyncMessage(void)
{
	if (thisChunk == NULL) 
	{
		return 0;
	}
    
	switch(thisChunk->data[1])
	{
		case CLOCK_INFO:
		{
			Time sendTime = getSendTime(thisChunk);
			Time receiveTime = getReceiveTime(thisChunk);
			Time estimatedGlobalTime;

			//PRINT_BBSIM("block %u: clock info\n", getGUID());			
			localClockMaxReach = fmax(getClock(), localClockMaxReach);
			estimatedGlobalTime = sendTime + ESTIMATED_TRANSMISSION_DELAY;

#ifdef LOG_DEBUG
			char s[70];
			snprintf(s, 70*sizeof(char), "s:%lu,r:%lu,c:%lu,l:%u", estimatedGlobalTime, receiveTime, getClockForTime(receiveTime), STlevel);
			s[69] = '\0';
#endif
			numSync++;
#ifdef SPEED_ESTIMATION
			if (numSync == 1) {
				offset = estimatedGlobalTime - receiveTime;
				syncData[0].globalTime = estimatedGlobalTime;
				syncData[0].localTime = receiveTime;
			} else {
				speedAvg = ((double) (estimatedGlobalTime - syncData[0].globalTime))/ ((double) (receiveTime - syncData[0].localTime));
				//speedAvg = computeSpeedAvg(estimatedGlobalTime, receiveTime);
				offset = round(estimatedGlobalTime - (speedAvg*((double)getTime())));
			}
						
			//PRINT_BBSIM("block %u: clock info at time %lu (clock %lu), speed average %f, off %d\n", getGUID(), getTime(), getClock(), speedAvg, offset);
#else
			offset = estimatedGlobalTime - receiveTime;
#endif
			synchronizeNeighbors();
#ifdef LOG_DEBUG
			if ((rand() % 4) == 0) {
				printDebug(s);
			}			
#endif
			break;
		}		
		case REQUEST_CLOCK_SYNC :
		{	
			// PRINT_BBSIM("block %u: sync request from %u\n", getGUID(), thisNeighborhood.n[faceNum(thisChunk)]);
			if(isSynchronized())
			{
				synchronizeNeighbor(faceNum(thisChunk));
			}
			break;
		}
		case MIN_ID_TIME_LEADER_ELECTION_GO_MSG :
		{
			uint16_t id = charToGUID(&(thisChunk->data[ID_INDEX]));
			uint16_t l = charToGUID(&(thisChunk->data[LEVEL_INDEX]));
			
			maxSystemClock = setMaxSystemClock(thisChunk, maxSystemClock);
			PRINT_BBSIM("block %u: go msg , maxsystemClock: %u\n", getGUID(), getMaxSystemClock());
			if (!electing)
			{
				//PRINT_BBSIM("block %u: go msg - election\n", getGUID()); 
				deregisterTimeout(&leaderElectionTimeOut);
				startLeaderElection();
			}
			
			if ((id == minId) && (l >= STlevel))
			{
				sendBackMsg(faceNum(thisChunk), 0, maxSystemClock);
			}
			if ((id < minId) || ((id == minId) && (l < STlevel)))
			{
				minId = id;
				minIdSender = faceNum(thisChunk);
				initSTChildren();
				STlevel = l;
				nbNeededAnswers = broadcastGoMsg(faceNum(thisChunk), id, l, maxSystemClock);
				if (nbNeededAnswers == 0) 
				{
					electing = 0;
					if (minId == getGUID())
					{
						setLeader();
					}
					else 
					{
						sendBackMsg(faceNum(thisChunk), 1, maxSystemClock);
					}
				}
			}
			break;
		}
		case MIN_ID_TIME_LEADER_ELECTION_BACK_MSG :
		{
			uint16_t id = charToGUID(&(thisChunk->data[ID_INDEX]));
			
			maxSystemClock = setMaxSystemClock(thisChunk, maxSystemClock);
			
			if (id == minId)
			{
				nbNeededAnswers--;
				syncChildren[faceNum(thisChunk)] = thisChunk->data[ANSWER_INDEX];
				if (nbNeededAnswers == 0)
				{
					electing = 0;
					if (id == getGUID())
					{
						setLeader();
					}
					else
					{
						sendBackMsg(minIdSender, 1, maxSystemClock);
					}
				}
			}
			break;
		}
	}
	return 1;
}

byte
handleNeighborChange(PRef p)
{
	PRINT_BBSIM("Neighbor change at Time %u\n", getTime());
	electing = 0;
	if (!electing)
	{
		scheduleLeaderElection();
	}
	return 0;
}

byte
isAClockSyncMessage(Chunk *c)
{	
	if ((*((MsgHandler*)c->handler) == RES_SYS_HANDLER) && (c->data[0] == CLOCK_SYNC_MSG)) {
		switch(c->data[1]) {
			case CLOCK_INFO:
			case MIN_ID_TIME_LEADER_ELECTION_BACK_MSG:
			case MIN_ID_TIME_LEADER_ELECTION_GO_MSG:
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
insertTimeStamp(byte *d, Time t, byte i) {
	d[i+3] = (byte) (t & 0xFF);
	d[i+2] = (byte) ((t >>  8) & 0xFF);
	d[i+1] = (byte) ((t >> 16) & 0xFF);
	d[i] = (byte) ((t >> 24) & 0xFF);
}

static Time
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
	insertTimeStamp(c->data, getEstimatedGlobalClock(), SEND_TIME_INDEX);
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

byte
requestSync(PRef p)
{
	byte data[2];
	
	data[0] = CLOCK_SYNC_MSG;
	data[1] = REQUEST_CLOCK_SYNC;
	
	return sendClockChunk(p, data, 2);
}

static byte
synchronizeNeighbor(PRef p)
{
	byte data[2];
	
	data[0] = CLOCK_SYNC_MSG;
	data[1] = CLOCK_INFO;
	return sendClockChunk(p, data, 2);
}

static byte
synchronizeNeighbors(void)
{
	byte p;
	byte n = 0;
	
	if (isTimeLeader()) {
		syncRound++;
		if(syncRound == NUM_CALIBRATION) {
			syncTimer.period = SYNC_PERIOD;
#ifdef LOG_DEBUG
			char s[10];
			snprintf(s, 10*sizeof(char), "calibOk");
			s[9] = '\0';
			printDebug(s);
#endif
		}
	}
	for( p = 0; p < NUM_PORTS; p++)
	{
		if ((syncChildren[p] == 0) || (thisNeighborhood.n[p] == VACANT))
		{
			continue;
		}
		synchronizeNeighbor(p);
		n++;
	}
	return n;
}

static void
freeClockChunk(void) {
    freeChunk(thisChunk);
}

byte
isSynchronized(void)
{
	return (isTimeLeader() || (numSync > 0));
}

static void
initSTChildren(void)
{
	byte p;
	
	for (p = 0; p < NUM_PORTS; p++)
	{
		syncChildren[p] = 0;
	}
}

static void
initSyncData(void)
{
	byte p;
	
	for (p = 0; p < NUM_SYNC_DATA; p++)
	{
		syncData[p].globalTime = 0;
		syncData[p].localTime = 0;
	}
}

static byte
insertSyncData(Time gl, Time ll)
{
	byte i = 0;
	byte iMin = 0;
	// Insert if:
		// There is empty data points
		// The oldest one was inserted about SYNC_DATA_PERIOD ago
		// Sum will not overflow (TODO)
	
	for (i=1; i<NUM_SYNC_DATA; i++) { // we keep the first point
		if  (syncData[i].globalTime < syncData[iMin].globalTime) {
			iMin = i;
		}
	}
	if ((syncData[iMin].localTime == 0) || ((gl - syncData[iMin].globalTime + SYNC_PERIOD) > SYNC_DATA_PERIOD)) {
		syncData[iMin].globalTime = gl;
		syncData[iMin].localTime = ll;
		return 1;
	} else {
		return 0;
	}
}

static void
printTable()
{
	byte i = 0;
	PRINT_BBSIM("\n");
	for(i=0;i<NUM_SYNC_DATA; i++) {
		PRINT_BBSIM("%u, %u\n", syncData[i].localTime, syncData[i].globalTime);
	}
	PRINT_BBSIM("\n");
}

static double
computeSpeedAvg(Time gl, Time ll)
{
	// Linear Regression:
		// x: local time
		// y: global time
	byte i = 0;
	double xAvg = 0, yAvg = 0;
	double sum1 = 0, sum2 = 0;
	byte inserted = insertSyncData(gl, ll);
	
	for(i=0;i<NUM_SYNC_DATA; i++) {
		if (syncData[i].localTime == 0) {
			break;
		}
		xAvg += syncData[i].localTime;
		yAvg += syncData[i].globalTime;
	}
	if (inserted == 0) {
		xAvg += ll;
		yAvg += gl;
		i++;
	}
	xAvg = xAvg/i;
	yAvg = yAvg/i;
	for (i=0;i<NUM_SYNC_DATA; i++) {
		if (syncData[i].localTime == 0) {
			break;
		}
		sum1 += (syncData[i].localTime - xAvg) * (syncData[i].globalTime - yAvg);
		sum2 += powf(syncData[i].localTime - xAvg,2);
	}
	if (inserted == 0) {
		sum1 += (ll- xAvg) * (gl - yAvg);
		sum2 += powf(ll - xAvg,2);
	}
	printTable();
	PRINT_BBSIM("%g, %g, %u, %u, %u\n", sum1, sum2, (unsigned)inserted, gl, ll);
	return sum1/sum2;
}

/******************************************************
 * Time Leader Election Functions
 *****************************************************/

static void
setLeader(void) {

	// Just in case, but should be the max value of the system (so the local max as well)
	//localClockMaxReach = fmax(getMaxSystemClock(maxSystemClock), localClockMaxReach);
	//offset =  getMaxSystemClock(maxSystemClock) - getTime();
	isLeader = 1;
	syncRound = 0;
	syncTimer.t.callback = (GenericHandler)&synchronizeNeighbors;
	syncTimer.period = CALIBRATION_PERIOD;
	
	#ifdef LOG_DEBUG
			char s[10];
			snprintf(s, 10*sizeof(char), "Leader");
			s[9] = '\0';
			printDebug(s);
	#endif
	
	PRINT_BBSIM("block %u: Leader\n", getGUID()); 
	synchronizeNeighbors();
	registerTimer(&(syncTimer));
	enableTimer(syncTimer);
}

static void
setSlave(void) {
	isLeader = 0;
	disableTimer(syncTimer);
	deregisterTimer(&syncTimer);
	deregisterTimer(&syncTimer);
	deregisterTimeout(&(syncTimer.t));
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
	data[1] = MIN_ID_TIME_LEADER_ELECTION_BACK_MSG;
	GUIDIntoChar(minId, &(data[ID_INDEX]));
	data[ANSWER_INDEX] = a;
	insertMaxSystemClock(data, m);
	return sendClockChunk(p, data, DATA_SIZE);
}

static byte
broadcastGoMsg(PRef p, uint16_t id, uint16_t l, syncData_t m)
{
	byte data[DATA_SIZE];
	
	data[0] = CLOCK_SYNC_MSG;
	data[1] = MIN_ID_TIME_LEADER_ELECTION_GO_MSG;
	GUIDIntoChar(id, &(data[ID_INDEX]));
	GUIDIntoChar(l+1, &(data[LEVEL_INDEX]));
	insertMaxSystemClock(data, m);
	return broadcastClockChunk(p, data, DATA_SIZE);
}

static void
scheduleLeaderElection(void)
{
	if (!electing)
	{
		deregisterTimeout(&leaderElectionTimeOut);
		leaderElectionTimeOut.calltime = getTime() + LEADER_ELECTION_TIMEOUT;
		leaderElectionTimeOut.callback = (GenericHandler)(&startLeaderElection);
		registerTimeout(&leaderElectionTimeOut);
	}
	else
	{
		//printf("too late!\n");
	}
}

static void
startLeaderElection(void)
{
	if (!electing) {
		setSlave();
		minId = getGUID();
		minIdSender = 255;
		electing = 1;
		STlevel = 0;
		numSync = 0;
		syncRound = 0;
		
		initSTChildren();
		initSyncData();
		
		maxSystemClock.localTime = getTime();
		maxSystemClock.globalTime = getEstimatedGlobalClock();

		nbNeededAnswers = broadcastGoMsg(255, getGUID(),0, maxSystemClock);
		if (nbNeededAnswers == 0) 
		{
			electing = 0;
			if (minId == getGUID())
			{
				setLeader();
			}
			else 
			{
				//sendBackMsg(minIdSender, 1);
			}
		}
	}
}

/******************************************************
 * Chunk Management Functions
 *****************************************************/

static byte 
sendClockChunk(PRef p, byte *d, byte s)
{
  Chunk *c=getSystemTXChunk();
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
