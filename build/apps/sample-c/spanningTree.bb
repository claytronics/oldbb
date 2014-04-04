#include "handler.bbh"
#include "data_link.bbh"
#include "led.bbh"
#include "log.bbh"
#include "block.bbh"
#include "ensemble.bbh"
#include "clock.bbh"
#include "block_config.bbh"

void sendAddYourself(PRef p);
void sendACK(PRef p);
void sendNACK(PRef p);
void addYourselfHandler(void);
void addChildHandler(void);

void freeMyChunk(void);
byte sendMyChunk(PRef port, byte *data, byte size, MsgHandler mh); 

#define ST_NACK		0
#define ST_ACK		1
#define ADD_YOURSELF	2

threadvar PRef children[NUM_PORTS];
threadvar PRef parent;
threadvar byte numChildren = 0;

threadvar byte isSTLeader = 0;
threadvar byte isInTree = 0;
threadvar byte isLeaf = 0;

#define LEAF_CHECK_TIME	5000
Time zeroTime;

void myMain(void)
{ 
 byte p;
  
 initClock();
 zeroTime = getClock();
 
 
 // INIT CHILDREN TABLE
 for (p = 0 ; p < NUM_PORTS ; p++)
 {
   children[p] = 0;
 }
  
 if (getGUID() == 2) 
 {
   isSTLeader = 1;
   isInTree = 1;
   setColor(BLUE);
   
   delayMS(1000);
   
   for (p = 0 ; p < NUM_PORTS ; p++)
   {
     if (thisNeighborhood.n[p] != VACANT) 
     {
       sendAddYourself(p);
     }
   }
 }
 else 
 {
   isSTLeader = 0;
   setColor(RED);
 }
  
 while(getClock() < zeroTime + LEAF_CHECK_TIME);
 
 if(numChildren == 0)
 {
   isLeaf = 1;
   setColor(GREEN);
 }
  
 while(1);
 
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
  #ifdef LOG_DEBUG
  #include "log.bbh"   
  #endif
  
  #ifdef LOG_DEBUG
  char s[150];
  snprintf(s, 150*sizeof(char), "ST sent to %d", p);
  s[149] = '\0';
  printDebug(s);
  #endif
  
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

/**/

void userRegistration(void)
{
  registerHandler(SYSTEM_MAIN, (GenericHandler)&myMain);	
}