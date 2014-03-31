#include "clock.bbh"
#include "ensemble.bbh"
#include "data_link.bbh"

//#define STATIC_TIME_LEADER
#define MIN_ID_TIME_LEADER

#define TIME_SYNC_PERIOD (5*1000)

#define ESTIMATED_TRANSMISSION_DELAY 6

// clock message format:
//	<CLOCK_SYNC_MSG> <TYPE> <parameters ... >

#ifdef STATIC_TIME_LEADER
	#define TIME_LEADER_ID 281
#elif defined MIN_ID_TIME_LEADER
	// 	Parameters <id (2 bytes)>
	#define MIN_ID_TIME_LEADER_ELECTION_GO_MSG 1
	// 	Parameters <id (2 bytes)> <answer (1 byte)>
	#define MIN_ID_TIME_LEADER_ELECTION_BACK_MSG 2
	#define LEADER_ELECTION_TIMEOUT 100
	
	threadvar byte isLeader = 0;
	threadvar byte electing = 0;
	
	threadvar PRef minIdSender;
	threadvar uint16_t minId;
	threadvar byte nbNeededAnswers;
	threadvar Timeout leaderElectionTimeOut;
	
	byte sendBackMsg(PRef p, byte a);
	byte sendGoMsg(PRef p, uint16_t id);
	byte broadcastGoMsg(PRef p, uint16_t id);
	void startLeaderElection(void);
	void scheduleLeaderElection(void);
#endif

#define CLOCK_INFO 3
// 	Parameters <wave id> <send time 4 bytes > <receive time 4 bytes>
#define SEND_TIME_INDEX 3
#define RECEIVE_TIME_INDEX (SEND_TIME_INDEX+sizeof(Time))

threadvar int offset = 0;
threadvar byte lastWaveId = 0;
threadvar byte syncBy = NUM_PORTS;
threadvar Timeout syncTimeOut;

void timeLeaderSubRoutine(void);

void freeClockChunk(void);
byte broadcastClockChunk(PRef excludedPort, byte *d, byte s);
byte sendClockChunk(PRef p, byte *d, byte s);

void initClock(void)
{
	offset = 0;
	lastWaveId = 0;
	syncBy = NUM_PORTS;

#ifdef STATIC_TIME_LEADER
	if (isTimeLeader()) {
		timeLeaderSubRoutine();
		//syncTimeOut.calltime = getClock() + TIME_SYNC_PERIOD;
		//syncTimeOut.callback = (GenericHandler)(&launchSynchronizationWave);
		//registerTimeout(&syncTimeOut);
	}
#elif defined MIN_ID_TIME_LEADER
	minIdSender = 255;
	minId = getGUID();
	electing = 0;
	isLeader = 0;
	scheduleLeaderElection();
#endif
} 

Time getClock(void)
{
	return (getTime() - offset);
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
			
			if (lastWaveId >= thisChunk->data[2]) {
				////printf("ko1 %d < %d\n", (int) lastWaveId, (int) thisChunk->data[1]);
				return 0;
			}
			
			lastWaveId = thisChunk->data[2];
			syncBy = faceNum(thisChunk);
			
			sendTime  = (Time)(thisChunk->data[SEND_TIME_INDEX+3]) & 0xFF;
			sendTime |= ((Time)(thisChunk->data[SEND_TIME_INDEX+2]) << 8) & 0xFF00;
			sendTime |= ((Time)(thisChunk->data[SEND_TIME_INDEX+1]) << 16) & 0xFF0000;
			sendTime |= ((Time)(thisChunk->data[SEND_TIME_INDEX]) << 24)  & 0xFF000000;
	
			receiveTime  = (Time)(thisChunk->data[RECEIVE_TIME_INDEX+3]) & 0xFF;
			receiveTime |= ((Time)(thisChunk->data[RECEIVE_TIME_INDEX+2]) << 8) & 0xFF00;
			receiveTime |= ((Time)(thisChunk->data[RECEIVE_TIME_INDEX+1]) << 16) & 0xFF0000;
			receiveTime |= ((Time)(thisChunk->data[RECEIVE_TIME_INDEX]) << 24)  & 0xFF000000;
	
			offset = (sendTime + ESTIMATED_TRANSMISSION_DELAY) - receiveTime;
	
			synchronizeNeighbors();
			break;
		}
