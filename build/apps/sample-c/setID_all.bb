#include "block.bbh"

#define COM			2



threadvar byte i = 1;
byte activePort;
byte sendMyChunk(PRef port, byte *data, byte size, MsgHandler mh); 
void startChangingId(void);
void checkId(byte cId);
void freeMyChunk(void);

void myMain(void)

{ 
  byte  host_id;
  byte p;
  uint16_t id = 256*i; 
  setColor(WHITE);
 
  for(p = 0; p < NUM_PORTS ; p++)
 {
   if(isHostPort(p))
   {
     host_id = getGUID();
   }
 }

 if (getGUID() == host_id) {
  setAndStoreUID(id);
  checkId(i);
 
  for (p = 0 ; p < NUM_PORTS ; p++) {
	  if (p == faceNum(thisChunk) || thisNeighborhood.n[p] == VACANT) continue;
	  else {
	    activePort = p;
	    break;
	  }
	}
	
  byte data[2];
  data[0] = COM;
  data[1] = i;
  sendMyChunk(activePort, data, 2, (MsgHandler)startChangingId);
    }
  
  while(1);
}

void 
startChangingId(void) 
{
  byte p;
  byte i = (thisChunk->data[1]) + 1;
  uint16_t id = 256*i;
  setAndStoreUID(id);
  byte data[2];
  byte neighbournum = 6;
  checkId(i);
  
  

  for (p = 0 ; p < NUM_PORTS ; p++) {
	  if (p == faceNum(thisChunk) || thisNeighborhood.n[p] == VACANT){
	    neighbournum--;
	  }
	  else {
	    activePort = p;
	    break;
	  }
	}
  if( neighbournum != 0)
  {
  data[0] = COM;
  data[1] = i;
  sendMyChunk(activePort, data, 2, (MsgHandler)startChangingId);  
  }
  
}

void checkId(byte cId)
{
  if(getGUID() == cId)
  {
    setColor(GREEN);
      char s[15];
#ifdef LOG_DEBUG
  snprintf(s, 15*sizeof(char), "Id changed");
  printDebug(s);
#endif  

  }
}

byte sendMyChunk(PRef port, byte *data, byte size, MsgHandler mh) 
{ 
  Chunk *c=getSystemTXChunk();
  if (c == NULL) return 0;
  if (sendMessageToPort(c, port, data, size, mh, (GenericHandler)&freeMyChunk) == 0) {
    freeChunk(c);
    return 0;
  }
  return 1;
}

void freeMyChunk(void)
{
  freeChunk(thisChunk);
}


void userRegistration(void)
{
  registerHandler(SYSTEM_MAIN, (GenericHandler)&myMain);	
}
