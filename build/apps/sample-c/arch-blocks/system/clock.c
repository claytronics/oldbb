# 1 "/home/pthalamy/CMU/build-modif/src-bobby/system/clock.bb"
#include "clock.h"
#include "ensemble.h"
#include "data_link.h"

#include <math.h>

#ifdef LOG_DEBUG
#include "log.h"
#endif

#define PRINT_BBSIM(...) //printf(__VA_ARGS__)
#define PRINT_BBSIM2(...) //printf(__VA_ARGS__)

//#define DO_NOT_START_BEFORE_BEING_SYNCHRONIZED

//#define STATIC_TIME_LEADER
#define MIN_ID_TIME_LEADER

//#define TEST_LINEAR_CORRECTION
//#define	IGNORE_AFTER 10
#define LINEAR_CORRECTION
#define RESET_SLOPE_AFTER 5
//#define REAL_LINEAR_MODEL

#define SYNC_PERIOD	(3000)
#define CLOCK_VALIDITY_PERIOD (5*1000)
#define ESTIMATED_TRANSMISSION_DELAY 6

// clock message format:
//	<CLOCK_SYNC_MSG> <TYPE> <parameters ... >

// TIME LEADER MANAGEMENT

void timeLeaderSubRoutine(void);
void setLeader(void);
void setSlave(void);

#ifdef STATIC_TIME_LEADER
	#define WAVE
#ifdef BBSIM
	#define TIME_LEADER_ID 2
#else
	#define TIME_LEADER_ID 257
#endif

#elif defined MIN_ID_TIME_LEADER
	// 	Parameters <id (2 bytes)>
	#define MIN_ID_TIME_LEADER_ELECTION_GO_MSG 1
	// 	Parameters <id (2 bytes)> <answer (1 byte)>
	#define MIN_ID_TIME_LEADER_ELECTION_BACK_MSG 2
	#define LEADER_ELECTION_TIMEOUT 500

	#define SPANNING_TREE

	 byte isLeader = 0;
	 byte electing = 0;

	 PRef minIdSender;
	 uint16_t minId;
	 byte nbNeededAnswers;
	 Timeout leaderElectionTimeOut;

	byte sendBackMsg(PRef p, byte a);
	byte sendGoMsg(PRef p, uint16_t id);
	byte broadcastGoMsg(PRef p, uint16_t id);
	void startLeaderElection(void);
	void scheduleLeaderElection(void);
#endif

// CLOCK/SYNCHRONIZATION MANAGEMENT:
#define CLOCK_INFO 3
// 	Parameters [<wave id (2 bytes) >] <send time (4 bytes) > <receive time (4 bytes)>
#define REQUEST_CLOCK_SYNC 4

#ifdef WAVE
	#define WAVE_ID_INDEX 2
	#define SEND_TIME_INDEX 4
	#define RECEIVE_TIME_INDEX (SEND_TIME_INDEX+sizeof(Time))
#else
	#define SEND_TIME_INDEX 2
	#define RECEIVE_TIME_INDEX (SEND_TIME_INDEX+sizeof(Time))
#endif

#ifdef LINEAR_CORRECTION
	 double speedAvg = 0.0;
	 Time firstCalibSend = 0;
	 unsigned int nbSync = 0;
#endif

 int32_t offset = 0;
 Time firstCalibRec = 0;
 Time localClockMaxReach = 0;
 Timer syncTimer;

#ifdef WAVE
 uint16_t lastWaveId = 0;
 byte syncBy = NUM_PORTS;
byte launchSynchronizationWave(void);
#elif defined SPANNING_TREE
 PRef syncChildren[NUM_PORTS];
void initSTChildren(void);
#endif

void freeClockChunk(void);
byte broadcastClockChunk(PRef excludedPort, byte *d, byte s);
byte sendClockChunk(PRef p, byte *d, byte s);
byte requestSync(PRef p);