#ifdef MIN_ID_TIME_LEADER
		case MIN_ID_TIME_LEADER_ELECTION_GO_MSG :
		{
			uint16_t id = charToGUID(&(thisChunk->data[2]));
			
			if (!electing)
			{
				//printf("block %u: go msg - election\n", getGUID()); 
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
				nbNeededAnswers = broadcastGoMsg(faceNum(thisChunk), id);
				if (nbNeededAnswers == 0) 
				{
					electing = 0;
					if (minId == getGUID())
					{
						timeLeaderSubRoutine();
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
				if (nbNeededAnswers == 0)
				{
					electing = 0;
					if (id == getGUID())
					{
						timeLeaderSubRoutine();
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
	
	//printf("Neighbor change at Time %u\n", getTime());
	electing = 0;
	if (!electing)
	{
		//printf("block %u: neighbor change - election\n", getGUID()); 
		//startLeaderElection();
		scheduleLeaderElection();
	}
	
	if (thisNeighborhood.n[p] == VACANT)
	{
		//printf("Vacant\n");
		//return 0;
	}
	
	// No guarantee that it will work
	/* else {
		if (sendGoMsg(p))
		{
			nbNeededAnswers++;
		}
	}*/
	
	/*
	if (isTimeLeader())
	{
		if (lastWaveId == 0)
		{
			lastWaveId++;
		}
		synchronizeNeighbor(p);
		//printf("Neighbor sync M %d\n", lastWaveId);
		return 1;
	} else if (lastWaveId > 0)
	{
		//printf("Neighbor sync\n");
		synchronizeNeighbor(p);
		return 1;
	}*/
	
	return 0;
}

byte isAClockSyncMessage(Chunk *c)
{	
	//if ((*((MsgHandler*)c->handler) == RES_SYS_HANDLER) && (c->data[0] == CLOCK_SYNC_MSG))
	if ((c->handler == RES_SYS_HANDLER) && (c->data[0] == CLOCK_SYNC_MSG))
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
	
	//printf("ir: %lu\n", t);
	c->data[RECEIVE_TIME_INDEX+3] = (byte) (t & 0xFF);
	c->data[RECEIVE_TIME_INDEX+2] = (byte) ((t >>  8) & 0xFF);
	c->data[RECEIVE_TIME_INDEX+1] = (byte) ((t >> 16) & 0xFF);
	c->data[RECEIVE_TIME_INDEX] = (byte) ((t >> 24) & 0xFF);
}

void insertSendTime(Chunk *c)
{
	Time t = getClock();
	
	//printf("is: %lu\n", t);
	c->data[SEND_TIME_INDEX+3] = (byte) (t & 0xFF);
	c->data[SEND_TIME_INDEX+2] = (byte) ((t >>  8) & 0xFF);
	c->data[SEND_TIME_INDEX+1] = (byte) ((t >> 16) & 0xFF);
	c->data[SEND_TIME_INDEX] = (byte) ((t >> 24) & 0xFF);
}

byte synchronizeNeighbor(PRef p)
{
	byte data[3];
	
	if (!isSynchronized()) 
	{
		return 0;
	}
	
	data[0] = CLOCK_SYNC_MSG;
	data[1] = CLOCK_INFO;
	data[2] = lastWaveId;

	sendClockChunk(p, data, 3);
	
	return 1;
}

byte synchronizeNeighbors(void)
{
	byte p;
	
	for( p = 0; p < NUM_PORTS; p++)
	{
		if ((p == syncBy) || (thisNeighborhood.n[p] == VACANT))
		{
			continue;
		}
		synchronizeNeighbor(p);
	}
	
	return 1;
}

byte launchSynchronizationWave(void)
{
	lastWaveId++;
	return synchronizeNeighbors();
}


byte isSynchronized(void)
{
	return (isTimeLeader() || (lastWaveId > 0));
}

/******************************************************
 * Time Leader Election Functions
 *****************************************************/

void timeLeaderSubRoutine(void) {
	//printf("%u is Elected!\n", getGUID());
	// setColor(RED);
#ifdef MIN_ID_TIME_LEADER
	isLeader = 1;
#endif
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
	static int i = 0;
	i++;
	//printf("block %u: start election function at %u\n", getGUID(), getTime());
	//printf("%d election\n", i);
	if (!electing) {
	//	printf("block %u: start - election\n", getGUID()); 
		isLeader = 0;
		minId = getGUID();
		minIdSender = 255;
		electing = 1;
		nbNeededAnswers = broadcastGoMsg(255, getGUID());
		if (nbNeededAnswers == 0) 
		{
			electing = 0;
			if (minId == getGUID())
			{
				printf("block %u: direct win\n", getGUID());
				timeLeaderSubRoutine();
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
