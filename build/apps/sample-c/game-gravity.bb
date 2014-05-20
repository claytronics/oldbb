#include "block.bbh"


// Neighbor management variables
// enum portReferences { DOWN, NORTH, EAST, WEST, SOUTH, UP, NUM_PORTS };
threadvar byte hasFaceNeighbor[NUM_PORTS];
threadvar PRef sideNeighbors[4];
threadvar byte numSideNeighbors;

// Layer management variables and functions
threadvar byte topLayer;
threadvar byte currentLayer;  
threadvar byte isOnBottomLayer;
threadvar byte isOnTopLayer;

byte sendCustomLayerChunk(byte messageType, PRef p);
byte sendLayerUpdate(byte newLayer, PRef p);
byte layerMessageHandler(void);
void spreadLayerInfo(void);

// Spanning Tree management
threadvar PRef parent;
threadvar PRef children[NUM_PORTS];
threadvar byte numChildren;
threadvar uint16_t isInATree;
threadvar byte numExpectedChildrenAnswers;
threadvar byte numExpectedBwdMessages;
threadvar uint16_t leaderID;
int randNum;
byte countChildren;

void setUpSpanningTree(void);
byte sendSpanningTreeMessage(PRef p, byte messageType, uint16_t id, byte layer);
void buildChildren(void);
void checkLeaf(void);

#define ADD_YOURSELF    0x10
#define IA_TREE          0x11
#define A_TREE         0x12
#define ST_OK           0x13

// Game related variables and functions
void startGame(void);

// Message types
#define LAYER_UPDATE 0x01
#define I_HAVE_BOTTOM_NEIGHBOR 0x02
#define I_HAVE_TOP_NEIGHBOR 0x03
#define NUMBER_OF_BLOCKS_ON_LAYER 0x04  

// Time management
#define LOWEST_LAYER_CHECK_TIME 500
Timeout lowestLayerCheck;
/*#define POLLING_DURATION 200
  Timeout waitForLeaderElection;*/
#define SPECIAL_BLOCKS_ELECTION_TIME 2000
Timeout leaderElectionTimeout;
#define GAMEPLAY_SPEED_RATE 2000 // Time in second between each turn.
Timeout tout;

// Chunk management
#define MYCHUNKS 12
extern Chunk* thisChunk;
Chunk myChunks[MYCHUNKS];
Chunk* getFreeUserChunk(void);

void 
myMain(void)
{
  setColor(WHITE);
  // We are forced to use a small delay before program execution, otherwise neighborhood may not be initialized yet
  delayMS(200);
 
  // Initialize layer variables
  currentLayer = 0;
  topLayer = 0;
  isOnBottomLayer = 1;
  isOnTopLayer = 1;

  // Initialize Timeouts
  lowestLayerCheck.callback = (GenericHandler)(&spreadLayerInfo);
  lowestLayerCheck.calltime = getTime() + LOWEST_LAYER_CHECK_TIME;
  registerTimeout(&lowestLayerCheck);

  leaderElectionTimeout.callback = (GenericHandler)(&setUpSpanningTree);
  leaderElectionTimeout.calltime = getTime() + SPECIAL_BLOCKS_ELECTION_TIME;
  registerTimeout(&leaderElectionTimeout);

    tout.callback = (GenericHandler)(&checkLeaf);
    tout.calltime = getTime() + 5000;
  registerTimeout(&tout);
  // Initialize chunks
  for(byte x=0; x < MYCHUNKS; x++) {
    myChunks[x].status = CHUNK_FREE;
  }

  // Initialize spanning tree variables
  isInATree = 0;
  leaderID = 0;
  for (byte p = 0 ; p < NUM_PORTS ; p++) children[p] = 0;  

  // Initialize neighbor variables
  numSideNeighbors = 0;
  if (thisNeighborhood.n[DOWN] != VACANT) hasFaceNeighbor[DOWN] = 1;
  else hasFaceNeighbor[DOWN] = 0;    
  if (thisNeighborhood.n[UP] != VACANT) hasFaceNeighbor[UP] = 1;
  else hasFaceNeighbor[UP] = 0;
  for (byte p = 1; p < 5; p++) {
    if (thisNeighborhood.n[p] != VACANT) {
      hasFaceNeighbor[p] = 1;
      sideNeighbors[numSideNeighbors++] = p;
    }
    else hasFaceNeighbor[p] = 0;
  }

  //------- Determine layer of each block
  // A block will assume it is on the ensemble's lowest layer until it receives a I_HAVE_BOTTOM_NEIGHBOR message from its side neighbors. 
  // Same goes with highest layer and I_HAVE_TOP_NEIGHBOR.
  if (hasFaceNeighbor[DOWN]) {
    isOnBottomLayer = 0;
    for (byte i=0; i < numSideNeighbors; i++) {
      sendCustomLayerChunk(I_HAVE_BOTTOM_NEIGHBOR, sideNeighbors[i]); 
    }
  }
  if (hasFaceNeighbor[UP]) {
    isOnTopLayer = 0;
    for (byte i=0; i < numSideNeighbors; i++) {
      sendCustomLayerChunk(I_HAVE_TOP_NEIGHBOR, sideNeighbors[i]); 
    }
  }
  // Then, they all wait for the layer below them to provide them with their layer.
  while(1);
}