void initClock(void)
{
	offset = 0;
	firstCalibRec = 0;
	localClockMaxReach = 0;

#ifdef LINEAR_CORRECTION
	speedAvg = 1.0;
	firstCalibSend = 0;
	nbSync = 0;
#endif

#ifdef WAVE
	lastWaveId = 0;
	syncBy = NUM_PORTS;
#elif defined SPANNING_TREE
	initSTChildren();
#endif

#ifdef STATIC_TIME_LEADER
	if (isTimeLeader()) {
		setLeader();
	}
#elif defined MIN_ID_TIME_LEADER
	minIdSender = 255;
	minId = getGUID();
	electing = 0;
	setSlave();
	scheduleLeaderElection();
#endif
/*
#ifdef DO_NOT_START_BEFORE_BEING_SYNCHRONIZED
	setColor(WHITE);
	byte data[2];

	data[0] = CLOCK_SYNC_MSG;
	data[1] = REQUEST_CLOCK_SYNC;

	while (!isSynchronized()) {
		broadcastClockChunk(255, data, 2);
		delayMS(6);
	}
#endif */
}

void printSlope(void)
{
/*#ifdef LOG_DEBUG
	char s[150];
	snprintf(s, 150*sizeof(char), "speedAvg: %f", speedAvg);
	s[149] = '\0';
	printDebug(s);
#endif*/
}

Time getClockForTime(Time t)
{
#ifdef LINEAR_CORRECTION
	return ((double)t*speedAvg) + offset;
#else
	return t + offset;
#endif
}

Time getEstimatedGlobalClock(void)
{
#ifdef LINEAR_CORRECTION
	return ((double)getTime()*speedAvg) + offset;
#else
	return getTime() + offset;
#endif
}

Time getClock(void) {
	return fmax(getEstimatedGlobalClock(), localClockMaxReach);
}

byte isTimeLeader(void)
{

#ifdef STATIC_TIME_LEADER
	return (getGUID() == TIME_LEADER_ID);
#elif defined MIN_ID_TIME_LEADER
	return isLeader;
#endif
}

byte isElecting(void) {
#ifdef MIN_ID_TIME_LEADER
	return electing;
#else
	return 0;
#endif
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

			PRINT_BBSIM("block %u: clock info\n", getGUID());
#ifdef WAVE
			uint16_t waveId = 0;
			waveId  = (uint16_t)(thisChunk->data[WAVE_ID_INDEX+1]) & 0xFF;
			waveId |= ((uint16_t)(thisChunk->data[WAVE_ID_INDEX]) << 8) & 0xFF00;

			if (lastWaveId >= waveId) {
				PRINT_BBSIM("ko1 %d < %d\n", (int) lastWaveId, (int) waveId);
				return 1;
			}
			lastWaveId = waveId;
			syncBy = faceNum(thisChunk);
#endif

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
			char s[150];
			snprintf(s, 150*sizeof(char), "s: %lu, r: %lu, c: %lu, sp: %f", estimatedGlobalTime, receiveTime, getClockForTime(receiveTime), speedAvg);
			s[149] = '\0';
			printDebug(s);
#endif

#ifdef LINEAR_CORRECTION
	#ifdef TEST_LINEAR_CORRECTION
		if(nbSync < IGNORE_AFTER) {
	#endif
			nbSync++;
			if ((nbSync == 1) || ( (getTime() - firstCalibRec) < (SYNC_PERIOD/2))) {
				offset = estimatedGlobalTime - receiveTime;
				firstCalibSend = estimatedGlobalTime;
				firstCalibRec = receiveTime;
			} else {
				//double n = (double) nbSync;
				//speedAvg = (speedAvg*(n-1) + observedSpeed) / n;
				speedAvg = ((double) (estimatedGlobalTime - firstCalibSend))/ ((double) (receiveTime - firstCalibRec));
				offset = round(estimatedGlobalTime - (speedAvg*((double)getTime())));
				#ifdef RESET_SLOPE_AFTER
				if ((nbSync % RESET_SLOPE_AFTER) == 0 )
				{
					firstCalibSend = estimatedGlobalTime;
					firstCalibRec = receiveTime;
				}
				#endif
			}

			PRINT_BBSIM("block %u: clock info at time %lu (clock %lu), speed average %f, off %d\n", getGUID(), getTime(), getClock(), speedAvg, offset);

	#ifdef TEST_LINEAR_CORRECTION
		}
	#endif
#else
			offset = estimatedGlobalTime - receiveTime;
#endif
/*#ifdef LOG_DEBUG
			char s[150];
			snprintf(s, 150*sizeof(char), "s: %lu, r: %lu, o: %d, t: %lu, c: %lu", sendTime, receiveTime, offset, getTime(), getClock());
			s[149] = '\0';
			printDebug(s);
#endif*/
			synchronizeNeighbors();
			break;
		}

		case REQUEST_CLOCK_SYNC :
		{	PRINT_BBSIM("block %u: sync request from %u\n", getGUID(), thisNeighborhood.n[faceNum(thisChunk)]);
			if(isSynchronized())
			{
				synchronizeNeighbor(faceNum(thisChunk));
			}
			break;
		}
