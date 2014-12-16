#include <stdarg.h>
#include <string.h>
#include <stdio.h>
#include <stdlib.h>
//#include <avr/wdt.h>


#include "log.bbh"
#include "ensemble.bbh"
#include "data_link.bbh"
#include "serial.bbh"
#include "span.bbh"

#define FILENUM 4

#define UNDEFINED_HOST 200

#define LOG_I_AM_HOST			0x01
#define LOG_PATH_TO_HOST		0x02
#define LOG_NEED_PATH_TO_HOST		0x03 // UNNECESSARY AND CAUSING MEMORY ISSUES
#define LOG_DATA			0x04
#define LOG_CMD			        0x05
#define LOG_OUT_OF_MEMORY               0x06
#define LOG_ASSERT                      0x07 

//CMD TYPES
#define COLOR_SET	0x10
#define ENSEMBLE_RESET	0x11
#define SET_ID          0x12
#define TREE_COUNT	0x14

static byte getSize(char* str);
static void freeLogChunk(void);
static Chunk* getLogChunk(void);

//#define FORCE_TRANSMISSION

threadvar byte PCConnection = 0;
threadvar PRef toHost = 200; //= NUM_PORT; // UNDEFINED_HOST (compilation error with SIM)
threadvar byte seq = 0; // sequence number for avoiding loops when broadcasting commands


//Need to create spanning tree for all the debug messages flowing from the host to all the blocks
//SpanningTree* tree;
//byte spFinished;
//void donefunc(SpanningTree* tree, SpanningTreeStatus status);

void donefunc(SpanningTree* spt, SpanTreeState status)
{ 
  //blockprint(stderr, "DONEFUNC: %d %d\n", spt->spantreeid, status);
}

//Spanning tree related

SpanningTree* dbg_tree;

// spanning tree broadcast message
int node_count = 0;
void
get_the_count(byte* msg)
{
  if(!dbg_tree)return;
  if (isSpanningTreeRoot(dbg_tree)) {
    //int count =0 ;// = treeCount(dbg_tree, 0);
    int count = treeCount(dbg_tree, 0);
    char m[5];
    sprintf(m,"$%d",count);
    printDebug(m);	
  }
  //treeBarrier(dbg_tree, 0);
}


//////////////////// PUBLIC FUNCTIONS /////////////////////
// Send a log string to host
byte printDebug(char* str) {
	byte size = getSize(str);
	static byte mId = 0;
	byte index = 0;
	byte buf[DATA_SIZE];
	byte s = 0;	
	byte fId = 0;
	byte off = 6;
	//byte random =  rand() % 45 + 1; 
	if (toHost == UNDEFINED_HOST)
	{
		return 0;
	}

	buf[0] = LOG_MSG;
	buf[1] = LOG_DATA;
	GUIDIntoChar(getGUID(), &(buf[2]));
	buf[4] = mId;

	if (size == 1) 
	{
		off = 7;
		s = strlen(str)+1;
		memcpy(buf+off, str, s);
		buf[5] = 0;	
		buf[6] = size;
		sendLogChunk(toHost, buf, s+off);
	} 
	else
	{	
		for (fId = 0; fId < size; fId++)
		{
			buf[5] = fId;
			if (fId == 0)
			{
				buf[6] = size;
				off = 7;
				s = 10;
			}
			else if (fId == (size -1))
			{
				s = strlen(str+index)+1 ;
			} 
			else
			{
				s = 11;
			}
			memcpy(buf+off, str+index, s);
			index += s;
			//delayMS(random);
			sendLogChunk(toHost, buf, s+off);
			off = 6;
		}
	}
	mId++;
	return 1;
}

byte blockingPrintDebug(char *s)
{
	while(toHost == UNDEFINED_HOST)
	{
		delayMS(1);
	}
	return printDebug(s);
}
////////////////// END PUBLIC FUNCTIONS ///////////////////

//////////////////// SYSTEM FUNCTIONS /////////////////////
//
// -------------- HOST DISCOVERY FUNCTIONS

