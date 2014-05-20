#include "block.bbh"
#include "block_config.bbh"
#include "memory.bbh"
#include "audio.bbh"
#include <stdlib.h>



void sendMsg(PRef p, signed char x,signed char y, signed char z);
byte checkneighbor(byte p);
void sendCoord(PRef p, signed char cx, signed char cy, signed char cz);
void sendCoordination(void);
void sendLog(void);
void sendToVirtualNeighbor(PRef p, signed char cx,signed char cy,signed char cz);
void sendToVirtualLayerNeighbor(PRef p,byte chunkSource ,signed char cx,signed char cy,signed char cz);
void checkVirtualLayerNeighbor(byte p,signed char cx,signed char cy,signed char cz);
void propagateUsedLayer(byte chunkSource);
//coordination fuction
signed char coord[25][4];
signed char x; 
signed char y;
signed char z;
byte numCoord;
byte haveCoor;
byte alreadyShared;
byte stpFinished = 0;
byte alreadyUsedLayer;
void startCoordination(void);
void buildNeighbor(void);
byte countNeighbor(void);
void stopUsedLayer(void);

Timeout propagateCoord;
Timeout logTimeout;
Timeout usedLayerTimeout;

#define MSG	    0x15
#define COOR	    0x16
#define SEND_COOR	    0x17
#define VIRTUAL	    0x18
#define U_LAYER	    0x19
#define CHECK_VLAYER	    0x20



// Accelerometer functions
void accelChange(void);
byte sendMyChunk(PRef port, byte *data, byte size, MsgHandler mh) ;
void freeMyChunk(void);
void sendAccelMsg(byte p);
byte checkneighbor(byte p);
void setTimeout(void) ;
void sendUsedLayer(void);
void blueBlock(void);
void sendBlueBlockMessage(PRef p, byte num);
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
threadvar int topLeader;
threadvar byte highestNumber;
threadvar byte number;


byte sendCustomLayerChunk(byte messageType, PRef p);
byte sendLayerUpdate(byte newLayer, PRef p);
byte layerMessageHandler(void);
void spreadLayerInfo(void);


// Spanning Tree management
threadvar PRef parent;
threadvar PRef children[NUM_PORTS];
threadvar byte numChildren;
threadvar int isInATree;
threadvar byte numExpectedChildrenAnswers;
threadvar byte numExpectedBwdMessages;
threadvar int leaderID;
int randNum;
byte countChildren;

void setUpSpanningTree(void);
byte sendSpanningTreeMessage(PRef p, byte messageType, int id, byte layer);
void buildChildren(void);
void checkLeaf(void);
void restartSpanningTree(void);



#define ADD_YOURSELF    0x10
#define IA_TREE          0x11
#define A_TREE         0x12
#define ST_OK           0x13
#define NUM_MSG    0x14
#define MSG_ACCEL	    0x09
// Game related variables and functions
void startGame(void);


// Message types
#define LAYER_UPDATE 0x01
#define I_HAVE_BOTTOM_NEIGHBOR 0x02
#define I_HAVE_TOP_NEIGHBOR 0x03
#define NUMBER_OF_BLOCKS_ON_LAYER 0x04  