#ifdef MIN_ID_TIME_LEADER
		case MIN_ID_TIME_LEADER_ELECTION_GO_MSG :
		{
			uint16_t id = charToGUID(&(thisChunk->data[2]));

			if (!electing)
			{
				PRINT_BBSIM("block %u: go msg - election\n", getGUID());
				deregisterTimeout(&leaderElectionTimeOut);
				startLeaderElection();
			}

			if (id == minId)
			{
				sendBackMsg(faceNum(thisChunk), 0);
			}
			if (id < minId)
			{
				minId = id;
				minIdSender = faceNum(thisChunk);
				initSTChildren();
				nbNeededAnswers = broadcastGoMsg(faceNum(thisChunk), id);

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
#endif
	}
	return 1;
}

byte handleNeighborChange(PRef p)
{
	PRINT_BBSIM("Neighbor change at Time %u\n", getTime());
#ifdef MIN_ID_TIME_LEADER
	electing = 0;
	if (!electing)
	{
		scheduleLeaderElection();
	}
#endif

#ifdef WAVE
	if (thisNeighborhood.n[p] == PRESENT)
	{

		PRINT_BBSIM("block %u: isSynchronized? = %u\n", getGUID(), isSynchronized());
		if (isSynchronized()) {
			PRINT_BBSIM("block %u: sync new neighbor %u\n", getGUID(), thisNeighborhood.n[p]);
			//synchronizeNeighbor(p);
		}
		 else {
			PRINT_BBSIM("block %u: request sync %u\n", getGUID(), thisNeighborhood.n[p]);
			requestSync(p);
		}
	}
#endif
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

	//PRINT_BBSIM("insert receive time: %u\n", t);
	c->data[RECEIVE_TIME_INDEX+3] = (byte) (t & 0xFF);
	c->data[RECEIVE_TIME_INDEX+2] = (byte) ((t >>  8) & 0xFF);
	c->data[RECEIVE_TIME_INDEX+1] = (byte) ((t >> 16) & 0xFF);
	c->data[RECEIVE_TIME_INDEX] = (byte) ((t >> 24) & 0xFF);
}

void insertSendTime(Chunk *c)
{
	Time t = getEstimatedGlobalClock(); // Global Clock

	//PRINT_BBSIM("insert send time: %u\n", t);
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

	//return 1;
	return sendClockChunk(p, data, 2);
}

byte synchronizeNeighbor(PRef p)
{
	byte data[4];

	data[0] = CLOCK_SYNC_MSG;
	data[1] = CLOCK_INFO;
#ifdef WAVE
	data[WAVE_ID_INDEX+1] = (byte) (lastWaveId & 0xFF);
	data[WAVE_ID_INDEX] = (byte) ((lastWaveId >>  8) & 0xFF);
	return sendClockChunk(p, data, 4);
#elif defined SPANNING_TREE
	return sendClockChunk(p, data, 2);
#endif
}