void initLogDebug(void)
{
	//byte buf[2];

	 char p[5];
	toHost = UNDEFINED_HOST;
	PCConnection = 0;

	/*buf[0] = LOG_MSG;
	  buf[1] = LOG_NEED_PATH_TO_HOST;
	  byte p;*/

	setColor(ORANGE); // to remember to the user that the block is waiting
	while(toHost == UNDEFINED_HOST)
	{
		/* for( p = 0; p < NUM_PORTS; p++)
		   {
		   if ((thisNeighborhood.n[p] == VACANT))
		   {
		   continue;
		   }
		   sendLogChunk(p, buf, 2, __LINE__);
		   }*/
		delayMS(500);
	}
	srand(getGUID());
	//Add spanning tree here
#if 0
	if(PCConnection)
	{
		setColor(GREEN);
		delayMS(1000);
	}
	else{
		int count = 10;
		while(count){
			setColor(BLUE);
			delayMS(1000);
			setColor(RED);
			delayMS(1000);
			count--;
		}
	}
#endif
	  SpanningTree* tree;
	  int baseid;
	  //blockprint(stderr, "init\n");
	  baseid = initSpanningTrees(1);
	  tree = getTree(baseid);
	  if(PCConnection){
		  createSpanningTree(tree, donefunc, 0, 0, 1);
	  }
	  else{
		  createSpanningTree(tree, donefunc, 0, 0, 0);
	  }

#if 0
	  if((PCConnection )&& (isSpanningTreeRoot(tree))){
		  setColor(PURPLE);
		  delayMS(1000);
		  setColor(PINK);
		  delayMS(1000);
		  setColor(PURPLE);
		  delayMS(1000);
	  }
#endif

	  //
	   dbg_tree = tree;
	  
	  //blockprint(stderr, "return\n");
	  setColor(AQUA);
	  
	  //blockprint(stderr, "finished\n");  
	  if ( treeBarrier(tree,5000) == 1 )
	    {
	      setColor(GREEN);
	    }  
	  else
	    {
	      setColor(YELLOW);
	    }
	  treeBarrier(tree, 0);
	  setColor(WHITE);
#if 0
	  if (isSpanningTreeRoot(tree)) {
	    node_count = treeCount(tree, 0);
	    printDebug("td");
	    sprintf(p,"@@%d",node_count);
	    printDebug(p);
	    

	  }
#endif
	  treeBarrier(tree, 0);
	  char m[2];
	  m[0] = 'd';
	  m[1] = '\0';
	  printDebug(m);
	  
}

byte isHostPort(PRef p)
{
	return ((p == toHost) && (PCConnection == 1));
}

	static
void sendPathToHost(PRef p)
{
	byte buf[2];

	buf[0] = LOG_MSG;
	buf[1] = LOG_PATH_TO_HOST;
	sendLogChunk(p, buf, 2);
}

	static
void spreadPathToHost(PRef excluded)
{
	byte p;

	for( p = 0; p < NUM_PORTS; p++) {
		if ((p == excluded) || (thisNeighborhood.n[p] == VACANT)) {
			continue;
		}
		sendPathToHost(p);
	}
}

#if 0
static void spread_message(PRef excluded) {
	byte p;

	for( p = 0; p < NUM_PORTS; p++) {
		if ((p == excluded) || (thisNeighborhood.n[p] == VACANT)) {
			continue;
		}
		sendLogChunk(p, thisChunk->data, thisChunk->data[3]);
	}
}
#endif





	static
void forwardToHost(Chunk *c)
{
	if(toHost != UNDEFINED_HOST) {
		sendLogChunk(toHost, c->data, DATA_SIZE);
	}
}

// ------------------ CRITICAL DEBUGGING FUNCTIONS

Chunk emergencyChunk;

	void