// Time management
Timeout lowestLayerCheck;
#define LOWEST_LAYER_CHECK_TIME 500
Timeout leaderElectionTimeout;
#define SPECIAL_BLOCKS_ELECTION_TIME 1500
Timeout checkLeafTimeout;
#define CHECK_LEAF_TIME 3000
Timeout gameStartTimeout;
#define START_TIME 20000
Timeout accelTimeout;
#define ACCEL_TIME 1000  
Timeout blueBlockTimeout;
#define BLUE_BLOCK_TIME 15000  
Timeout coordTimeout;
Timeout restartSpanningTreeTimeout;

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
  
  //Initialize coordinates
 x = 0; 
 y = 0;
 z = 0;
 numCoord = 1;
 haveCoor = 0;
 alreadyShared = 0;
 alreadyUsedLayer = 0;

  
   //ACCELEROMETER CONFIGURATION
 // put into standby mode to update registers
    setAccelRegister(0x07, 0x18);

    // every measurement triggers an interrupt
    setAccelRegister(0x06, 0x10);

    // set filter rate
    setAccelRegister(0x08, 0x00);

    // enable accelerometer
    setAccelRegister(0x07, 0x19);
    
   initAudio(); 
    

  // Initialize Timeouts
   //Layer Timeout
  lowestLayerCheck.callback = (GenericHandler)(&spreadLayerInfo);
  lowestLayerCheck.calltime = getTime() + LOWEST_LAYER_CHECK_TIME;
  registerTimeout(&lowestLayerCheck);
   //Spanning tree Timeout
  leaderElectionTimeout.callback = (GenericHandler)(&setUpSpanningTree);
  leaderElectionTimeout.calltime = getTime() + SPECIAL_BLOCKS_ELECTION_TIME;
  registerTimeout(&leaderElectionTimeout);
  
  restartSpanningTreeTimeout.callback = (GenericHandler)(&restartSpanningTree);
  
    
   //Check Leaf and send back message Timeout
  checkLeafTimeout.callback = (GenericHandler)(&checkLeaf);
  checkLeafTimeout.calltime = getTime() + CHECK_LEAF_TIME;
  registerTimeout(&checkLeafTimeout);
  
  
  
  
  accelTimeout.callback = (GenericHandler)(&accelChange);
  usedLayerTimeout.callback = (GenericHandler)(&stopUsedLayer);
  
  //coordination Timeout
  propagateCoord.callback = (GenericHandler)(&sendCoordination);
  /*logTimeout.callback = (GenericHandler)(&sendLog);
   logTimeout.calltime = getTime() + 10000;
   //registerTimeout(&logTimeout); */

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

 setColor(WHITE);
 //begin the game if is the leader
  if (getGUID() == isInATree) {
    setTimeout();
  }
    else{
   if (number == highestNumber) {
    setColor(BLUE);
  }
  }
}  

