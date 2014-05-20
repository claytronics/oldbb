
#include "block.bbh"
#include "block_config.bbh"
#include "memory.bbh"
#include "audio.bbh"
#include <stdlib.h>

byte layerMessageHandler(void);
byte sendMyChunk(PRef port, byte *data, byte size, MsgHandler mh) ;
void freeMyChunk(void);
void sendMsg(PRef p, signed char x,signed char y, signed char z);
byte checkneighbor(byte p);
void sendCoord(PRef p, signed char cx, signed char cy, signed char cz);
void sendCoordination(void);
void sendLog(void);
uint16_t id;

byte countChildren;
byte numCoord = 1;

//coordination fuction
signed char coord[30][4];

signed char x = 0; 
signed char y = 0;
signed char z = 0;

byte haveCoor = 0;
byte alreadyShared = 0;
void startCoordination(void);
void buildNeighbor(void);
byte countNeighbor(void);
Timeout propagateCoord;
Timeout logTimeout;
int numMsg = 0;

#define MSG	    0x15
#define COOR	    0x16
#define SEND_COOR	    0x17


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
  delayMS(500);
 
    // Initialize chunks
  for(byte x=0; x < MYCHUNKS; x++) {
    myChunks[x].status = CHUNK_FREE;
  }
  propagateCoord.callback = (GenericHandler)(&sendCoordination);
  logTimeout.callback = (GenericHandler)(&sendLog);
    logTimeout.calltime = getTime() + 10000;
  registerTimeout(&logTimeout); 
 
 if(getGUID() == 2)
 {
   startCoordination();
 }
  while(1);
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
  #ifdef LOG_DEBUG
  signed char s[15];
  snprintf(s, 15*sizeof(signed char), "%d",numMsg);
  printDebug(s);
  #endif 
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

void startCoordination(void)
{
    coord[0][0] = x;
    coord[0][1] = y;
    coord[0][2] = z;
    coord[0][3] = 6;
  haveCoor = 1;
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


void sendCoordination(void)
{
    alreadyShared = 1;
      for (byte p = 0; p < NUM_PORTS; p++) {
    if (thisNeighborhood.n[p] != VACANT) {
    sendCoord(p, x, y, z);
    }
    }    
}


byte checkneighbor(byte p)
{
  if (thisNeighborhood.n[p] != VACANT)
      {
	return 1;
      }
  return 0;
}


byte
layerMessageHandler(void)
{
  if (thisChunk == NULL) return 0;
  byte messageType = thisChunk->data[0];
  byte chunkSource = faceNum(thisChunk);
  switch (messageType) {
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
  
   setColor(YELLOW);
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
   propagateCoord.calltime = getTime() + 1000;
   registerTimeout(&propagateCoord); 
	alreadyShared = 1;
	}
  }
  break;
  
  return 1;
}
}


 
// find a useable chunk
Chunk* 
getFreeUserChunk(void)
{
  Chunk* c;
  byte i;

  for(i=0; i<MYCHUNKS; i++) {
    c = &(myChunks[i]);

    if( !chunkInUse(c) ) {
      return c;
    }
  }
  return NULL;
}


byte
sendSpanningTreeMessage(PRef p, byte messageType, uint16_t id, byte layer)
{
  Chunk *c = getFreeUserChunk();
  if (c == NULL) 
  {
  c = getSystemTXChunk();
  }
  
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


byte sendMyChunk(byte port, byte *data, byte size, MsgHandler mh) 
{ 
  numMsg++;
  Chunk *c=getFreeUserChunk();
  if (c == NULL) 
  {
  c = getSystemTXChunk();
  if(c == NULL){
  return 0;
  }
  }
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