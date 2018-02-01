#include "block.bbh"
#include "clock.bbh"

threaddef #define TIME_MASTER_IN_RED

//threaddef #define COORD_DEBUG_COLOR
//threaddef #define COORD_DEBUG_PRINTF

threaddef #define SET_COORD_DEBUG_COLOR(col) //setColor(col)

/* Use synchronized global time */
threaddef #define GET_TIME getClock

/* Use unsynchronized local time (disable synchronization) */
//threaddef #define GET_TIME getTime

threadvar byte bitcodes[7][9];
threadvar byte scrollPos;
threadvar float distance;
threadvar byte position[2];
threadvar byte xplusBorder;
threadvar byte yplusBorder;
threadvar byte myColor;

/***************/
/** functions **/
byte sendCoordChunk(PRef p);
byte coordMessageHandler(void);
byte sendSearchCorner(PRef p);
byte cornerMessageHandler(void);
byte getPixel(byte x,byte y);
Chunk* getFreeUserChunk(void);
void freeUserChunk(void);

void fillCode(byte l,char *str);

threaddef #define MYCHUNKS 12
threadextern Chunk* thisChunk;
threadvar Chunk myChunks[MYCHUNKS];

threadvar byte myColors[4][3];

void myMain(void) {
  setLED(0,0,0,0);
  position[0] = 0;
  position[1] = 0;
  myColor=0;
  xplusBorder=EAST;
  yplusBorder=UP;

  myColors[0][0]=164; myColors[0][1]=171; myColors[0][2]=179;
  myColors[1][0]=181; myColors[1][1]=206; myColors[1][2]=86;
  myColors[2][0]=0;   myColors[2][1]=138; myColors[2][2]=200;
  myColors[4][0]=178; myColors[3][1]=178; myColors[3][2]=178;

  fillCode(6,"003333200000000000000000000000000000");
  fillCode(5,"003000000000000300000000000000300000");
  fillCode(4,"003013310000200333100000002330333100");
  fillCode(3,"003030030333330300013200003000300000");
  fillCode(2,"033333313103030300030303302320300000");
  fillCode(1,"003030003003030300030300000030300000");
  fillCode(0,"003023332003030233023100003320233000");

#ifdef CLOCK_SYNC
  setLED(64,64,0,64);

  while (!isSynchronized()) {
    delayMS(6);
  }
#endif

  setLED(0,0,0,0);
	
  int x;
  float level;

  while (1) {
	  
#ifdef TIME_MASTER_IN_RED
    if (isTimeLeader()) {
      setColor(RED);
      continue;
    }
#endif
    if (position[0] == 0) {
      setLED(0,0,0,0);
    } else {
      x = position[0]-127+GET_TIME()/250;
      myColor = (x/36)%4;
      level = getPixel(x%36,position[1]-127)/255.0;
      setLED(level*myColors[myColor][0],level*myColors[myColor][1],level*myColors[myColor][2],128);
    }
    delayMS(1);
  }
}

void neighborChangeDetect() {

}

void handleLostTimeMaster(void) {
  position[0] = 0;
  position[1] = 0;
}

void handleNewTimeMaster(void) {
  // recompute coordinates
  if (thisNeighborhood.n[5-xplusBorder]!=VACANT) {
#ifdef COORD_DEBUG_COLOR
    setColor(ORANGE);
#endif
    sendSearchCorner(5-xplusBorder);
  } else if (thisNeighborhood.n[5-yplusBorder]!=VACANT) {
#ifdef COORD_DEBUG_COLOR
    setColor(ORANGE);
#endif
    sendSearchCorner(5-yplusBorder);
  } else {
#ifdef COORD_DEBUG_COLOR
    setColor(RED);
#endif
    position[0]=127;
    position[1]=127;
    
    for (byte i=0; i< 6; i++) {
      if (thisNeighborhood.n[i]!=VACANT) {
	sendCoordChunk(i);
      }
    }
  }
}