// On each potential top level, the block will set up a spanning tree to spread its level. Blocks will only get into trees with a higher top layer number than theirs.
void 
setUpSpanningTree(void)
{
  
  restartSpanningTreeTimeout.calltime = getTime() + 2000;//after 2 seconds check if the spanning tree is finished, if not restart it
    registerTimeout(&restartSpanningTreeTimeout);
  
  if (isOnTopLayer) {
    numChildren = 0;
    setColor(GREEN);
    topLayer = currentLayer;
    isInATree = getGUID();   
    srand ( getTime() );
    number = rand() % 255 + 1;
    highestNumber = 0;
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

//start the creation of the blue block
void
blueBlock (void)
{
 if(getGUID() == isInATree){
    number = 0;
    highestNumber=0;
 for (byte p = 0 ; p < NUM_PORTS ; p++) {
      if (thisNeighborhood.n[p] != VACANT) {	
	sendBlueBlockMessage(p,highestNumber);
      }
    }
 }

}

void
sendBlueBlockMessage(PRef p, byte num)
{
  byte data[2];
  data[0] = NUM_MSG;
  data[1] = num;
  sendMyChunk(p, data, 2, (MsgHandler)&layerMessageHandler);
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
    int potentialLeaderID = thisChunk->data[1];
    byte potentialTopLayer = thisChunk->data[3];
    if (isInATree == 0) {
      srand ( getTime() );
      number = rand() % 255 + 1;
      isInATree = potentialLeaderID;
      topLayer = potentialTopLayer;
      parent = chunkSource;
      sendSpanningTreeMessage(chunkSource, IA_TREE, isInATree, topLayer);
      buildChildren();
    }
    else if (isInATree != 0 && isInATree == potentialLeaderID) {
      sendSpanningTreeMessage(chunkSource, A_TREE, isInATree, topLayer);
    }
    else if (isInATree != 0 && isInATree != potentialLeaderID) {
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
    return 1;
    break;
  case ST_OK: {
    int agreedLeaderID = thisChunk->data[1];
    byte agreedTopLayer = thisChunk->data[3];
    countChildren--;
    if (countChildren == 0) {
      if ( (getGUID() == agreedLeaderID)&&(currentLayer == agreedTopLayer) ) {
	setColor(ORANGE);
	stpFinished=1;
      }
      else {
	setColor(BROWN);
	if (isInATree != agreedLeaderID) {
	  if(isInATree > agreedLeaderID){
	  isInATree = agreedLeaderID;
	  stpFinished=1;
      sendSpanningTreeMessage(parent, ST_OK, isInATree, topLayer);
	  }
    }
    else
    {
	stpFinished=1;
	sendSpanningTreeMessage(parent, ST_OK, isInATree, topLayer);
    }
      }
    }
    // else continue waiting for other children's response
  }
    break;
        case COOR:
    {
    if (haveCoor == 0)
    {
       x = thisChunk->data[1];
       y = thisChunk->data[2];
       z = thisChunk->data[3];
  
       for( byte p = 0; p < NUM_PORTS;p++){
       if (thisNeighborhood.n[p] != VACANT &&  p != chunkSource) {	
       if( UP == p){
       sendMsg(p,x,y+1,z);
       
       }
       if( DOWN == p ){
       sendMsg(p,x,y-1,z);
       }
       if(WEST == p) 
       {
       sendMsg(p,x+1,y,z);
       }
       if(EAST == p) 
       {
       sendMsg(p,x-1,y,z);
       }
       if(NORTH == p)
       {
	sendMsg(NORTH,x,y,z+1);
       }
       if(SOUTH == p)
       {
	sendMsg(NORTH,x,y,z-1);
       }
       }
    }
    haveCoor = 1;
    setColor(AQUA);
   }
  }
  break;
  case SEND_COOR:
  {
   signed char cx = thisChunk->data[1];
   signed char cy = thisChunk->data[2];
   signed char cz = thisChunk->data[3];
   coord[0][0] = x;
   coord[0][1] = y;
   coord[0][2] = z;
   coord[0][3] = 6;
   if(alreadyShared == 1)
   {
      setColor(YELLOW);
   }
   
   for(byte i=1; i<numCoord; i++)
   {
     if( coord[i][0] == cx && coord[i][1] == cy && coord[i][2] == cz)
     {
	return 0;
     }
     else continue;
   }
  
   
   coord[numCoord][0] = cx;
   coord[numCoord][1] = cy;
   coord[numCoord][2] = cz;
   coord[numCoord++][3] = faceNum(thisChunk);
    for( byte p = 0; p < NUM_PORTS;p++){
       if (thisNeighborhood.n[p] != VACANT &&  p != chunkSource) {	
       sendCoord(p, cx, cy,cz);
       }
       } 
       if(alreadyShared == 0)
       {
   propagateCoord.calltime = getTime() + 500;
   registerTimeout(&propagateCoord); 
	alreadyShared = 1;
	}
  }
  break;
  case VIRTUAL:
  {
   signed char cx = thisChunk->data[1];
   signed char cy = thisChunk->data[2];
   signed char cz = thisChunk->data[3];
     if( x == cx && y == cy && z == cz)
    {
      setTimeout();
      return 1;
    }
  
   for(byte i=0; i<numCoord; i++)
   {
     if( coord[i][0] == cx && coord[i][1] == cy && coord[i][2] == cz)
     {
	sendToVirtualNeighbor(coord[i][3], cx, cy, cz);
	setColor(WHITE);
	return 0;
     }
     else continue;
   }
  }
  break;
  case U_LAYER:
  {
   propagateUsedLayer(chunkSource);
  }
  break;
  case CHECK_VLAYER:
  {
   signed char cx = thisChunk->data[1];
   signed char cy = thisChunk->data[2];
   signed char cz = thisChunk->data[3];
   byte ch = thisChunk->data[4];
     if( x == cx && y == cy && z == cz)
    {   
      propagateUsedLayer(ch);
      return 1;
    }
  
   for(byte i=0; i<numCoord; i++)
   {
     if( coord[i][0] == cx && coord[i][1] == cy && coord[i][2] == cz)
     {
	sendToVirtualLayerNeighbor(coord[i][3],ch, cx, cy, cz);
	return 1;
     }
     else continue;
   }
  }
  break;
  case NUM_MSG:
  {
    
    byte potentialNumber = thisChunk->data[1];
    if( number < potentialNumber ){
	highestNumber = potentialNumber;
	 for (byte p = 0 ; p < NUM_PORTS ; p++) {
      if (thisNeighborhood.n[p] != VACANT && p != chunkSource) {	
	sendBlueBlockMessage(p,highestNumber);
      }
      }
    }
    else if( number > potentialNumber ){
       highestNumber = number;
       for (byte p = 0 ; p < NUM_PORTS ; p++) {
      if (thisNeighborhood.n[p] != VACANT ) {	
	sendBlueBlockMessage(p,highestNumber);
      }
      }
     }else
     {
       return 1;
     }
     
     setColor(BLUE);
  }
  break;
  case MSG_ACCEL:
  {
    setTimeout();
  }
  break;
  }
  
  return 1;
}


void propagateUsedLayer(byte chunkSource)
{
   byte data[1];
    data[0] = U_LAYER;
    AccelData acc = getAccelData();

	int xo1,yo1,zo1;
	xo1 = acc.x;
	yo1 = acc.y;
	zo1 = acc.z;
	
    if(alreadyUsedLayer == 0)
    {
    
    alreadyUsedLayer = 1;
  if(( zo1 < -15 ) || ( zo1 > 15 ))
  { 
    if(checkneighbor(EAST) == 1 && chunkSource != EAST)sendMyChunk(EAST, data, 1, (MsgHandler)&layerMessageHandler); else if(chunkSource != EAST) checkVirtualLayerNeighbor(WEST,x-1,y,z);
    if(checkneighbor(WEST) == 1 && chunkSource != WEST)sendMyChunk(WEST, data, 1, (MsgHandler)&layerMessageHandler); else if(chunkSource != WEST) checkVirtualLayerNeighbor(EAST,x+1,y,z);
    if(checkneighbor(NORTH) == 1 && chunkSource != NORTH)sendMyChunk(NORTH, data, 1, (MsgHandler)&layerMessageHandler); else if(chunkSource != NORTH)  checkVirtualLayerNeighbor(SOUTH,x,y,z+1);
    if(checkneighbor(SOUTH) == 1 && chunkSource != SOUTH)sendMyChunk(SOUTH, data, 1, (MsgHandler)&layerMessageHandler); else if(chunkSource != SOUTH) checkVirtualLayerNeighbor(NORTH,x,y,z-1);
  } 
  if(( yo1 > 15 ) || ( yo1 < -15 ))
  {
    if(checkneighbor(UP) == 1 && chunkSource != UP)  sendMyChunk(UP, data, 1, (MsgHandler)&layerMessageHandler); else if(chunkSource != UP) checkVirtualLayerNeighbor(DOWN,x,y,z+1);
    if(checkneighbor(DOWN) == 1 && chunkSource != DOWN)sendMyChunk(DOWN, data, 1, (MsgHandler)&layerMessageHandler);else if(chunkSource != DOWN) checkVirtualLayerNeighbor(UP,x,y,z-1);
    if(checkneighbor(EAST) == 1 && chunkSource != EAST)sendMyChunk(EAST, data, 1, (MsgHandler)&layerMessageHandler);else if(chunkSource != EAST) checkVirtualLayerNeighbor(WEST,x-1,y,z);
    if(checkneighbor(WEST) == 1 && chunkSource != WEST)sendMyChunk(WEST, data, 1, (MsgHandler)&layerMessageHandler);else if(chunkSource != WEST)  checkVirtualLayerNeighbor(EAST,x+1,y,z);
  }
  if(( (zo1 >= -15 && zo1 <= 15)&&( yo1 >= -15 && yo1 <= 15)&&( xo1 <= -15)) || ( (zo1 >= -15 && zo1 <= 15)&&( yo1 >= -15 && yo1 <= 15)&&( xo1 >= 15)))
  {
    if(checkneighbor(UP) == 1 && chunkSource != UP)  sendMyChunk(UP, data, 1, (MsgHandler)&layerMessageHandler);else if(chunkSource != UP) checkVirtualLayerNeighbor(DOWN,x,y+1,z);
    if(checkneighbor(DOWN) == 1 && chunkSource != DOWN)sendMyChunk(DOWN, data, 1, (MsgHandler)&layerMessageHandler);else if(chunkSource != DOWN) checkVirtualLayerNeighbor(UP,x,y-1,z);
    if(checkneighbor(NORTH) == 1 && chunkSource != NORTH)sendMyChunk(NORTH, data, 1, (MsgHandler)&layerMessageHandler);else if(chunkSource != NORTH) checkVirtualLayerNeighbor(SOUTH,x,y,z+1);
    if(checkneighbor(SOUTH) == 1 && chunkSource != SOUTH)sendMyChunk(SOUTH, data, 1, (MsgHandler)&layerMessageHandler);else if(chunkSource != SOUTH) checkVirtualLayerNeighbor(NORTH,x,y,z-1);
  }
  //if it is not the blue block change it into YELLOW
  if(highestNumber != number){
	setColor(YELLOW);
	usedLayerTimeout.calltime = getTime() + 900;
	registerTimeout(&usedLayerTimeout); 
	}
  }
}

//In case the spanning tree is not properly done it will restart
void restartSpanningTree(void)
{
  if(stpFinished == 0){
  if(getGUID() == isInATree)
  { 
    setUpSpanningTree();   
  }
  else
  {
      isInATree = 0;
  }
  }
}
//change the used layer after 1 second
void stopUsedLayer(void)
{
  alreadyUsedLayer = 0;
  setColor(WHITE);
}

//check if virtual neighbor exist into the block coordinates tables, if not i block has no neighbor 
void checkVirtualLayerNeighbor(byte p,signed char cx,signed char cy,signed char cz)
{
    if( x == cx && y == cy && z == cz)
    {
      setColor(YELLOW);
      usedLayerTimeout.calltime = getTime() + 900;
      registerTimeout(&usedLayerTimeout);
      return ;
    }
  
   for(byte i=0; i<numCoord; i++)
   {
     if( coord[i][0] == cx && coord[i][1] == cy && coord[i][2] == cz)
     {
	sendToVirtualLayerNeighbor(coord[i][3],p, cx, cy, cz);
	return ;
     }
     else continue;
   }
}

void sendToVirtualLayerNeighbor(PRef p, byte chunkSource, signed char cx, signed char cy, signed char cz)
{
  byte data[5];
  data[0] = CHECK_VLAYER;
  data[1] = cx;
  data[2] = cy;
  data[3] = cz;
  data[4] = chunkSource;
  sendMyChunk(p, data, 5, (MsgHandler)&layerMessageHandler);
}

//check the Leaves and send back message to the leader
 void checkLeaf(void)
 {
   //Look for the blue block Timeout
   blueBlockTimeout.callback = (GenericHandler)(&blueBlock);
  blueBlockTimeout.calltime = getTime() + 7000;
  registerTimeout(&blueBlockTimeout);
  
  //create Coordinates timeout
  coordTimeout.callback = (GenericHandler)(&startCoordination);
  coordTimeout.calltime = getTime() + 2000;
  registerTimeout(&coordTimeout);
   if(numChildren == 0){
       setColor(YELLOW);
       sendSpanningTreeMessage(parent, ST_OK, isInATree, topLayer);
  }
 }
 
 //add children to the spanning tree 
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


byte checkneighbor(byte p)
{
  if (thisNeighborhood.n[p] != VACANT)
      {
	return 1;
      }
  return 0;
}


//called each time the red block changes position
void setTimeout(void) 
{
  sendUsedLayer();
  if(number == highestNumber)
  {
    setColor(GREEN);
    return ;
  }
    
  setColor(RED);
  accelTimeout.calltime = getTime() + ACCEL_TIME;
  registerTimeout(&accelTimeout);
}

//check if have virtual neighbor into his table and send message to him
void checkVirtualNeighbor(signed char cx,signed char cy,signed char cz)
{
    if( x == cx && y == cy && z == cz)
    {
      setTimeout();
      return ;
    }
  
   for(byte i=0; i<numCoord; i++)
   {
     if( coord[i][0] == cx && coord[i][1] == cy && coord[i][2] == cz)
     {
	sendToVirtualNeighbor(coord[i][3], cx, cy, cz);
	setColor(WHITE);
	return ;
     }
     else continue;
   }
   chirp(40000,2);
}

void sendToVirtualNeighbor(PRef p, signed char cx,signed char cy,signed char cz)
{
  byte data[4];
  data[0] = VIRTUAL;
  data[1] = cx;
  data[2] = cy;
  data[3] = cz;
  sendMyChunk(p, data, 4, (MsgHandler)&layerMessageHandler);
}


//Propagate message to the layer of the red block 
void sendUsedLayer(void)
{
   AccelData acc = getAccelData();

	int xo2,yo2,zo2;
	xo2 = acc.x;
	yo2 = acc.y;
	zo2 = acc.z;
   byte data[1]; 
    data[0] = U_LAYER;
  if(( zo2 < -15 ) || ( zo2 > 15 ))
  { 
    if(checkneighbor(EAST) == 1)sendMyChunk(EAST, data, 1, (MsgHandler)&layerMessageHandler); else  checkVirtualLayerNeighbor(WEST,x-1,y,z);
    if(checkneighbor(WEST) == 1)sendMyChunk(WEST, data, 1, (MsgHandler)&layerMessageHandler); else  checkVirtualLayerNeighbor(EAST,x+1,y,z);
    if(checkneighbor(NORTH) == 1)sendMyChunk(NORTH, data, 1, (MsgHandler)&layerMessageHandler); else  checkVirtualLayerNeighbor(SOUTH,x,y,z+1);
    if(checkneighbor(SOUTH) == 1)sendMyChunk(SOUTH, data, 1, (MsgHandler)&layerMessageHandler); else  checkVirtualLayerNeighbor(NORTH,x,y,z-1);
  } 
  if(( yo2 > 15 ) || ( yo2 < -15 ))
  {
    if(checkneighbor(UP) == 1)  sendMyChunk(UP, data, 1, (MsgHandler)&layerMessageHandler); else  checkVirtualLayerNeighbor(DOWN,x,y,z+1);
    if(checkneighbor(DOWN) == 1)sendMyChunk(DOWN, data, 1, (MsgHandler)&layerMessageHandler);else  checkVirtualLayerNeighbor(UP,x,y,z-1);
    if(checkneighbor(EAST) == 1)sendMyChunk(EAST, data, 1, (MsgHandler)&layerMessageHandler);else  checkVirtualLayerNeighbor(WEST,x-1,y,z);
    if(checkneighbor(WEST) == 1)sendMyChunk(WEST, data, 1, (MsgHandler)&layerMessageHandler);else  checkVirtualLayerNeighbor(EAST,x+1,y,z);
  }
  if(( (zo2 >= -15 && zo2 <= 15)&&( yo2 >= -15 && yo2 <= 15)&&( xo2 <= -15)) || ( (zo2 >= -15 && zo2 <= 15)&&( yo2 >= -15 && yo2 <= 15)&&( xo2 >= 15)))
  {
    if(checkneighbor(UP) == 1)  sendMyChunk(UP, data, 1, (MsgHandler)&layerMessageHandler);else  checkVirtualLayerNeighbor(DOWN,x,y+1,z);
    if(checkneighbor(DOWN) == 1)sendMyChunk(DOWN, data, 1, (MsgHandler)&layerMessageHandler);else  checkVirtualLayerNeighbor(UP,x,y-1,z);
    if(checkneighbor(NORTH) == 1)sendMyChunk(NORTH, data, 1, (MsgHandler)&layerMessageHandler);else  checkVirtualLayerNeighbor(SOUTH,x,y,z+1);
    if(checkneighbor(SOUTH) == 1)sendMyChunk(SOUTH, data, 1, (MsgHandler)&layerMessageHandler);else  checkVirtualLayerNeighbor(NORTH,x,y,z-1);
  }
}

void accelChange(void)
{
   
    
    
    
  AccelData acc = getAccelData();

	int xo,yo,zo;
	xo = acc.x;
	yo = acc.y;
	zo = acc.z;

//CHECK ORIENTATION
	if( zo < -15 ) //up
	{ 
	  setColor(WHITE);
	  if(checkneighbor(DOWN) == 1){
	   sendAccelMsg(DOWN);
	   chirp(20000,1);
	  }
	  else
	  {
	    checkVirtualNeighbor(x,y-1,z);
	  }
	}
	
	
	
	  if(zo > 15)//down
	{
	  setColor(WHITE);
	  if(checkneighbor(UP) == 1){
	   sendAccelMsg(UP);
	   chirp(20000,1);
	  }
	   else
	  {
	 checkVirtualNeighbor(x,y+1,z);
	  }
	}
	
	
	
         if( (zo >= -15 && zo <= 15)&&( yo >= -15 && yo <= 15)&&( xo <= -15))//left
        {
	   setColor(WHITE);
	   if(checkneighbor(EAST) == 1){
	   sendAccelMsg(EAST);
	   chirp(20000,1);
	   }
	   else
	   {
	   checkVirtualNeighbor(x-1,y,z);
	   }
	}
	
	
	
	if( (zo >= -15 && zo <= 15)&&( yo >= -15 && yo <= 15)&&( xo >= 15))//right 
	{
	   setColor(WHITE);
	   if(checkneighbor(WEST) == 1){
	   sendAccelMsg(WEST);
	   chirp(20000,1);
	   }
	   else
	   {
	   checkVirtualNeighbor(x+1,y,z);
	   }
	}
	
	  
	if( yo > 15 ) //front
	{
	    setColor(WHITE);
	    if(checkneighbor(SOUTH) == 1){
	    sendAccelMsg(SOUTH);
	    chirp(20000,1);
	    }
	    else
	    {
	    checkVirtualNeighbor(x,y,z-1);
	    }
	}
	
	
        if( yo < -15 )//back
        {
	     setColor(WHITE);
	     if(checkneighbor(NORTH) == 1){
	     sendAccelMsg(NORTH);
	     chirp(20000,1);
	     }
	     else
	     {
	     checkVirtualNeighbor(x,y,z+1);
	     }
	}
	 
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

//accelerometer send message function
void sendAccelMsg(PRef p)
{ 
  byte data[1];
  data[0] = MSG_ACCEL;
  sendMyChunk(p, data, 1, (MsgHandler)&layerMessageHandler);
}


byte
sendSpanningTreeMessage(PRef p, byte messageType, int id, byte layer)
{
  Chunk *c=getFreeUserChunk();
  if (c == NULL) 
  {
  c = getSystemTXChunk();
  if(c == NULL){
  return 0;
  }
  }
  
  byte buf[5];
  buf[0] = messageType;
  buf[1] = id;
  buf[3] = layer;
  
  
  if (c != NULL) {      
    if ( sendMessageToPort(c, p, buf, 5, layerMessageHandler, NULL) == 0 ) {
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
   Chunk *c=getFreeUserChunk();
  if (c == NULL) 
  {
  c = getSystemTXChunk();
  if(c == NULL){
  return 0;
  }
  }
 
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
   Chunk *c=getFreeUserChunk();
  if (c == NULL) 
  {
  c = getSystemTXChunk();
  if(c == NULL){
  return 0;
  }
  }
  
 
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



void sendCoord(PRef p, signed char cx, signed char cy, signed char cz)
{
   byte data[4];
  
  data[0] = SEND_COOR;
  data[1] = cx;
  data[2] = cy;
  data[3] = cz;
  
  sendMyChunk(p, data, 4, (MsgHandler)&layerMessageHandler);
}


void sendLog(void)
{
if(getGUID() == 2)
{
   for(byte i=0; i<numCoord; i++)
   {
  #ifdef LOG_DEBUG
  char s[15];
  snprintf(s, 15*sizeof(char), "x%d y%d z%d",coord[i][0],coord[i][1],coord[i][2]);
  printDebug(s);
  #endif 
  delayMS(200);
   }
   
}
}

void sendMsg(PRef p, signed char x, signed char y, signed char z)
{ 
  byte data[4];
  
  data[0] = COOR;
  data[1] = x;
  data[2] = y;
  data[3] = z;
  sendMyChunk(p, data, 4, (MsgHandler)&layerMessageHandler);
    
}

//start giving coordinates to all the block beginning from the leader
void startCoordination(void)
{
  if(getGUID() == isInATree)
  {
    coord[0][0] = x;
    coord[0][1] = y;
    coord[0][2] = z;
    coord[0][3] = 6;
  setColor(RED);
  if( checkneighbor(UP) == 1){
  sendMsg(UP,x,y+1,z);
  }
  if( checkneighbor(DOWN) == 1){
  sendMsg(DOWN,x,y-1,z);
  }
  if( checkneighbor(WEST) == 1){
  sendMsg(WEST,x+1,y,z);
  }
  if( checkneighbor(EAST) == 1){
  sendMsg(EAST,x-1,y,z);
  }
  if( checkneighbor(NORTH) == 1){
  sendMsg(NORTH,x,y,z+1);
  }
  if( checkneighbor(SOUTH) == 1){
  sendMsg(SOUTH,x,y,z-1);
  }
  
   propagateCoord.calltime = getTime() + 1000;
   registerTimeout(&propagateCoord);
   
  }
  
  gameStartTimeout.callback = (GenericHandler)(&startGame);
  gameStartTimeout.calltime = getTime() + 7000;
   registerTimeout(&gameStartTimeout);
}


void sendCoordination(void)
{
 alreadyShared = 1;
      for (byte p = 0; p < NUM_PORTS; p++) {
    if (thisNeighborhood.n[p] != VACANT) {
    sendCoord(p, x, y, z);
    }
    }
}





byte sendMyChunk(byte port, byte *data, byte size, MsgHandler mh) 
{ 
  Chunk *c=getSystemTXChunk();
 
  if (sendMessageToPort(c, port, data, size, mh, NULL) == 0) {
    freeChunk(c);
    return 0;
  }
  return 1;
}


void 
userRegistration(void)
{
  registerHandler(SYSTEM_MAIN, (GenericHandler)&myMain);
}