// Start gravity game*
void
startGame(void)
{
  //some stuff...
}  

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

// --------------------- ENSEMBLE HIERARCHY FUNCTIONS

// On each potential top level, the block with the highest id will set up a spanning tree to spread its level. Blocks will only get into trees with a higher top layer number than theirs.
void 
setUpSpanningTree(void)
{
  if (isOnTopLayer) {
    numChildren = 0;
    setColor(GREEN);
    topLayer = currentLayer;
    isInATree = getGUID();   
    for (byte p = 0 ; p < NUM_PORTS ; p++) {
      if (thisNeighborhood.n[p] != VACANT) {	
	sendSpanningTreeMessage(p, ADD_YOURSELF, isInATree, topLayer);
      }
    }
  }
  else 
  {
    setColor(RED);
  }
}

byte
sendSpanningTreeMessage(PRef p, byte messageType, uint16_t id, byte layer)
{
  Chunk *c = getFreeUserChunk();
  
  byte buf[4];
  buf[0] = messageType;
  GUIDIntoChar(id, &(buf[1]));
  buf[3] = layer;
  
  if (c != NULL) {      
    if ( sendMessageToPort(c, p, buf, 4, layerMessageHandler, NULL) == 0 ) {
      freeChunk(c);
      return 0;
    }
  }
  return 1;
}

