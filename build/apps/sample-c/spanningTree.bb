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

void sendAddYourself(PRef p);
void sendACK(PRef p);
void sendNACK(PRef p);
void addYourselfHandler(void);
void addChildHandler(void);
void checkLeaves(void);

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

#define LEAF_CHECK_TIME	3000
threadvar Timeout leafCheck;

// COLOR NOTE: RED means not in spanning tree, BLUE in spanning tree. Leaves are GREEN.

void myMain(void)
{ 
 byte p;
 
 // init timeout
 leafCheck.callback = (GenericHandler)(&checkLeaves);
 leafCheck.calltime = getTime() + LEAF_CHECK_TIME; 
 registerTimeout(&leafCheck);

 // init children table
 for (p = 0 ; p < NUM_PORTS ; p++) children[p] = 0;
  
 // Leader is id 2, starts spanning tree setup
 if (getGUID() == 2) {
   isSTLeader = 1;
   isInTree = 1;
   setColor(BLUE);
   
   delayMS(1000);
   
   for (p = 0 ; p < NUM_PORTS ; p++) {
     if (thisNeighborhood.n[p] != VACANT) sendAddYourself(p);
   }
 }
 else 
 {
   isSTLeader = 0;
   setColor(RED);
 }
  
 while(1);
 
}

/*********************************************
 ********** SPANNING TREE FUNCTIONS **********
 ********************************************/

// Check if block is leaf, turn GREEN if true
void checkLeaves(void)
{
 if(numChildren == 0) {
   isLeaf = 1;
   setColor(GREEN);
 }
}

// Add yourself to the spanning tree, parent is message's sender, send it an ack
// Sending addYourself to all connected blocks and ignoring future add messages => NACK to sender
void addYourselfHandler(void)
{
  byte p;
  
  if (isInTree) sendNACK(faceNum(thisChunk));
  else {
    // NOW IN TREE
    setColor(BLUE);
    isInTree  = 1;
    
    // TELLS PARENT
    parent = faceNum(thisChunk);
    sendACK(parent);
   
    // GETS CHILDREN
    for (p = 0 ; p < NUM_PORTS ; p++) {
      if (p == parent || thisNeighborhood.n[p] == VACANT) continue;
      else sendAddYourself(p);
    }
  }
}

// Adds sender of the ack to the blocks child table
void 
addChildHandler(void)
{
  switch (thisChunk->data[0]) {
    case ST_ACK:
      children[faceNum(thisChunk)] = 1;
      numChildren++;
    break;
    case ST_NACK:
      // Just ignore this face.
    break;
  }
}

// 
void 
sendAddYourself(PRef p)
{ 
  byte data[1];
  data[0] = ADD_YOURSELF;
  
  sendMyChunk(p, data, 1, (MsgHandler)addYourselfHandler);
  
  #ifdef LOG_DEBUG
  char s[10];
  snprintf(s, 10*sizeof(char), "AY to %d", p);
  s[149] = '\0';
  printDebug(s);
  #endif
  
}

void 
sendACK(PRef p)
{ 
  byte data[1];
  data[0] = ST_ACK;
  
  sendMyChunk(p, data, 1, (MsgHandler)addChildHandler);
}


// 
void 
sendNACK(PRef p)
{ 
  byte data[1];
  data[0] = ST_NACK;
  
  sendMyChunk(p, data, 1, (MsgHandler)addChildHandler);
}

/************************************************************
***************CHUNCK MANAGEMENT FUNCTIONS*******************
************************************************************/

void 
freeMyChunk(void)
{
  free(thisChunk);
}

byte 
sendMyChunk(PRef port, byte *data, byte size, MsgHandler mh) 
{ 
  Chunk *c = getSystemTXChunk();
  if (c == NULL) return 0;
  if (sendMessageToPort(c, port, data, size, mh, (GenericHandler)&freeMyChunk) == 0)
  {
    free(c);
    return 0;
  }
  return 1;
}

void 
userRegistration(void)
{
  registerHandler(SYSTEM_MAIN, (GenericHandler)&myMain);	
}
