#include "coordinate.bbh" 
#include "block.bbh"

//PRIVATE VARIABLES
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
#define VIRTUAL 0x18


// Chunk management
#define MYCHUNKS 12
extern Chunk* thisChunk;
Chunk myChunks[MYCHUNKS];
Chunk* getFreeUserChunk(void);




//PRIVATE FUNCTIONS
void startCoordination (void); //start giving the coordinates to the ensemble
void sendCoordination (void);// start sharing the coordinates to each other
void sendMsg(PRef p, signed char x, signed char y, signed char z);// message sent when giving coordinates
void sendCoord(PRef p, signed char cx, signed char cy, signed char cz); // message sent during the exchange of coordinates
byte checkneighbor(byte p); //check the neighbor on the port p if it exists
byte coordinateHandler(void); //handler for all the coordinate system
void sendToVirtualNeighbor(PRef p, byte *data,byte size, signed char cx, signed char cy, signed char cz); //message sent to the virtual neighbor
byte sendMyChunk(byte port, byte *data, byte size, MsgHandler mh);
MsgHandler vhandler;

void sendCoord(PRef p, signed char cx, signed char cy, signed char cz)
{
   byte data[4];
  
  data[0] = SEND_COOR;
  data[1] = cx;
  data[2] = cy;
  data[3] = cz;
  sendMyChunk(p, data, 4, (MsgHandler)&coordinateHandler);
}


void sendLog(void)
{
  #ifdef LOG_DEBUG
  signed char s[15];
  snprintf(s, 15*sizeof(signed char), "%d",numCoord);
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
  sendMyChunk(p, data, 4, (MsgHandler)&coordinateHandler); 
}

void sendToVirtualNeighbor(PRef p,byte *data, byte size, signed char cx, signed char cy, signed char cz)
{
  byte buf[size + 5];
  buf[0] = VIRTUAL;
  buf[1] = cx;
  buf[2] = cy;
  buf[3] = cz;
  buf[4] = size;
  memcpy(buf + 5, data, size*sizeof(byte));
  sendMyChunk(p, buf, size + 5, (MsgHandler)&coordinateHandler);
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
 //Share his coordinates after 1 second
   propagateCoord.calltime = getTime() + 500;
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
coordinateHandler(void)
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
    setColor(YELLOW);
    sendCoordination();
  /* propagateCoord.calltime = getTime() + 1000;
   registerTimeout(&propagateCoord); */
	alreadyShared = 1;
	}
  }
  break;
   case VIRTUAL:
  {
    setColor(AQUA);
   signed char cx = thisChunk->data[1];
   signed char cy = thisChunk->data[2];
   signed char cz = thisChunk->data[3];
   byte size = thisChunk->data[4];
   byte buf[size];
   memcpy(buf, thisChunk->data + 5, size*sizeof(byte));
     if( x == cx && y == cy && z == cz)
    {
      vhandler();
      return 0;
    }
  
   for(byte i=0; i<numCoord; i++)
   {
     if( coord[i][0] == cx && coord[i][1] == cy && coord[i][2] == cz)
     {
	sendToVirtualNeighbor(coord[i][3],buf,size, cx, cy, cz);
	return 0;
     }
     else continue;
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






//PUBLIC FUNCTIONS

//give coordinates to all the blocks and each blocks share their coordinates to each other, the block with the ID id will be the origin with the coordinate (0, 0, 0)
//There is a timeout to know when the blocks should share their coordinates, the variable timeout is the time for all the blocks to get coordinates before shareing these coordinates
//the timeout depends on the number of blocks in the ensemble
void  initCoordination(uint16_t id, MsgHandler donefunc)
{
  setColor(WHITE);
  // We are forced to use a small delay before program execution, otherwise neighborhood may not be initialized yet
  delayMS(300);
  vhandler = donefunc;
    // Initialize chunks
  for(byte x=0; x < MYCHUNKS; x++) {
    myChunks[x].status = CHUNK_FREE;
  }
  propagateCoord.callback = (GenericHandler)(&sendCoordination);
  logTimeout.callback = (GenericHandler)(&sendLog);
  logTimeout.calltime = getTime() + 5000;
  registerTimeout(&logTimeout); 
  
 if(getGUID() == id)
 {
   startCoordination();
 }
}

//check if a virtual neighbor exist somewhere, if yes return 1 and if no return 0
byte checkVirtualNeighbor(PRef port)
{
  signed char cx = x;
  signed char cy = y;
  signed char cz = z;
  if (port == UP){ cy = y + 1 ;}
  if (port == DOWN){ cy = y - 1 ;}
  if (port == EAST){ cx = x - 1 ;}
  if (port == WEST){ cx = x + 1 ;}
  if (port == NORTH){ cz = z + 1 ;}
  if (port == SOUTH){ cz = z - 1 ;}
    
  
   for(byte i=0; i<numCoord; i++)
   {
     if( coord[i][0] == cx && coord[i][1] == cy && coord[i][2] == cz)
     {
       return 1;
     }
   }
   return 0;
}


//send a data to a virtual neighbor
void sendDataToVirtualNeighbor(PRef port,byte *data, byte size)
{
  if (checkVirtualNeighbor(port) == 0)
  {
    return ;
  }
  
  signed char cx = x;
  signed char cy = y;
  signed char cz = z;
  if (port == UP){ cy = y + 1 ;}
  if (port == DOWN){ cy = y - 1 ;}
  if (port == EAST){ cx = x - 1 ;}
  if (port == WEST){ cx = x + 1 ;}
  if (port == NORTH){ cz = z + 1 ;}
  if (port == SOUTH){ cz = z - 1 ;}
    
  
  
   for(byte i=0; i<numCoord; i++)
   {
     if( coord[i][0] == cx && coord[i][1] == cy && coord[i][2] == cz)
     {
       	sendToVirtualNeighbor(coord[i][3], data,size, cx, cy, cz);
	return;
     }
      else continue;
   }
}



