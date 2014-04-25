#include "block.bbh"

threadvar byte hasBottomNeighbor;
threadvar byte hasTopNeighbor;
threadvar PRef sideNeighbors[4];
threadvar byte numSideNeighbors;
threadvar byte currentLayer;  
threadvar byte iAmOnLowestLayer;

byte sendCustomChunk(byte messageType, PRef p);
byte sendLayerUpdate(byte newLayer, PRef p);
byte customMessageHandler(void);
void spreadLayerInfo(void);
Chunk* getFreeUserChunk(void);

// Message types
#define LAYER_UPDATE 0x01
#define I_HAVE_BOTTOM_NEIGHBOR 0x02
  
// Time management
#define LOWEST_LAYER_CHECK_TIME 200
Timeout lowestLayerCheck;
#define SET_COLOR_TIME 500
Timeout waitForLayerSetup;

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

// Send a layer update chunk
byte 
sendLayerUpdate(byte newLayer, PRef p)
{
  Chunk *c = getFreeUserChunk();
 
  c->data[0] = LAYER_UPDATE;
  c->data[1] = newLayer;

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
  byte newLayer;
  if (thisChunk == NULL) return 0;
  byte messageType = thisChunk->data[0];
  byte chunkSource = faceNum(thisChunk);
  switch (messageType) {
  case LAYER_UPDATE: 
    newLayer = thisChunk->data[1];
    if (newLayer > currentLayer) {
      currentLayer = newLayer;
      for (byte i=0; i < numSideNeighbors; i++) {
	sendLayerUpdate(newLayer, sideNeighbors[i]);
      }
      if (hasTopNeighbor) sendLayerUpdate((currentLayer+1), UP);
      if (hasBottomNeighbor && (chunkSource != DOWN) ) sendLayerUpdate((newLayer-1), DOWN);
    }
    // else ignore message.
    break;
  case I_HAVE_BOTTOM_NEIGHBOR:
    if (iAmOnLowestLayer) {
      iAmOnLowestLayer = 0;
      for (byte i=0; i < numSideNeighbors; i++) {
	sendCustomChunk(I_HAVE_BOTTOM_NEIGHBOR, sideNeighbors[i]);
      }
    }
    // else ignore message. 
    break; 
  }
  return 1;
}

// Spread layer info from the base to the top, incrementing sent number on each layer.
void
spreadLayerInfo(void)
{
  if (iAmOnLowestLayer) {
    // Send new layer to next layer
    if (hasTopNeighbor) sendLayerUpdate((currentLayer+1), 5);
  }
  // else do nothing, keep on waiting.
}

void
setupRainbow(void)
{
  setColor(currentLayer % NUM_COLORS);
}

void 
myMain(void)
{
  setColor(WHITE);
  // We are forced to use a small delay before program execution, otherwise neighborhood may not be initialized yet
  delayMS(200);

  // Initialize Timeouts
  lowestLayerCheck.callback = (GenericHandler)(&spreadLayerInfo);
  lowestLayerCheck.calltime = getTime() + LOWEST_LAYER_CHECK_TIME;
  registerTimeout(&lowestLayerCheck);

  waitForLayerSetup.callback = (GenericHandler)(&setupRainbow);
  waitForLayerSetup.calltime = getTime() + SET_COLOR_TIME;
  registerTimeout(&waitForLayerSetup);

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

  //------- Determine layer of each block
  // A block will assume it is on the ensemble's lowest layer until it receives a I_HAVE_BOTTOM_NEIGHBOR message from its side neighbors. 
  currentLayer = 0;
  iAmOnLowestLayer = 1;
  if (hasBottomNeighbor) {
    for (byte i=0; i < numSideNeighbors; i++) {
      sendCustomChunk(I_HAVE_BOTTOM_NEIGHBOR, sideNeighbors[i]); 
    }
  }
  // Then, they all wait for the layer below them to provide them with their layer.
  while(1);
}

void 
userRegistration(void)
{
  registerHandler(SYSTEM_MAIN, (GenericHandler)&myMain);
  // NOT YET IMPLEMENTED
  // registerHandler(EVENT_NEIGHBOR_CHANGE, (GenericHandler)&processNeighborChange);
}