reportLoggerOutOfMemory(PRef failurePort)
{
	Chunk* c = &emergencyChunk;
	emergencyChunk.next = NULL;
	emergencyChunk.status = CHUNK_USED;
	byte buf[DATA_SIZE];
	buf[0] = LOG_MSG;
	buf[1] = LOG_OUT_OF_MEMORY;
	buf[2] = failurePort;
	GUIDIntoChar(getGUID(), &(buf[3]));
	sendMessageToPort(c, toHost, buf, 5, (MsgHandler)RES_SYS_HANDLER, (GenericHandler)&freeLogChunk);
	return; 
}

	void
reportAssert(byte fn, int ln)
{
	Chunk* c = &emergencyChunk;
	emergencyChunk.next = NULL;
	emergencyChunk.status = CHUNK_USED;
	byte buf[DATA_SIZE];
	buf[0] = LOG_MSG;
	buf[1] = LOG_ASSERT; 
	GUIDIntoChar(getGUID(), &(buf[2]));
	buf[4] = fn;
	buf[5] = 0xff & (ln>>8);
	buf[6] = ln & 0xff;
	sendMessageToPort(c, toHost, buf, 7, (MsgHandler)RES_SYS_HANDLER, (GenericHandler)&freeLogChunk);
	return; 
}


	void
report_something(byte what, int how_much)
{
#if 0
	Chunk* c = &emergencyChunk;
	emergencyChunk.next = NULL;
	emergencyChunk.status = CHUNK_USED;
	byte buf[DATA_SIZE];
	buf[0] = LOG_MSG;
	buf[1] = LOG_ASSERT; 
	GUIDIntoChar(getGUID(), &(buf[2]));
	buf[4] = fn;
	buf[5] = 0xff & (ln>>8);
	buf[6] = ln & 0xff;
	sendMessageToPort(c, toHost, buf, 7, (MsgHandler)RES_SYS_HANDLER, (GenericHandler)&freeLogChunk);
#endif
	printDebug("rs");
	byte buf[DATA_SIZE];
	if (toHost == UNDEFINED_HOST)
	{
		return 0;
	}

	buf[0] = LOG_MSG;
	buf[1] = what;
	GUIDIntoChar(getGUID(), &(buf[2]));
	buf[4] = how_much;
	sendLogChunk(toHost, buf, 5);

}

// --------------- CHUNK SENDING FUNCTIONS

	byte 
sendCmdChunk(PRef p, byte *d, byte s, MsgHandler mh) 
{
	Chunk *c=getLogChunk();
	if (c == NULL) {
		reportLoggerOutOfMemory(p);
		//setColor(PINK);
		return 0;
	}
	if (sendMessageToPort(c, p, d, s, mh, (GenericHandler)&freeLogChunk) == 0) {
		freeChunk(c);
		return 0;
	}
	return 1;
}

// send message in d to port p for logging to host
byte sendLogChunk(PRef p, byte *d, byte s)
{
	return sendCmdChunk(p, d, s, (MsgHandler)RES_SYS_HANDLER);
}

// --------------- MESSAGE HANDLING FUNCTIONS

	void 
commandHandler(void)
{
	switch (thisChunk->data[2]) {
		case COLOR_SET:
			printDebug("color_set");//this is me
			callHandler(EVENT_COMMAND_RECEIVED);//color_set
			break;
		case SET_ID:
			callHandler(EVENT_COMMAND_RECEIVED);
			break;
		case TREE_COUNT:
			{
				if(dbg_tree!=NULL){
					  if (isSpanningTreeRoot(dbg_tree)&&PCConnection) {
					    int count = treeCount(dbg_tree, 0);
					    char m[5];
					    sprintf(m,"$%d",count);
					    printDebug(m);	
					    report_something(TREE_COUNT,count);
					    
					  }
					break;
				}
			}
		case ENSEMBLE_RESET:
			setColor(GREEN);
			//printDebug("RESET");
			//asm("wdr");//watchdog reset
			//wdt_reset();
			//wdt_enable(WDTO_15MS); //wd on,15ms 
			//setColor(GREEN);
			//while(1); //loop
			//spread_message(faceNum(thisChunk));
			//callHandler(EVENT_COMMAND_RECEIVED);
			//jumpToBootSection();
			//asm("JMP 0x0");
			/*asm volatile ( 
					"clr r1" "\n\t"
					"push r1" "\n\t"
					"push r1" "\n\t" 
					"ret"     "\n\t" 
					::); 
			for(;;);*/
			break;
			/*case SEND_ATTENDANCE:
			  printDebug("");//just sending a dummy message to latch the addresses from the response
			  spread_message(faceNum(thisChunk));*/

	}
}

	byte 