// Send a chunk with a specific message type
byte
sendCustomLayerChunk(byte messageType, PRef p)
{
  Chunk *c = getFreeUserChunk();
 
  c->data[0] = messageType;

  if (c != NULL) {      
    if ( sendMessageToPort(c, p, c->data, 1, layerMessageHandler, NULL) == 0 ) {
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
    if ( sendMessageToPort(c, p, c->data, 2, layerMessageHandler, NULL) == 0 ) {
      freeChunk(c);
      return 0;
    }
  }
  return 1;
}

// Process received chunk depending on its type on the block's situation
byte
layerMessageHandler(void)
{
  if (thisChunk == NULL) return 0;
  byte messageType = thisChunk->data[0];
  byte chunkSource = faceNum(thisChunk);
  switch (messageType) {
  case LAYER_UPDATE: { 
    byte newLayer = thisChunk->data[1];
    if (newLayer > currentLayer) {
      currentLayer = newLayer;
      setColor(currentLayer % NUM_COLORS);
      for (byte i=0; i < numSideNeighbors; i++) {
	sendLayerUpdate(newLayer, sideNeighbors[i]);
      }
      if (hasFaceNeighbor[UP]) sendLayerUpdate((currentLayer+1), UP);
      if (hasFaceNeighbor[DOWN] && (chunkSource != DOWN) ) sendLayerUpdate((newLayer-1), DOWN);
    }
  }
    // else ignore message.
    break;
  case I_HAVE_BOTTOM_NEIGHBOR: {
    if (isOnBottomLayer) {
      isOnBottomLayer = 0;
      for (byte i=0; i < numSideNeighbors; i++) {
	sendCustomLayerChunk(I_HAVE_BOTTOM_NEIGHBOR, sideNeighbors[i]);
      }
    }
    // else ignore message. 
  }
    break; 
  case I_HAVE_TOP_NEIGHBOR: {
    if (isOnTopLayer) {
      isOnTopLayer = 0;
      for (byte i=0; i < numSideNeighbors; i++) {
	sendCustomLayerChunk(I_HAVE_TOP_NEIGHBOR, sideNeighbors[i]);
      }
    }
    // else ignore message. 
  }
    break; 
  case ADD_YOURSELF: {
    uint16_t potentialLeaderID = charToGUID(&(thisChunk->data[1]));
    byte potentialTopLayer = thisChunk->data[3];
    if (!isInATree) {
      setColor(BLUE);
      isInATree = potentialLeaderID;
      topLayer = potentialTopLayer;
      parent = chunkSource;
      sendSpanningTreeMessage(chunkSource, IA_TREE, isInATree, topLayer);
      buildChildren();
    }
    else if (isInATree && isInATree == potentialLeaderID) {
      sendSpanningTreeMessage(chunkSource, A_TREE, isInATree, topLayer);
    }
    else if (isInATree && isInATree != potentialLeaderID) {
      if ( potentialTopLayer > topLayer ) {
	topLayer = potentialTopLayer;
	isInATree = potentialLeaderID;
	parent = chunkSource;
        sendSpanningTreeMessage(chunkSource, IA_TREE, isInATree, topLayer);
	buildChildren();
    }else if ( potentialTopLayer == topLayer){
	      if ( potentialLeaderID < isInATree ){
		  isInATree = potentialLeaderID;
		  topLayer = potentialTopLayer;
		  parent = chunkSource;
		  sendSpanningTreeMessage(chunkSource, IA_TREE, isInATree, topLayer);
		  buildChildren();
	      }
    }
    }
    else {
      sendSpanningTreeMessage(chunkSource, A_TREE, isInATree, topLayer);
    }
  } 
    break;
  case IA_TREE: {
    children[numChildren++] = chunkSource; 
    countChildren = numChildren;
  }
    break;
  case A_TREE: 
    return 0;
    break;
  case ST_OK: {
    uint16_t agreedLeaderID = charToGUID(&(thisChunk->data[1]));
    byte agreedTopLayer = thisChunk->data[3];
    countChildren--;
    if (countChildren == 0) {
      if ( (getGUID() == agreedLeaderID)&&(currentLayer == agreedTopLayer) ) {
	setColor(ORANGE);
      }
      else {
	sendSpanningTreeMessage(parent, ST_OK, isInATree, topLayer);
	setColor(AQUA);
      }
    }
    // else continue waiting for other children's response
  }
    break;
  }
  
  return 1;
}
 void checkLeaf(void)
 {
   if(numChildren == 0){
       setColor(YELLOW);
       sendSpanningTreeMessage(parent, ST_OK, isInATree, topLayer);
  }
 }
void
buildChildren(void)
{
  numChildren = 0;
  // Gets children
  for (byte p = 0 ; p < NUM_PORTS ; p++) {
    if (p == parent || thisNeighborhood.n[p] == VACANT) continue;
    else {
      sendSpanningTreeMessage(p, ADD_YOURSELF, isInATree, topLayer);
    }
  }
  
}

// Spreads layer info from the base to the top, incrementing sent number on each layer.
void
spreadLayerInfo(void)
{
  if (isOnBottomLayer) {
    // Send new layer to next layer
    if (hasFaceNeighbor[UP]) sendLayerUpdate((currentLayer+1), UP);
  }
  // else do nothing, keep on waiting.
}

void 
userRegistration(void)
{
  registerHandler(SYSTEM_MAIN, (GenericHandler)&myMain);
}
