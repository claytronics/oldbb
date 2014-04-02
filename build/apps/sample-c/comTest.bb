#include "handler.bbh"
#include "data_link.bbh"
#include "led.bbh"
#include "log.bbh"
#include "accelerometer.bbh"
#include "handler.bbh"
#include "block.bbh"
#include "ensemble.bbh"
#include "clock.bbh"
#include "block_config.bbh"

byte pong(void);
void myMsgHandler(void);
void freeMyChunk(void);
byte sendMyChunk(PRef port, byte *data, byte size, MsgHandler mh); 

#ifdef LOG_DEBUG
byte from;
byte comCount = 0;
    
void myMain(void)
{ 
  byte p;
  char s[150];
  byte activePort;
  byte msg[17];
 
  //while(1) {
    snprintf(s, 150*sizeof(char), "INIT");
    s[149] = '\0';
    printDebug(s);
    setIntensity(0); 
    delayMS(1000);
    setIntensity(255);
        
    if (getGUID() == 21593) {
      setColor(ORANGE);
      delayMS(2000);
    for( p = 0; p < NUM_PORTS; p++) {
      if (thisNeighborhood.n[p] == VACANT) {
	continue;
      }
      else {
	activePort = p;
      }
    }
    msg[0] = ++comCount;
    sendMyChunk(activePort, msg, 1, (MsgHandler)myMsgHandler);
    
    snprintf(s, 150*sizeof(char), "START");
    s[149] = '\0';
    printDebug(s);
    
    } else {
      setColor(RED);
    }
 //}
 #endif
 
 while(1);
 
}

void myMsgHandler(void)
{
  char s[150];
  comCount = thisChunk->data[0];
  switch (comCount) {
    case 50: setIntensity(50); break;
    case 100: setIntensity(100); break;
  }
  from = faceNum(thisChunk);
  delayMS(500);
  setNextColor();
  
  pong();
}

byte pong(void)
{ 
  char s[150];
  byte msg[17];
  msg[0] = ++comCount;
  
  /*snprintf(s, 150*sizeof(char), "%d", comCount);
  s[149] = '\0';
  printDebug(s);*/
  
  sendMyChunk(from, msg, 1, (MsgHandler)myMsgHandler);
  
  return 1;
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

void userRegistration(void)
{
  registerHandler(SYSTEM_MAIN, (GenericHandler)&myMain);	
}
