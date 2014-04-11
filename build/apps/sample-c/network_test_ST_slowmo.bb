#include "handler.bbh"
#include "data_link.bbh"
#include "led.bbh"
#include "block.bbh"
#include "ensemble.bbh"
#include "clock.bbh"
#include "block_config.bbh"

#ifdef LOG_DEBUG
#include "log.bbh"   
#endif

/********************************************** HOW TO READ BLOCK COLORS ******************************************************
 * 1 - DURING SPANNING TREE SETUP: 
 * 	Leader is blue
 * 	blocks not yet in spanning tree are red
 * 	blocks turn blue once they are added to the spanning tree
 * 2 - DURING NETWORK TEST: 
 * 	Blocks which receive a message from the leader to the leaves turn red
 * 	once the message is received by the leaves, the leaves send back a message to their parent and stay red
 * 	parents turn green when they receive a message from all of their children
 * 	once the leader has received a message from all of its children, the cycle is complete, it turns green and starts a new cycle by turning blue
 * 	if a message has been lost, the leader's lights turn off
 * 	if 100 cycle in a row are completed, the test has succeeded, all blocks turn green.
 *******************************************************************************************************************************/

void handleReceivedCommand(void);
byte resetReceived = 0;

/****************************************
 ************ SPANNING TREE *************
 ***************************************/

#define ST_NACK		0
#define ST_ACK		1
#define ADD_YOURSELF	2

void sendAddYourself(PRef p);
void sendACK(PRef p);
void sendNACK(PRef p);
void sendSuccessMsg(PRef p);
void addYourselfHandler(void);
void addChildHandler(void);
void successMsgHandler(void);

threadvar PRef children[NUM_PORTS];
threadvar PRef parent;
threadvar byte numChildren = 0;

threadvar byte isSTLeader = 0;
threadvar byte isInTree = 0;
threadvar byte isLeaf = 0;

/****************************************
 ************* NETWORK TEST *************
 ***************************************/

#define COM			3
#define COMBACK			4
#define SUCCESS			5

void goMsgHandler(void);
void backMsgHandler(void);
void sendCycle(PRef p);
void sendBackCycle(PRef p);

byte childResponseCount = 0;
byte cycleNum = 1;

// CHUNCK MANAGEMENT
void freeMyChunk(void);
byte sendMyChunk(PRef port, byte *data, byte size, MsgHandler mh); 


// TIME MANAGEMENT
#define LEAF_CHECK_TIME		2000
#define CYCLE_TIME_LIMIT	7000
#define LAST_CYCLE_WAIT		100
Time zeroTime;
Time cycleStartTime;

Timeout tout;

void
start(void)
{ 
 byte p;

  setColor(AQUA);

  // build spanning tree

  // INIT CHILDREN TABLE
  for (p = 0 ; p < NUM_PORTS ; p++) {
      children[p] = 0;
  }
  
  if (getGUID() == 2) {
    isSTLeader = 1;
    isInTree = 1;
    setColor(BLUE);
   
    delayMS(500);
   
    for (p = 0 ; p < NUM_PORTS ; p++) {
      if (thisNeighborhood.n[p] != VACANT) {
	sendAddYourself(p);
      }
    }
  }
  else {
    isSTLeader = 0;
    setColor(RED);
  }
  
  while(getClock() < zeroTime + LEAF_CHECK_TIME);
 
  if(numChildren == 0) {
      isLeaf = 1;
  }
 
  if (isSTLeader) {
      delayMS(1000); // TIME TO VISUALLY CHECK THE LEAVES
      setColor(RED);
   
#ifdef LOG_DEBUG
      char s[150];
      snprintf(s, 150*sizeof(char), "C%d STARTS", cycleNum);
      s[149] = '\0';
      printDebug(s);
#endif
       
      for( p = 0; p < NUM_PORTS; p++) {
	if (children[p] == 1)
	  {
	    cycleStartTime = getClock();
	    setColor(BLUE);
	    sendCycle(p);
	  }
      }    
      while ( (getClock() < cycleStartTime + CYCLE_TIME_LIMIT) && (cycleNum < 20) );
   
      while (getClock() < cycleStartTime + CYCLE_TIME_LIMIT + LAST_CYCLE_WAIT); // USED TO MAKE SURE THAT LEADER WILL SPREAD SUCCESS MESSAGE TO SET EVERYONE GREEN
   
      if (cycleNum == 20)
	{
#ifdef LOG_DEBUG
	  snprintf(s, 150*sizeof(char), "NETWORK TEST PASSED");
	  s[149] = '\0';
	  printDebug(s);
#endif
     
	  for (p = 0; p < NUM_PORTS; p++) 
	    {
	      if (children[p] == 1)
		{
		  sendSuccessMsg(p);
		}
	    }    
	}
      else
	{
#ifdef LOG_DEBUG
	  snprintf(s, 150*sizeof(char), "C%d TIMEOUT\n", cycleNum);
	  s[149] = '\0';
	  printDebug(s);
#endif
      
	  setIntensity(0);
	}
      setColor(GREEN);
    }
  
  while(1);
 
}