void userRegistration(void) {
  registerHandler(SYSTEM_MAIN, (GenericHandler)&myMain);
  registerHandler(NEW_TIME_MASTER_EVENT,
		  (GenericHandler)&handleNewTimeMaster);
  registerHandler(LOST_TIME_MASTER_EVENT,
		  (GenericHandler)&handleLostTimeMaster);
}

void fillCode(byte l,char *str) {
  byte c0,c1,c2,c3;
  for (byte i=0; i<9; i++) {
    c0 = str[i*4]-'0';
    c1 = str[i*4+1]-'0';
    c2 = str[i*4+2]-'0';
    c3 = str[i*4+3]-'0';
    bitcodes[l][i]=(c0<<6)+(c1<<4)+(c2<<2)+c3;
  }
}

byte getPixel(byte x,byte y) {
  if (x<0 || x>36 || y<0 || y>6) return 0;
  byte pixel = ((bitcodes[y][(x/4)%9]>>((3-(x%4))*2))) & 0x03;
  switch (pixel) {
  case 0 : return 0;
  case 1 : return 96;
  case 2 : return 192;
  case 3 : return 255;
  }
  return 0;
}

// Send a chunk with a specific message type
byte sendCoordChunk(PRef p) {
  Chunk *c = getFreeUserChunk();
  byte data[3];
	
  if (c != NULL) {
    data[0]=position[0];
    data[1]=position[1];
    data[2]=0;
    if (p==xplusBorder) {
      data[0]++;
      data[2]=1; // axe X
    } else if (p==5-xplusBorder) {
      data[0]--;
      data[2]=2; // axe -X
    } else if (p==yplusBorder) {
      data[1]++;
      data[2]=3; // axe Y
    } else if (p==5-yplusBorder) {
      data[1]--;
      data[2]=4; // axe -Y
    }
    if (sendMessageToPort(c, p, data, 3, coordMessageHandler, freeUserChunk) == 0) {
      freeChunk(c);
      return 0;
    }
  }
  return 1;
}

byte coordMessageHandler(void) {
  if (thisChunk == NULL) return 0;
  byte sender = faceNum(thisChunk);
  if (position[0]==0) {
    position[0] = thisChunk->data[0];
    position[1] = thisChunk->data[1];
		
#ifdef COORD_DEBUG_PRINTF
    printf("%d,(%d;%d),%d,%d,%d,%d\n",(int)getGUID(),position[0],position[1]);
#endif
    
    for (byte i=0; i<6; i++) {
      if ((thisNeighborhood.n[i]!=VACANT) && (i != sender)) {
	if (i!=sender)
	  sendCoordChunk(i);
      }
    }
  }
  return 1;
}

// Send a chunk with a specific message type
byte sendSearchCorner(PRef p) {
  Chunk *c = getFreeUserChunk();
  byte data[1] ={0};
	
  if (c != NULL) {
    if (sendMessageToPort(c, p, data, 0, cornerMessageHandler, freeUserChunk) == 0) {
      freeChunk(c);
      return 0;
    }
  }
  return 1;
}

byte cornerMessageHandler(void) {
  if (thisChunk == NULL) return 0;

  if (thisNeighborhood.n[5-xplusBorder]!=VACANT) {
    SET_COORD_DEBUG_COLOR(ORANGE);
    sendSearchCorner(5-xplusBorder);
  } else if (thisNeighborhood.n[5-yplusBorder]!=VACANT) {
    SET_COORD_DEBUG_COLOR(ORANGE);
    sendSearchCorner(5-yplusBorder);
  } else {
    SET_COORD_DEBUG_COLOR(RED);
    position[0]=127;
    position[1]=127;
    for (byte i=0; i< 6; i++) {
      if (thisNeighborhood.n[i]!=VACANT) {
	sendCoordChunk(i);
      }
    }
  }
  return 1;
}

// find a useable chunk
Chunk* getFreeUserChunk(void) {
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

void
freeUserChunk(void) {
  freeChunk(thisChunk);
  thisChunk->status = CHUNK_FREE; // not necessary I guess...
}