byte synchronizeNeighbors(void)
{
	byte p;
	byte n = 0;

	PRINT_BBSIM("block %u: synchronizes its neighbors\n", getGUID());

#ifndef SPANNING_TREE
	for( p = 0; p < NUM_PORTS; p++)
	{
		if ((p == syncBy) || (thisNeighborhood.n[p] == VACANT))
		{
			continue;
		}
		synchronizeNeighbor(p);
		n++;
	}
#else
	for( p = 0; p < NUM_PORTS; p++)
	{
		if ((syncChildren[p] == 0) || (thisNeighborhood.n[p] == VACANT))
		{
			continue;
		}
		synchronizeNeighbor(p);
		n++;
	}
#endif
	return n;
}

byte isSynchronized(void)
{
	return (isTimeLeader() || (firstCalibRec > 0));
	//return (isTimeLeader() || ( (firstCalibRec != 0) && ((getEstimatedLocalClock() - firstCalibRec) < CLOCK_VALIDITY_PERIOD )));
}

#ifdef WAVE
byte launchSynchronizationWave(void)
{
	lastWaveId++;
	return synchronizeNeighbors();
}

#elif defined SPANNING_TREE

void initSTChildren(void)
{
	byte p;

	for (p = 0; p < NUM_PORTS; p++)
	{
		syncChildren[p] = 0;
	}
}
#endif

/******************************************************
 * Time Leader Election Functions
 *****************************************************/

void setLeader(void) {

	PRINT_BBSIM2("block %u: leader!\n", getGUID());
#ifdef MIN_ID_TIME_LEADER
	isLeader = 1;
#endif
	syncTimer.period = SYNC_PERIOD;
#ifdef WAVE
	syncTimer.t.callback = (GenericHandler)&launchSynchronizationWave;
	launchSynchronizationWave();
#elif defined SPANNING_TREE
	syncTimer.t.callback = (GenericHandler)&synchronizeNeighbors;
	synchronizeNeighbors();
#endif
	registerTimer(&(syncTimer));
	enableTimer(syncTimer);
}

void setSlave(void) {
	//PRINT_BBSIM("block %u: de-elected!\n", getGUID());
#ifdef MIN_ID_TIME_LEADER
	isLeader = 0;
#endif
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

byte broadcastGoMsg(PRef p, uint16_t id)
{
	byte data[4];

	data[0] = CLOCK_SYNC_MSG;
	data[1] = MIN_ID_TIME_LEADER_ELECTION_GO_MSG;
	GUIDIntoChar(id, &(data[2]));

	return broadcastClockChunk(p, data, 4);
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
	//static int i = 0;
	//i++;
	//printf("block %u: start election function at %u\n", getGUID(), getTime());
	//printf("%d election\n", i);
	if (!electing) {
	//	printf("block %u: start - election\n", getGUID());
		setSlave();
		minId = getGUID();
		minIdSender = 255;
		electing = 1;
		initSTChildren();
		nbNeededAnswers = broadcastGoMsg(255, getGUID());
		if (nbNeededAnswers == 0)
		{
			electing = 0;
			if (minId == getGUID())
			{
				//printf("block %u: direct win\n", getGUID());
				setLeader();
			}
			else
			{
				sendBackMsg(minIdSender, 1);
			}
		}
	}
}

#endif

/******************************************************
 * Chunk Management Functions
 *****************************************************/

void freeClockChunk(void)
{
	free(thisChunk);
}

byte sendClockChunk(PRef p, byte *d, byte s)
{
	Chunk *c=calloc(sizeof(Chunk), 1);
	if (c == NULL)
	{
		return 0;
	}
	if (sendMessageToPort(c, p, d, s, (MsgHandler)RES_SYS_HANDLER, (GenericHandler)&freeClockChunk) == 0)
	{
		free(c);
		return 0;
	}
	return 1;
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
