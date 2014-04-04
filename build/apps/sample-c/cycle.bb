#include "handler.bbh"
#include "data_link.bbh"
#include "led.bbh"
#include "log.bbh"
#include "accelerometer.bbh"
#include "handler.bbh"
#include "block.bbh"
#include "ensemble.bbh"
#include "clock.bbh"
#include "block_config.bbh"

#define COM		3
#define COMBACK		4

byte sendMyChunk(PRef port, byte *data, byte size, MsgHandler mh); 
byte myMsgHandlerGo(void);
byte myMsgHandlerBack(void);
void sendCycle(byte);
void sendBackCycle(byte);
byte testChildResponse(void);


threadvar byte children[6];
threadvar PRef parent;
threadvar PRef isLeader=0;

    byte p;
    byte msg[17];
    char s[150];
    byte childResponse = 0;
    byte cycleNum = 1;

   
void myMain(void)
{ 
 
  setColor(WHITE);
  snprintf(s, 150*sizeof(char), "START");
  s[149] = '\0';
  printDebug(s);
  sendCycle();
     
 if (getGUID() == 2) 
 {
  setColor(RED);
  snprintf(s, 150*sizeof(char), "C %d",cycleNum);
  s[149] = '\0';
  printDebug(s);
 
      for( p = 0; p < NUM_PORTS; p++) {
	
	if (thisNeighborhood.n[p] == VACANT) {
	  continue;
	}
	else {
	   sendCycle(p);
	}
      }    
  } 
    else {
      setColor(WHITE);
    }
 }
   
 while(1);
 
}


void sendCycle(byte *child)
{
   msg[0] = COM;
   sendMyChunk(child, msg, 1, (MsgHandler)myMsgHandlerGo);
}

void sendBackCycle(byte *parent)
{
   msg[0] = COMBACK;  
   sendMyChunk(parent, msg, 1, (MsgHandler)myMsgHandlerBack);
}



byte myMsgHandlerGo(void)
{ 
  if(numChildren != 0)
  {
      for( p = 0; p < NUM_PORTS; p++) {
	if (thisNeighborhood.n[p] == VACANT) {
	  continue;
	}
	else {
	   if(children[p] == 1)
	   {
	    sendCycle(p);
	   }
	   else
	   {
	    continue;
	   }
	}
      }
  }
  else
  {
   sendBackCycle(parent);
  }
}




byte myMsgHandlerBack(void)
{ 
  if(!isLeader)
    {
      
      testChildResponse();
      
      for( p = 0; p < NUM_PORTS; p++) {
	if (thisNeighborhood.n[p] == VACANT) {
	  continue;
	}
	else
	{
	   if(parent == p)
	   {
	    sendBackCycle(p);
	   }
	   else
	   {
	    continue;
	   }
	}
	
      }
    }
  else
    {
      testChildResponse();
      snprintf(s, 150*sizeof(char), "C succeed");
	s[149] = '\0';
	printDebug(s);
      cycleNum++;
      snprintf(s, 150*sizeof(char), "C %d",cycleNum);
	s[149] = '\0';
	printDebug(s);
	sendCycle();
    }
      
 }

byte testChildResponse(void)
{
  setColor(BLUE);
  while(childResponse != numChildren)
      {
	  if(thisChunk->data[0] == COMBACK)
	  {
	    if(children[faceNum(thisChunk)] == 1)
	    {
	      childResponse++;
	    }
	    else
	    {
	      continue;
	    }
	  
	  }
      }
  setColor(GREEN);
  return 1
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



void userRegistration(void)
{
  registerHandler(SYSTEM_MAIN, (GenericHandler)&myMain);	
}