handleLogMessage(void)
{
	if( thisChunk == NULL ) 
	{
		return 0;
	}

	switch(thisChunk->data[1])
	{
		case LOG_I_AM_HOST:
			setColor(WHITE);
			toHost = faceNum(thisChunk);			
			PCConnection = 1;
			//setColor(BLUE);
			//delayMS(1000);
			spreadPathToHost(faceNum(thisChunk));
			break;
		case LOG_PATH_TO_HOST:
			if (toHost == UNDEFINED_HOST) {
				setColor(WHITE);
				toHost = faceNum(thisChunk);
				spreadPathToHost(faceNum(thisChunk));
			}
			break;
			/*case LOG_NEED_PATH_TO_HOST:
			  if (toHost != UNDEFINED_HOST) {
			  sendPathToHost(faceNum(thisChunk));
			  }
			  break;*/
		case LOG_DATA:
			if(toHost != UNDEFINED_HOST) {
				forwardToHost(thisChunk);
			}
			break;		
		case LOG_CMD:
			commandHandler();
			break;
		case LOG_ASSERT:
			if(toHost != UNDEFINED_HOST) forwardToHost(thisChunk);
			break;
		case LOG_OUT_OF_MEMORY:
			if(toHost != UNDEFINED_HOST) forwardToHost(thisChunk);
			break;
		case TREE_COUNT:
			if(toHost != UNDEFINED_HOST) forwardToHost(thisChunk);
			break;
	}	
	return 1;
}

// --------------- CHUNK MANAGEMENT

threaddef #define NUM_LOG_CHUNK 35
threadvar Chunk logChunkPool[NUM_LOG_CHUNK];

static Chunk* getLogChunk(void)
{
	//Chunk *p = getSystemTXChunk();
	//return p;
	byte i = 0;
	Chunk *cp = NULL;
	for(i = 0; i < NUM_LOG_CHUNK ; i++) {
		// check top bit to indicate usage
		cp = &logChunkPool[i];
		if(!chunkInUse(cp) ) {
			// indicate in use
			cp->status = CHUNK_USED;
			cp->next = NULL;
			return cp;
		}
	}
	return NULL;
}

	static void 
freeLogChunk(void)
{
	freeChunk(thisChunk);
	thisChunk->status = CHUNK_FREE;
}

// ------------- UTILITY

static byte getSize(char* str) {
	byte sizeCar = 0;
	byte sizeChunk = 1;

	if (str == NULL) {
		return 0;
	}

	sizeCar = strlen(str) + 1;

	if (sizeCar < 11) {
		return 1;
	}

	sizeCar -= 10;
	sizeChunk += sizeCar / 11;
	if ((sizeCar % 11) != 0) {
		sizeChunk++;
	}
	return sizeChunk;
}

/*
void donefunc(SpanningTree* tree, SpanningTreeStatus status)
{ 
   

  if(status == COMPLETED)
  {
   if (isSpanningTreeRoot(tree) == 1)
  {
    setColor(YELLOW);
  }
  else
  {
    setColor(WHITE);
    if(tree->numchildren == 0)
    {
      setColor(PINK);
    }
  }
  }
  
  else
  { 
    setColor(RED);
  }

}
*/
////////////////// END SYSTEM FUNCTIONS ///////////////////


// Local Variables:
// mode: c
// tab-width: 8
// indent-tabs-mode: nil
// c-basic-offset: 2
// End:
