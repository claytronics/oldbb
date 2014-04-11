#include "handler.bbh"
#include "data_link.bbh"
#include "led.bbh"
#include "log.bbh"
#include "block.bbh"
#include "ensemble.bbh"
#include "clock.bbh"
#include "block_config.bbh"

byte sendCmd(void);
byte sendACK(void);
void myMsgHandler(void);
void freeMyChunk(void);

#define TEST_ACK	0
#define CMD		1
#define ACK_TIMEOUT	500

//#ifdef LOG_DEBUG
threadvar byte activePort;
threadvar byte seqNum = 1;
threadvar byte recSeq;	
threadvar byte ackReceived = 0;
threadvar char s[150];

Time sendTime;

void myMain(void)
{ 
    byte p;
    
    snprintf(s, 150*sizeof(char), "INIT");
    s[149] = '\0';
    printDebug(s);
    
    initClock();
    setIntensity(0); 
    delayMS(500);
    setIntensity(255);
        
    if (getGUID() == 2) {				// Master initializes communication
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
      while (seqNum < 50) {
	while (!ackReceived){
	  if (getClock() > (sendTime + ACK_TIMEOUT)) {	// While an ack has not been received, resend command every ACK_TIMEOUT milliseconds
	    /*snprintf(s, 150*sizeof(char), "CMD %d LOST", seqNum);
	    s[149] = '\0';
	    printDebug(s);*/
	    sendCmd();
	    sendTime = getClock();
	  }
	}
	ackReceived = 0;
	sendTime = getClock();
	sendCmd();
      }
      snprintf(s, 150*sizeof(char), "PASSED!");
      s[149] = '\0';
      printDebug(s);
 
      setColor(GREEN);
    } 
    else {
      seqNum = 0;
      setColor(WHITE);					// Slave only get into a loop and process received messages with myMsgHandler
    }
 
 while(1)
 {
   if(seqNum == 50) {
       setColor(GREEN);
   }
 }
 
}

void myMsgHandler(void)
{
  activePort = faceNum(thisChunk);
  recSeq = thisChunk->data[1];
  switch (thisChunk->data[0]) {
    case CMD:
      if (recSeq > seqNum)
      { 						// Check if ACK has been lost, if true: resend ack and ignore command
	setNextColor();
	seqNum = recSeq;
	sendACK();	
      }
      else { 						// Default case, execute command, send ACK
	sendACK();
	/*snprintf(s, 150*sizeof(char), "ACK %d LOST", lastSeq);
	s[149] = '\0';
	printDebug(s);*/
      }
    break;
    case TEST_ACK: 					// ACK received, set to 1 so myMain can trigger next command
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
  snprintf(s, 150*sizeof(char), "ACK %d", seqNum);
  s[149] = '\0';
  printDebug(s);
  
  Chunk *c = calloc(sizeof(Chunk), 1);
  
  c->data[0] = TEST_ACK;
  c->data[1] = seqNum;
  
  if (c == NULL)
  {  
    return 0;
  }
  if (sendMessageToPort(c, activePort, c->data, 17, (MsgHandler)myMsgHandler, (GenericHandler)&freeMyChunk) == 0) {
    free(c);
    return 0;
  }
  return 1;
}

byte 
sendCmd(void)
{
  snprintf(s, 150*sizeof(char), "CMD %d", seqNum);
  s[149] = '\0';
  printDebug(s);
  
  Chunk *c = calloc(sizeof(Chunk), 1);
  
  c->data[0] = CMD;
  c->data[1] = seqNum;
  
  if (c == NULL)
  {  
    return 0;
  }
  if (sendMessageToPort(c, activePort, c->data, 17, (MsgHandler)myMsgHandler, (GenericHandler)&freeMyChunk) == 0)
  {
    free(c);
    return 0;
  }
  return 1;
}

void 
freeMyChunk(void)
{
  free(thisChunk);
}

void 
userRegistration(void)
{
  registerHandler(SYSTEM_MAIN, (GenericHandler)&myMain);	
}