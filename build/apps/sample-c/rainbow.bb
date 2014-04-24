#include "block.bbh"

threadvar byte hasBottomNeighbor;
threadvar byte hasTopNeighbor;
threadvar PRef sideNeighbors[4];
threadvar byte numSideNeighbors;
threadvar byte currentLevel;  
threadvar byte iAmOnLowestLevel;

byte sendCustomChunk(byte messageType, PRef p);
byte sendLevelUpdate(byte newLevel, PRef p);
byte customMessageHandler(void);
void spreadLevelInfo(void);
Chunk* getFreeUserChunk(void);

// Message types
#define LEVEL_UPDATE 0x01
#define I_HAVE_BOTTOM_NEIGHBOR 0x02
  
// Time management
#define LOWEST_LEVEL_CHECK_TIME 200
Timeout lowestLevelCheck;
#define SET_COLOR_TIME 500
Timeout waitForLevelSetup;

#define MYCHUNKS 12
extern Chunk* thisChunk;
Chunk myChunks[MYCHUNKS];

// find a useable chunk
Chunk* 
getFreeUserChunk(void)
{
    Chunk* c;
    int i;

    for(i=0; i<MYCHUNKS; i++) {
        c = &(myChunks[i]);

        if( !chunkInUse(c) ) {
            return c;
        }
    }
    return NULL;
}

// Send a chunk with a specific message type
byte
sendCustomChunk(byte messageType, PRef p)
{
  Chunk *c = getFreeUserChunk();
 
  c->data[0] = messageType;

  if (c != NULL) {      
    if ( sendMessageToPort(c, p, c->data, 1, customMessageHandler, NULL) == 0 ) {
      freeChunk(c);
      return 0;
    }
  }
  return 1;
}

// Send a level update chunk
byte 
sendLevelUpdate(byte newLevel, PRef p)
{
  Chunk *c = getFreeUserChunk();
 
  c->data[0] = LEVEL_UPDATE;
  c->data[1] = newLevel;

  if (c != NULL) {      
    if ( sendMessageToPort(c, p, c->data, 2, customMessageHandler, NULL) == 0 ) {
      freeChunk(c);
      return 0;
    }
  }
  return 1;
}

// Process received chunk depending on its type on the block's situation
byte
customMessageHandler(void)
{
  byte newLevel;
  if (thisChunk == NULL) return 0;
  byte messageType = thisChunk->data[0];
  byte chunkSource = faceNum(thisChunk);
  switch (messageType) {
  case LEVEL_UPDATE: 
    newLevel = thisChunk->data[1];
    if (newLevel > currentLevel) {
      currentLevel = newLevel;
      for (byte i=0; i < numSideNeighbors; i++) {
	sendLevelUpdate(newLevel, sideNeighbors[i]);
      }
      if (hasTopNeighbor) sendLevelUpdate((currentLevel+1), UP);
      if (hasBottomNeighbor && (chunkSource != DOWN) ) sendLevelUpdate((newLevel-1), DOWN);
    }
    // else ignore message.
    break;
  case I_HAVE_BOTTOM_NEIGHBOR:
    if (iAmOnLowestLevel) {
      iAmOnLowestLevel = 0;
      for (byte i=0; i < numSideNeighbors; i++) {
	sendCustomChunk(I_HAVE_BOTTOM_NEIGHBOR, sideNeighbors[i]);
      }
    }
    // else ignore message. 
    break; 
  }
  return 1;
}

// Spread level info from the base to the top, incrementing sent number on each level.
void
spreadLevelInfo(void)
{
  if (iAmOnLowestLevel) {
    // Send new level to next level
    if (hasTopNeighbor) sendLevelUpdate((currentLevel+1), 5);
  }
  // else do nothing, keep on waiting.
}

void
setupRainbow(void)
{
  setColor(currentLevel % NUM_COLORS);
}

void 
myMain(void)
{
  setColor(WHITE);
  // We are forced to use a small delay before program execution, otherwise neighborhood may not be initialized yet
  delayMS(200);

  // Initialize Timeouts
  lowestLevelCheck.callback = (GenericHandler)(&spreadLevelInfo);
  lowestLevelCheck.calltime = getTime() + LOWEST_LEVEL_CHECK_TIME;
  registerTimeout(&lowestLevelCheck);

  waitForLevelSetup.callback = (GenericHandler)(&setupRainbow);
  waitForLevelSetup.calltime = getTime() + SET_COLOR_TIME;
  registerTimeout(&waitForLevelSetup);

  // Initialize chunks
  for(byte x=0; x < MYCHUNKS; x++) {
    myChunks[x].status = CHUNK_FREE;
  }

  // Initialize neighbor variables
  numSideNeighbors = 0;
  if (thisNeighborhood.n[UP] != VACANT) hasTopNeighbor = 1;
  else hasTopNeighbor = 0;
  if (thisNeighborhood.n[DOWN] != VACANT) hasBottomNeighbor = 1;
  else hasBottomNeighbor = 0;
  for (byte p = 1; p < 5; p++) {
    if (thisNeighborhood.n[p] != VACANT) sideNeighbors[numSideNeighbors++] = p;
  }

  //------- Determine level of each block
  // A block will assume it is on the ensemble's lowest level until it receives a I_HAVE_BOTTOM_NEIGHBOR message from its side neighbors. 
  currentLevel = 0;
  iAmOnLowestLevel = 1;
  if (hasBottomNeighbor) {
    for (byte i=0; i < numSideNeighbors; i++) {
      sendCustomChunk(I_HAVE_BOTTOM_NEIGHBOR, sideNeighbors[i]); 
    }
  }
  // Then, they all wait for the level below them to provide them with their level.
  while(1);
}

void 
userRegistration(void)
{
  registerHandler(SYSTEM_MAIN, (GenericHandler)&myMain);
  // NOT YET IMPLEMENTED
  // registerHandler(EVENT_NEIGHBOR_CHANGE, (GenericHandler)&processNeighborChange);
}
