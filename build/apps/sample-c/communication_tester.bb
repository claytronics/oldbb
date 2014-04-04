#include "handler.bbh"
#include "data_link.bbh"
#include "led.bbh"
#include "log.bbh"
#include "block.bbh"
#include "ensemble.bbh"
#include "clock.bbh"
#include "block_config.bbh"

void sendCmd(void);
void sendACK(void);
void myMsgHandler(void);
void freeMyChunk(void);
byte sendMyChunk(PRef port, byte *data, byte size, MsgHandler mh); 

#define ACK		0
#define CMD		1
#define ACK_TIMEOUT	(1000)

//#ifdef LOG_DEBUG
byte activePort;
byte seqNum = 1;
byte lastSeq = 0;
byte ackReceived = 0;
byte msg[2];
char s[150];
    
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
        
    if (getGUID() == 2) {						// Master initializes communication
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
	  if (getClock() > (sendTime + ACK_TIMEOUT)) {			// While an ack has not been received, resend command every ACK_TIMEOUT milliseconds
	    /*snprintf(s, 150*sizeof(char), "CMD %d LOST", seqNum);
	    s[149] = '\0';
	    printDebug(s);*/
	    sendCmd();
	    sendTime = getClock();
	  }
	}
	seqNum++;
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
      setColor(WHITE);							// Slave only get into a loop and process received messages with myMsgHandler
    }
 
 while(1);
 
}

void myMsgHandler(void)
{
  activePort = faceNum(thisChunk);
  
  switch (thisChunk->data[0]) {
    case CMD:
      seqNum = thisChunk->data[1];
      if (seqNum == lastSeq) { 						// Check if ACK has been lost, if true: resend ack and ignore command
	sendACK();
	
	/*snprintf(s, 150*sizeof(char), "ACK %d LOST", lastSeq);
	s[149] = '\0';
	printDebug(s);*/
      }
      else { 								// Default case, execute command, send ACK
	setNextColor();
	sendACK();
      }
    break;
    case ACK: 								// ACK received, set to 1 so myMain can trigger next command
      ackReceived = 1;
    break;
  }
}

void sendACK(void)
{ 
  msg[0] = ACK;
  msg[1] = seqNum;
  
  sendMyChunk(activePort, msg, 2, (MsgHandler)myMsgHandler);
  
  snprintf(s, 150*sizeof(char), "ACK %d", seqNum);
  s[149] = '\0';
  printDebug(s);
  
  lastSeq = seqNum;
  
  if(seqNum == 50) {
    setColor(GREEN);
  }
  
}

void sendCmd(void)
{
  msg[0] = CMD;
  msg[1] = seqNum;
  
  snprintf(s, 150*sizeof(char), "CMD %d", seqNum);
  s[149] = '\0';
  printDebug(s);
  
  sendMyChunk(activePort, msg, 2, (MsgHandler)myMsgHandler);
}

void freeMyChunk(void)
{
  free(thisChunk);
}

byte sendMyChunk(PRef port, byte *data, byte size, MsgHandler mh) 
{ 
  Chunk *c=calloc(sizeof(Chunk), 1);
  if (c == NULL)
  {  
    return 0;
  }
  if (sendMessageToPort(c, port, data, size, mh, (GenericHandler)&freeMyChunk) == 0)
  {
    free(c);
    return 0;
  }
  return 1;
}
//#endif

void userRegistration(void)
{
  registerHandler(SYSTEM_MAIN, (GenericHandler)&myMain);	
}