void myMain(void)
{ 
  
#if 0
 initClock();
 zeroTime = getClock();
#endif 
 
 setColor(GREEN);
 tout.callback = (GenericHandler)(&start);
 tout.calltime = getTime() + 3000;
 registerTimeout(&tout);
}


/**************************************************
 ************* NETWORK TEST FUNCTIONS *************
 **************************************************/

void backMsgHandler(void)
{ 
  delayMS(200);
  
  byte p;
  
  childResponseCount++;
  
  if(!isSTLeader)
  {
    if (childResponseCount == numChildren)
    {
      childResponseCount = 0;
      setColor(GREEN);
      sendBackCycle(parent);
    }
  }
  else
  {
    if (childResponseCount == numChildren)
    {
      childResponseCount = 0;
      setColor(GREEN);    
      
#ifdef LOG_DEBUG
      char s[150];
      snprintf(s, 150*sizeof(char), "C%d PASSED\n", cycleNum);
      s[149] = '\0';
      printDebug(s);
#endif
      
      if (cycleNum < 20)
      {
	cycleNum++; // STARTING A NEW CYCLE
      
#ifdef LOG_DEBUG
	snprintf(s, 150*sizeof(char), "C%d STARTS", cycleNum);
	s[149] = '\0';
	printDebug(s);
#endif
	
	for( p = 0; p < NUM_PORTS; p++) 
	{
	  if (children[p] == 1)
	  {
	    setColor(BLUE);
	    cycleStartTime = getClock();
	    sendCycle(p);
	  }
	}
      }
    }
  }
}

void goMsgHandler(void)
{ 
  delayMS(200);
  byte p;
  setColor(RED);
  if (!isLeaf)
  {
    for (p = 0; p < NUM_PORTS; p++) 
    {
      if (children[p] == 1)
      {
	sendCycle(p);
      }
    }    
  }
  else
  {
    sendBackCycle(parent);
  }
}

void successMsgHandler(void)
{ 
  byte p;
  setColor(GREEN);
  if (!isLeaf)
  {
    for (p = 0; p < NUM_PORTS; p++) 
    {
      if (children[p] == 1)
      {
	sendSuccessMsg(p);
      }
    }    
  }
}

void sendCycle(byte child)
{
  byte data[1];
  data[0] = COM;
  sendMyChunk(child, data, 1, (MsgHandler)goMsgHandler);
}

void sendBackCycle(byte parent)
{
  byte data[1];
  data[0] = COMBACK;  
  sendMyChunk(parent, data, 1, (MsgHandler)backMsgHandler);
}

void sendSuccessMsg(byte child)
{
  byte data[1];
  data[0] = SUCCESS;  
  sendMyChunk(child, data, 1, (MsgHandler)successMsgHandler);
}

/*********************************************
 ********** SPANNING TREE FUNCTIONS **********
 ********************************************/

void addYourselfHandler(void)
{
  byte p;
  
  if (isInTree)
  {
    sendNACK(faceNum(thisChunk));
  }
  else
  {
    // NOW IN TREE
    setColor(BLUE);
    isInTree  = 1;
    
    // TELLS PARENT
    parent = faceNum(thisChunk);
    sendACK(parent);
   
    // GETS CHILDREN
    for (p = 0 ; p < NUM_PORTS ; p++)
    {
      if (p == parent || thisNeighborhood.n[p] == VACANT)
      {
	continue;
      }
      else
      {
	sendAddYourself(p);
      }
    }
  }
}

void addChildHandler(void)
{
  switch (thisChunk->data[0])
  {
    case ST_ACK:
      children[faceNum(thisChunk)] = 1;
      numChildren++;
    break;
    case ST_NACK:
      // Just ignore this face.
    break;
  }
}

void sendAddYourself(PRef p)
{ 
  byte data[1];
  data[0] = ADD_YOURSELF;
  
  sendMyChunk(p, data, 1, (MsgHandler)addYourselfHandler);
}

void sendACK(PRef p)
{ 
  byte data[1];
  data[0] = ST_ACK;
  
  sendMyChunk(p, data, 1, (MsgHandler)addChildHandler);
}



void sendNACK(PRef p)
{ 
  byte data[1];
  data[0] = ST_NACK;
  
  sendMyChunk(p, data, 1, (MsgHandler)addChildHandler);
}

/************************************************************
***************CHUNCK MANAGEMENT FUNCTIONS*******************
************************************************************/

void freeMyChunk(void)
{
  freeChunk(thisChunk);
}

byte sendMyChunk(PRef port, byte *data, byte size, MsgHandler mh) 
{ 
  Chunk *c=getSystemTXChunk();
  if (c == NULL)
  {  
    return 0;
  }
  if (sendMessageToPort(c, port, data, size, mh, (GenericHandler)&freeMyChunk) == 0)
  {
    freeChunk(c);
    return 0;
  }
  return 1;
}

void handleReceivedCommand(void)
{
  initializeMemory();
  callHandler(SYSTEM_MAIN);
}

/************************************************************
************************COMMAND HANDLING*********************
************************************************************/

void userRegistration(void)
{
  registerHandler(SYSTEM_MAIN, (GenericHandler)&myMain);	
  registerHandler(EVENT_COMMAND_RECEIVED, (GenericHandler)&handleReceivedCommand);
}
