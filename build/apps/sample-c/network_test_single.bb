#include "block.bbh"

byte sendCmd(void);
byte sendACK(void);
void myMsgHandler(void);
void freeMyChunk(void);

#define TEST_ACK	0
#define CMD		1
#define ACK_TIMEOUT	500

threadvar byte activePort;
threadvar byte seqNum = 1;
threadvar byte recSeq;	
threadvar byte ackReceived = 0;
threadvar char ss[10];

Time sendTime;

void myMain(void)
{ 
    byte p;
    uint16_t masterID;
    
    #ifdef LOG_DEBUG
    snprintf(ss, 150*sizeof(char), "INIT");
    printDebug(ss);
    #endif
    
    initClock();
    setIntensity(0); 
    delayMS(500);
    setIntensity(255);
        
    // Determines ID of master block
    for(p = 0; p < NUM_PORTS ; p++) {
	if(isHostPort(p)) {
	    masterID = getGUID();
	  }
      }

    // Master initializes communication
    if (getGUID() == masterID) {
      setColor(RED);
      
      for (p = 0; p < NUM_PORTS; p++) {
	if (thisNeighborhood.n[p] == VACANT) {
	  continue;
	}
	else {
	  activePort = p;
	}
      }
      sendTime = getClock();
      while (1) {
	while (!ackReceived){
	  if (getClock() > (sendTime + ACK_TIMEOUT)) {	
	    // While an ack has not been received, resend command every ACK_TIMEOUT milliseconds
#ifdef LOG_DEBUG
	    snprintf(ss, 15*sizeof(char), "CMD %d LOST", seqNum);
	    printDebug(ss);
#endif
	    sendCmd();
	    sendTime = getClock();
	  }
	}
	ackReceived = 0;
	sendTime = getClock();
	sendCmd();
      }
      #ifdef LOG_DEBUG
      snprintf(ss, 15*sizeof(char), "PASSED!");
      printDebug(ss);
      #endif
 
      setColor(GREEN);
    } 
    else {
      // Slave gets into a loop and processes received messages with myMsgHandler
      seqNum = 0;
      setColor(WHITE);					
    }
 
 while(1);
}

void myMsgHandler(void)
{
  activePort = faceNum(thisChunk);
  recSeq = thisChunk->data[1];
  switch (thisChunk->data[0]) {
    case CMD:
      // Check if ACK has been lost, if true: resend ack and ignore command
      if (recSeq > seqNum) { 						
	setNextColor();
	seqNum = recSeq;
	sendACK();	
      }
      // Default case, execute command, send ACK
      else {
	sendACK();
#ifdef LOG_DEBUG
	snprintf(ss, 15*sizeof(char), "ACK %d LOST", recSeq);
	printDebug(ss);
#endif
      }
      break;
      // ACK received, set to 1 so myMain can trigger next command
    case TEST_ACK:
      if (recSeq = seqNum)
      {
      ackReceived = 1;
      seqNum++;
      }
      delayMS(200);
    break;
  }
}

byte sendACK(void)
{ 
#ifdef LOG_DEBUG
  snprintf(ss, 15*sizeof(char), "ACK %d", seqNum);
  printDebug(ss);
#endif
  
  Chunk *c = getSystemTXChunk();
  
  c->data[0] = TEST_ACK;
  c->data[1] = seqNum;
  
  if (c == NULL)
  {  
    return 0;
  }
  if (sendMessageToPort(c, activePort, c->data, 2, (MsgHandler)myMsgHandler, (GenericHandler)&freeMyChunk) == 0) {
    freeChunk(c);
    return 0;
  }
  return 1;
}

byte 
sendCmd(void)
{
#ifdef LOG_DEBUG
  snprintf(ss, 15*sizeof(char), "CMD %d", seqNum);
  printDebug(ss);
#endif
  
  Chunk *c = getSystemTXChunk();
  
  c->data[0] = CMD;
  c->data[1] = seqNum;
  
  if (c == NULL)
  {  
    return 0;
  }
  if (sendMessageToPort(c, activePort, c->data, 2, (MsgHandler)myMsgHandler, (GenericHandler)&freeMyChunk) == 0)
  {
    freeChunk(c);
    return 0;
  }
  return 1;
}

void 
freeMyChunk(void)
{
  freeChunk(thisChunk);
}

void 
userRegistration(void)
{
  registerHandler(SYSTEM_MAIN, (GenericHandler)&myMain);	
}
