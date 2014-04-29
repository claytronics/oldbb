#include "clock.bbh"
#include "ensemble.bbh"
#include "data_link.bbh"
#include "log.bbh"
#include <math.h>


#define PRINT_BBSIM(...) printf(__VA_ARGS__)

#define MIN_ID_TIME_LEADER

#define SPEED_ESTIMATION


#define NUM_CALIBRATION	15
#define CALIBRATION_PERIOD (2000)
#define SYNC_PERIOD	(10000)

#define CLOCK_VALIDITY_PERIOD (5*1000)
#define ESTIMATED_TRANSMISSION_DELAY 6

// clock message format:
//	<CLOCK_SYNC_MSG> <TYPE> <parameters ... >

// TIME LEADER MANAGEMENT

void timeLeaderSubRoutine(void);
void setLeader(void);
void setSlave(void);
	
// 	Parameters <id (2 bytes)>
#define MIN_ID_TIME_LEADER_ELECTION_GO_MSG 1
// 	Parameters <id (2 bytes)> <answer (1 byte)>
#define MIN_ID_TIME_LEADER_ELECTION_BACK_MSG 2
#define LEADER_ELECTION_TIMEOUT 500
	
threadvar byte isLeader = 0;
threadvar byte electing = 0;
threadvar uint16_t STlevel = 0;
	
threadvar PRef minIdSender;
threadvar uint16_t minId;
threadvar byte nbNeededAnswers;
threadvar Timeout leaderElectionTimeOut;
	
byte sendBackMsg(PRef p, byte a);
byte sendGoMsg(PRef p, uint16_t id);
byte broadcastGoMsg(PRef p, uint16_t id, uint16_t l);
void startLeaderElection(void);
void scheduleLeaderElection(void);

// CLOCK/SYNCHRONIZATION MANAGEMENT:
#define CLOCK_INFO 3
// 	Parameters [<wave id (2 bytes) >] <send time (4 bytes) > <receive time (4 bytes)>
#define REQUEST_CLOCK_SYNC 4

#define SEND_TIME_INDEX 2
#define RECEIVE_TIME_INDEX (SEND_TIME_INDEX+sizeof(Time))

#ifdef SPEED_ESTIMATION
	//#define NUM_SYNC_DATA 6
	//threadtype typedef struct _syncData {Time globalTime; Time localTime} syncData_t;
	threadvar double speedAvg = 1.0;
	threadvar Time firstCalibSend = 0;
	threadvar unsigned int nbSync = 0;
	//threadvar syncData_t syncData[NUM_SYNC_DATA];
#endif

threadvar int32_t offset = 0;
threadvar Time firstCalibRec = 0;
threadvar Time localClockMaxReach = 0;
threadvar Timer syncTimer;
threadvar uint16_t syncRound = 0;

threadvar PRef syncChildren[NUM_PORTS];
void initSTChildren(void);

void freeClockChunk(void);
byte broadcastClockChunk(PRef excludedPort, byte *d, byte s);
byte sendClockChunk(PRef p, byte *d, byte s);
byte requestSync(PRef p);

void initClock(void)
{
	offset = 0;
	firstCalibRec = 0;
	localClockMaxReach = 0;
	syncRound = 0;
	
#ifdef SPEED_ESTIMATION
	speedAvg = 1.0;
	firstCalibSend = 0;
	nbSync = 0;
#endif

	minIdSender = 255;
	minId = getGUID();
	electing = 0;
	STlevel = 0;
	setSlave();
	scheduleLeaderElection();
}

Time getClockForTime(Time t)
{
#ifdef SPEED_ESTIMATION
	return ((double)t*speedAvg) + offset;
#else
	return t + offset;
#endif
}

Time getEstimatedGlobalClock(void)
{
#ifdef SPEED_ESTIMATION
	return ((double)getTime()*speedAvg) + offset;
#else
	return getTime() + offset;
#endif
}

Time getClock(void) {
#ifdef CLOCK_SYNC
	return fmax(getEstimatedGlobalClock(), localClockMaxReach);
#else
	return getTime();
#endif
}

byte isTimeLeader(void)
{
	return isLeader;
}

byte isElecting(void) {
	return electing;
}

byte handleClockSyncMessage(void)
{
	if (thisChunk == NULL) 
	{
		return 0;
	}
    
	switch(thisChunk->data[1])
	{
		case CLOCK_INFO:
		{
			Time sendTime;
			Time receiveTime;
			Time estimatedGlobalTime;

			//PRINT_BBSIM("block %u: clock info\n", getGUID()); 
			
			sendTime  = (Time)(thisChunk->data[SEND_TIME_INDEX+3]) & 0xFF;
			sendTime |= ((Time)(thisChunk->data[SEND_TIME_INDEX+2]) << 8) & 0xFF00;
			sendTime |= ((Time)(thisChunk->data[SEND_TIME_INDEX+1]) << 16) & 0xFF0000;
			sendTime |= ((Time)(thisChunk->data[SEND_TIME_INDEX]) << 24)  & 0xFF000000;
	
			receiveTime  = (Time)(thisChunk->data[RECEIVE_TIME_INDEX+3]) & 0xFF;
			receiveTime |= ((Time)(thisChunk->data[RECEIVE_TIME_INDEX+2]) << 8) & 0xFF00;
			receiveTime |= ((Time)(thisChunk->data[RECEIVE_TIME_INDEX+1]) << 16) & 0xFF0000;
			receiveTime |= ((Time)(thisChunk->data[RECEIVE_TIME_INDEX]) << 24)  & 0xFF000000;
			
			localClockMaxReach = fmax(getClock(), localClockMaxReach);
			estimatedGlobalTime = sendTime + ESTIMATED_TRANSMISSION_DELAY;

#ifdef LOG_DEBUG
			char s[70];
			snprintf(s, 70*sizeof(char), "s:%lu,r:%lu,c:%lu,l:%u", estimatedGlobalTime, receiveTime, getClockForTime(receiveTime), STlevel);
			s[69] = '\0';
#endif

#ifdef SPEED_ESTIMATION
			nbSync++;
			if (nbSync == 1) {
				offset = estimatedGlobalTime - receiveTime;
				//syncData[0].globalTime = estimatedGlobalTime;
				//syncData[0].localTime = receiveTime;
			} else {
				speedAvg = ((double) (estimatedGlobalTime - firstCalibSend))/ ((double) (receiveTime - firstCalibRec));
				offset = round(estimatedGlobalTime - (speedAvg*((double)getTime())));
			}
						
			//PRINT_BBSIM("block %u: clock info at time %lu (clock %lu), speed average %f, off %d\n", getGUID(), getTime(), getClock(), speedAvg, offset);
#else
			offset = estimatedGlobalTime - receiveTime;
#endif
			synchronizeNeighbors();
			#ifdef LOG_DEBUG
			if ( (rand() % 4) == 0) {
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
			uint16_t id = charToGUID(&(thisChunk->data[2]));
			uint16_t l = charToGUID(&(thisChunk->data[4]));
			
			if (!electing)
			{
				//PRINT_BBSIM("block %u: go msg - election\n", getGUID()); 
				deregisterTimeout(&leaderElectionTimeOut);
				startLeaderElection();
			}
			
			if ((id == minId) && (l >= STlevel))
			{
				sendBackMsg(faceNum(thisChunk), 0);
			}
			if ((id < minId) || ((id == minId) && (l < STlevel)))
			{
				minId = id;
				minIdSender = faceNum(thisChunk);
				initSTChildren();
				STlevel = l;
				nbNeededAnswers = broadcastGoMsg(faceNum(thisChunk), id, l);
				setColor(l);
				if (nbNeededAnswers == 0) 
				{
					electing = 0;
					if (minId == getGUID())
					{
						setLeader();
					}
					else 
					{
						sendBackMsg(faceNum(thisChunk), 1);
					}
				}
			}
			break;
		}
		case MIN_ID_TIME_LEADER_ELECTION_BACK_MSG :
		{
			uint16_t id = charToGUID(&(thisChunk->data[2]));
			
			if (id == minId)
			{
				nbNeededAnswers--;
				syncChildren[faceNum(thisChunk)] = thisChunk->data[4];
				if (nbNeededAnswers == 0)
				{
					electing = 0;
					if (id == getGUID())
					{
						setLeader();
					}
					else
					{
						sendBackMsg(minIdSender, 1);
					}
				}
			}
			break;
		}
	}
	return 1;
}

byte handleNeighborChange(PRef p)
{
	PRINT_BBSIM("Neighbor change at Time %u\n", getTime());
	electing = 0;
	if (!electing)
	{
		scheduleLeaderElection();
	}
	return 0;
}

byte isAClockSyncMessage(Chunk *c)
{	
	if ((*((MsgHandler*)c->handler) == RES_SYS_HANDLER) && (c->data[0] == CLOCK_SYNC_MSG) && (c->data[1] == CLOCK_INFO))
	{
		return 1;
	}
	return 0;
}

/******************************************************
 * Clock (Time) Synchronization Functions
 *****************************************************/

void insertReceiveTime(Chunk *c)
{
	Time t = getTime();
	
	c->data[RECEIVE_TIME_INDEX+3] = (byte) (t & 0xFF);
	c->data[RECEIVE_TIME_INDEX+2] = (byte) ((t >>  8) & 0xFF);
	c->data[RECEIVE_TIME_INDEX+1] = (byte) ((t >> 16) & 0xFF);
	c->data[RECEIVE_TIME_INDEX] = (byte) ((t >> 24) & 0xFF);
}

void insertSendTime(Chunk *c)
{
	Time t = getEstimatedGlobalClock(); // Global Clock

	c->data[SEND_TIME_INDEX+3] = (byte) (t & 0xFF);
	c->data[SEND_TIME_INDEX+2] = (byte) ((t >>  8) & 0xFF);
	c->data[SEND_TIME_INDEX+1] = (byte) ((t >> 16) & 0xFF);
	c->data[SEND_TIME_INDEX] = (byte) ((t >> 24) & 0xFF);
}

byte requestSync(PRef p)
{
	byte data[2];
	
	data[0] = CLOCK_SYNC_MSG;
	data[1] = REQUEST_CLOCK_SYNC;
	
	return sendClockChunk(p, data, 2);
}

byte synchronizeNeighbor(PRef p)
{
	byte data[4];
	
	data[0] = CLOCK_SYNC_MSG;
	data[1] = CLOCK_INFO;
	return sendClockChunk(p, data, 2);
}

byte synchronizeNeighbors(void)
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

byte isSynchronized(void)
{
	return (isTimeLeader() || (firstCalibRec > 0));
	//return (isTimeLeader() || ( (firstCalibRec != 0) && ((getEstimatedLocalClock() - firstCalibRec) < CLOCK_VALIDITY_PERIOD )));
}

void initSTChildren(void)
{
	byte p;
	
	for (p = 0; p < NUM_PORTS; p++)
	{
		syncChildren[p] = 0;
	}
}

void initSyncPoints(void)
{
	byte p;
	
	for (p = 0; p < 6; p++)
	{
		//syncPoints[p] = 0;
	}
}

/******************************************************
 * Time Leader Election Functions
 *****************************************************/

void setLeader(void) {
	#ifdef LOG_DEBUG
	char s[15];
	snprintf(s, 15*sizeof(char), "leader");
	s[14] = '\0';
	printDebug(s);
	#endif
	isLeader = 1;
	syncTimer.t.callback = (GenericHandler)&synchronizeNeighbors;
	PRINT_BBSIM("block %u: Leader\n", getGUID()); 
	//synchronizeNeighbors();
	//registerTimer(&(syncTimer));
	//enableTimer(syncTimer);
}

void setSlave(void) {
	isLeader = 0;
	disableTimer(syncTimer);
	deregisterTimer(&syncTimer);
	deregisterTimeout(&(syncTimer.t));
}

#ifdef MIN_ID_TIME_LEADER
byte sendBackMsg(PRef p, byte a)
{
	byte data[5];
	
	data[0] = CLOCK_SYNC_MSG;
	data[1] = MIN_ID_TIME_LEADER_ELECTION_BACK_MSG;
	GUIDIntoChar(minId, &(data[2]));
	data[4] = a;
	
	return sendClockChunk(p, data, 5);
}

byte sendGoMsg(PRef p, uint16_t id)
{
	byte data[4];
	
	data[0] = CLOCK_SYNC_MSG;
	data[1] = MIN_ID_TIME_LEADER_ELECTION_GO_MSG;
	GUIDIntoChar(id, &(data[2]));
	
	return sendClockChunk(p, data, 4);
}

byte broadcastGoMsg(PRef p, uint16_t id, uint16_t l)
{
	byte data[6];
	
	data[0] = CLOCK_SYNC_MSG;
	data[1] = MIN_ID_TIME_LEADER_ELECTION_GO_MSG;
	GUIDIntoChar(id, &(data[2]));
	GUIDIntoChar(l+1, &(data[4]));
	return broadcastClockChunk(p, data, 6);
}

void scheduleLeaderElection(void)
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

void startLeaderElection(void)
{
	if (!electing) {
		setSlave();
		minId = getGUID();
		minIdSender = 255;
		electing = 1;
		STlevel = 0;
		initSTChildren();
		initSyncPoints();
		nbNeededAnswers = broadcastGoMsg(255, getGUID(),0);
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

#endif

/******************************************************
 * Chunk Management Functions
 *****************************************************/

byte 
sendClockChunk(PRef p, byte *d, byte s)
{
  return sendLogChunk(p, d, s);
}

byte broadcastClockChunk(PRef excludedPort, byte *d, byte s)
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
