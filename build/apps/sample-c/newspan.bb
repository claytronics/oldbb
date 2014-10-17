#include "block.bbh"
#include "block_config.bbh"
#include "memory.bbh"
#include "audio.bbh"
#include <stdlib.h>
#include "../sim/sim.h"
#include "myassert.h"

threadtype typedef enum { STABLE, WAITING, CANCELED } SpanTreeState;
threadtype typedef enum { Root, Interior, Leaf } SpanTreeKind;
threadtype typedef enum { Vacant, Parent, Child, NoLink, Unknown, Slow } SpanTreeNeighborKind;

threadtype typedef struct _spanningtree SpanningTree;
threadtype typedef void (*SpanningTreeHandler)(SpanningTree* treee, SpanTreeState kind);

struct _spanningtree{
  PRef myParent; 		// port for my parent if there is one, else 255 == I am root
  byte numchildren;		/* number of children I have */
  SpanTreeNeighborKind neighbors[NUM_PORTS];	// what kind of node is my neighbor on each port 
  byte spantreeid;		// the id of this spanning tree
  SpanTreeState state;		// state i am in in forming the spanning tree
  SpanTreeKind kind;		// kind of node I am
  uint16_t value; 		// used in the creation of spanning trees, the name of the root
  byte bmcGeneration;		// track number of bmc calls that I recieved
  int outstanding;		// used to count messages when we are in Waiting state
  SpanningTreeHandler mydonefunc;	// handler to call when we reach first stable state
  MsgHandler broadcasthandler;
  byte lastNeighborCount;	 // last time I checked, how many neighbors I had
  Timeout spantimeout;
  // info to handle barriers
  byte barrierNumber;		 /* number of the barrier we are currently executing */
  byte waitingForBarrier;	 /* number of children that have already entered */
}; 

typedef struct _basicMsg {
  byte spid;			 /* tree it */
  char value[2];		 /* value of sender */
} BasicMsg;

typedef struct _barrierMsg {
  byte spid;			 /* tree we are operating on */
  byte num;			 /* number of this barrier */
} BarrierMsg;

threaddef #define MAX_SPANTREE_ID 16

// set by setSpanningTreeDebug
threadvar int debugmode = 0;

// list of ptrs to spanning tree structures
threadvar SpanningTree* trees[MAX_SPANTREE_ID];

// number of already allocated spanning tree structures.
threadvar int maxSpanId = 0;

// Message Handlers
void beMyChild(void);
void notReadyYet(void);
void youAreMyParent(void);
void alreadyInYourTree(void);
void sorry(void);

// General routines
void startAskingNeighors(SpanningTree* st, byte gen);
void resetNeighbors(SpanningTree* st);
byte sendMySpChunk(byte myport, byte *data, byte size, MsgHandler mh);
char* tree2str(char* buffer, byte id);
void checkStatus(SpanningTree* st);

//////////////////////////////////////////////////////////////////
// Handlers
//////////////////////////////////////////////////////////////////

threadvar Timeout nryTimeout;
threadvar byte haveTimeout = 0;

void
retrySlowBlocks(void)
{
  // indicate timeout is inactive now (in case we get a notReadyYet
  // msg from someone while we are in this function
  haveTimeout = 0;

  blockprint(stderr, "In Not Ready Timeout\n");
  int rsbCounter = 0;

  // now search all trees for children that might not have been ready before
  int id;
  for (id=0; id<MAX_SPANTREE_ID; id++) {
    SpanningTree* spt = trees[id];
    if (spt == NULL) continue;
    if (spt->state != WAITING) continue;
    // we might have children that weren't ready last time we checked
    uint16_t oldvalue = spt->value;
    int i;
    for (i=0; i<NUM_PORTS; i++) {
      if (spt->neighbors[i] == Slow) {
        // this was a slow child.  First unmark as slow
        spt->neighbors[i] = Unknown; /* indicate no longer on delay list */
        // now, resend msg to create tree
	BasicMsg msg;
	msg.spid = id;
	GUIDIntoChar(spt->value, (byte*)&(msg.value));
	assert(spt->value == oldvalue);
        sendMySpChunk(i, (byte*)&msg, sizeof(BasicMsg), (MsgHandler)&beMyChild); 
	rsbCounter++;
      }
    }
  }
  blockprint(stderr, "NRT sent %d msgs\n", rsbCounter);
}

// sent from a neighbor that hasn't set up his spanningtree structure
// yet.  Try again soon.
void
notReadyYet(void)
{
  BasicMsg* msg = (BasicMsg*)thisChunk;
  SpanningTree* st = trees[msg->spid];
  st->neighbors[faceNum(thisChunk)] = Slow;
  // if we don't have a timeout yet, register it
  if (haveTimeout) return;
  haveTimeout = 1;
  nryTimeout.callback = (GenericHandler)(&retrySlowBlocks);
  nryTimeout.calltime = getTime()+100;
  registerTimeout(&nryTimeout);
}

// sent from a neighbor that has become my child.
void
youAreMyParent(void)
{
  BasicMsg* msg = (BasicMsg*)thisChunk;
  SpanningTree* st = trees[msg->spid];
  PRef senderPort = faceNum(thisChunk); /* face we got sender's msg from */
  // make sure this msg has the proper value
  uint16_t senderValue = charToGUID((byte*)&(msg->value));
  if (senderValue != st->value) {
    // this came from a previous bemychild msg, but I have since joined another tree, so ignore it
    return;
  }
  assert(st->outstanding > 0);
  st->outstanding--;
  st->neighbors[senderPort] = Child;
  st->numchildren++;
  if (st->kind == Leaf) st->kind = Interior;
  checkStatus(st);
}

// sent from a neighbor already in this tree, who won't be my child
void
alreadyInYourTree(void)
{
  BasicMsg* msg = (BasicMsg*)thisChunk;
  SpanningTree* st = trees[msg->spid];
  PRef senderPort = faceNum(thisChunk); /* face we got sender's msg from */
  uint16_t senderValue = charToGUID((byte*)&(msg->value));
  if (senderValue != st->value) return; /* this is now old information */

  assert(st->outstanding > 0);
  st->outstanding--;
  st->neighbors[senderPort] = NoLink;
  checkStatus(st);
}

// sent from a neighbor that is in a better tree already
void
sorry(void)
{
  BasicMsg* msg = (BasicMsg*)thisChunk;
  SpanningTree* st = trees[msg->spid];
  uint16_t senderValue = charToGUID((byte*)&(msg->value));
  if (senderValue != st->value) return; /* Someone else already grabbed me */

  assert(st->outstanding > 0);
  // do nothing.  We will keep waiting until we get a child request from the neighbor that sent this.
}

// sent from a potential parent to its neighbor asking it to be its child. Received by neighbor.
// if sender's value > my value => become its child
// if sender's value = my value => i am already in this tree, so say alreadyInYourTree
// if sender's value < my value => say sorry, can't be in your tree
//
void
beMyChild(void)
{
  BasicMsg* msg = (BasicMsg*)thisChunk;
  SpanningTree* st = trees[msg->spid];
  PRef senderPort = faceNum(thisChunk); /* face we got sender's msg from */
  if ((st == NULL)||(st->value == 0)) {
    // we haven't started creating a tree here yet, so tell sender to
    // try again soon
    sendMySpChunk(senderPort, 
                  thisChunk->data, 
                  sizeof(BasicMsg), 
                  (MsgHandler)&notReadyYet);
    return;
  } 
  // lets see what we should do
  uint16_t senderValue = charToGUID((byte*)&(msg->value));
  if (senderValue > st->value) {
    // I will become sender's child
    st->bmcGeneration++;		 /* track that we just got a bemychild call that we are acting on */
    st->value = senderValue;
    resetNeighbors(st);
    st->myParent = senderPort;
    st->neighbors[senderPort] = Parent;
    sendMySpChunk(senderPort, 
                  thisChunk->data, 
                  sizeof(BasicMsg), 
                  (MsgHandler)&youAreMyParent);
    // set kind of node
    st->kind = Leaf; 
    // now talk to all other neighbors and ask them to be my children
    startAskingNeighors(st, st->bmcGeneration);
    checkStatus(st);
    return;
  } else if (senderValue == st->value) {
    // I am already in a tree with this value (before I set this to
    // not being a link I need to make sure that this wasn't a slow
    // link where we are going to send a bemychild msg back to this
    // node)
    if (st->neighbors[senderPort] != Slow) {
      st->neighbors[senderPort] = NoLink;
    }
    sendMySpChunk(senderPort, 
		  thisChunk->data, 
		  sizeof(BasicMsg), 
		  (MsgHandler)&alreadyInYourTree);
    return;
  } else if (senderValue < st->value) {
    // I am in a better tree, tell sender no luck
    sendMySpChunk(senderPort, 
                  thisChunk->data, 
                  sizeof(BasicMsg), 
                  (MsgHandler)&sorry);
    return;
  }
}

////////////////////////////////////////////////////////////////

static char* kind2str(SpanTreeKind x)
{
  switch (x) {
  case Root: return "Root";
  case Interior: return "Interior";
  case Leaf: return "Leaf";
  }
  return "???";
}

static char* state2str(SpanTreeState x)
{
  switch (x) {
  case STABLE: return "STABLE";
  case WAITING: return "WAITING";
  case CANCELED: return "CANCELED";
  }
  return "???";
}

static char* nstate2str(SpanTreeNeighborKind x)
{
  switch (x) {
  case Vacant: return "Vacant";
  case Parent: return "Parent";
  case Child: return "Child";
  case NoLink: return "NoLink";
  case Unknown: return "Unknown";
  case Slow: return "Slow";
  }
  return "????";
}

// thinkChunk has just been sent
static void
freeSpChunk(void)
{
  freeChunk(thisChunk);
  thisChunk->status = CHUNK_FREE;
}

byte sendMySpChunk(byte myport, byte *data, byte size, MsgHandler mh) 
{ 
  Chunk *c=getSystemTXChunk();
  char buffer[128];
  
  buffer[0] = 0;
  int i;
  for (i=0; i<size; i++) {
    char mbuf[10];
    sprintf(mbuf, "%2x ", data[i]);
    strcat(buffer, mbuf);
  }
  char tbuff[256];
  tree2str(tbuff, data[0]);
  blockprint(stderr, "== %d->%p [%s]   %s using %p\n", 
             myport, mh, buffer, tbuff, c);
  if (sendMessageToPort(c, myport, data, size, mh, (GenericHandler)freeSpChunk) == 0) {
    freeChunk(c);
    blockprint(stderr, "FAILED TO SEND\n");
    return 0;
  } else {
    return 1;
  }
}

// called when a neighbor indicates they are in my tree, either a child, or some other node
void
checkStatus(SpanningTree* st)
{
  if (st->outstanding == 0) st->state = STABLE;
  char buffer[512];
  blockprint(stderr, "Status: %s: %s\n", state2str(st->state), tree2str(buffer, st->spantreeid));
}

// called when we are starting to enter a new tree.  Reset everything
void
resetNeighbors(SpanningTree* st)
{
  for (int i=0; i<NUM_PORTS; i++) {
    st->neighbors[i] = Unknown;
  }
  st->numchildren = 0;
  st->outstanding = 0;
  st->lastNeighborCount = getNeighborCount();
  st->state = WAITING;
}

// will send a msg to all Unknown neighbors asking them to be my child
// state of node is WAITING.
// track number of outstanding messages
void
startAskingNeighors(SpanningTree* st, byte gen)
{
  BasicMsg msg;
  msg.spid = st->spantreeid;
  GUIDIntoChar(st->value, (byte*)&(msg.value));
  uint16_t oldvalue = st->value;

  for (int i=0; i<NUM_PORTS; i++) {
    if (gen != st->bmcGeneration) {
      // another bemychild msg came in with a better value for a tree, so abort the rest of this one.
      char buffer[512];
      blockprint(stderr, "Aborting asking neighbors: %d->%d: %s\n", gen, st->bmcGeneration, tree2str(buffer, st->spantreeid));
      return;
    }
    if (isPortVacant(i)) {
      if (!((st->neighbors[i] == Vacant)||(st->neighbors[i] == Unknown))) {
	// system thinks port is vacant, but i don't???
	char buffer[512];
	blockprint(stderr, "%d is %s, but system thinks it is vacant\nstate is: %s", i, nstate2str(st->neighbors[i]), tree2str(buffer, st->spantreeid));
      }
      assert((st->neighbors[i] == Vacant)||(st->neighbors[i] == Unknown));
      st->neighbors[i] = Vacant;
    } else {
      if (st->neighbors[i] == Unknown) {
	st->outstanding++;
	assert(oldvalue == st->value);
	sendMySpChunk(i, 
		      (byte*)&msg, 
		      sizeof(BasicMsg), 
		      (MsgHandler)&beMyChild);
	
      }
    }
  }
}

char* 
tree2str(char* buffer, byte id)
{
  SpanningTree* st = trees[id];
  if (st == NULL) {
    sprintf(buffer, "%d: TREE NOT ALLOCATED", id);
    return buffer;
  }
  sprintf(buffer, 
          "%d: val:%d outstndg:%d parent:%d <%s> status:%d numchdrn:%d %s [", 
          id, st->value, st->outstanding, st->myParent, 
          state2str(st->state), st->state, st->numchildren, kind2str(st->kind));
  char* bp = buffer + strlen(buffer);
  int i;
  for (i=0; i<NUM_PORTS; i++) {
    sprintf(bp, " %s", nstate2str(st->neighbors[i]));
    bp += strlen(bp);
  }
  strcat(bp, " ]");
  return buffer;
}

////////////////////////////////////////////////////////////////
// API
////////////////////////////////////////////////////////////////

void
initSpanningTreeInformation(void)
{
  int i;
  for (i=0; i<MAX_SPANTREE_ID; i++) {
    trees[i] = NULL;
  }
}

// allocate a set of <num> spanning trees.  If num is 0, use system default.
// returns the base of num spanning tree to be used ids.
int 
initSpanningTrees(int num)
{
  if((maxSpanId + num) < MAX_SPANTREE_ID) {
    int i; 
    for( i = 0 ; i<num; i++) {
      SpanningTree* spt = (SpanningTree*)calloc(1, sizeof(SpanningTree));
      trees[maxSpanId] = spt;
      spt->spantreeid = maxSpanId; /* the newId of this spanning tree */
      resetNeighbors(spt);
      maxSpanId++;
    }
    return (maxSpanId-num);     /* return base number to be used */
  } else {
    return -1; //the wanted allocation number exceed the maximun
  }
}

// return the tree structure for a given tree id
SpanningTree*
getTree(int id)
{
  assert(id < MAX_SPANTREE_ID);
  SpanningTree* ret = trees[id];
  assert(ret != 0);
  return ret;
}

// start a spanning tree with a random root, all nodes must initiate this.
// if timeout == 0, never time out
void 
createSpanningTree(SpanningTree* spt, SpanningTreeHandler donefunc, int timeout)
{
  assert(spt->state == WAITING);

  setColor(WHITE);
  assert(trees[spt->spantreeid] == spt);
  // set the state to WAITING
  spt->state = WAITING;
  spt->kind = Root;
  //done function for the spanning tree
  spt->mydonefunc = donefunc;

  // pick a tree->value = rand()<<8|myid 
  // (unless debug mode, then it is just id)
  spt->value = (0 & rand()<<8)|getGUID();
  {
    char buffer[1024];
    tree2str(buffer, spt->spantreeid);
    blockprint(stderr, "Starting: %s\n", buffer);
  }
  startAskingNeighors(spt, 0);

#if 0
  // if timeout > 0, set a timeout
  if(timeout > 0) {
    spt->spantimeout.callback = (GenericHandler)(&spCreation);
    spt->spantimeout.arg = spId;
    spt->spantimeout.calltime = getTime() + timeout;
    registerTimeout(&(spt->spantimeout)); 
  }
#endif
       
  // now we wait til we reach the DONE state
  int counter = 0;
  while (spt->state != STABLE) {
    if (counter++ > 150000) {
      char buffer[256];
      blockprint(stderr, "creating Waiting: %s\n", tree2str(buffer, spt->spantreeid));
      counter = 0;
    }
  }
  blockprint(stderr, "ALL DONE - RETURNING\n");
}

byte isSpanningTreeRoot(SpanningTree* spt)
{
  return (spt->kind == Root);
}

// my and all my children have entered barrier
void
upBarrier(void)
{
  BarrierMsg* msg = (BarrierMsg*)thisChunk;
  SpanningTree* st = trees[msg->spid];
  if (msg->num == st->barrierNumber) {
    st->outstanding--;
    return;
  }
  // I haven't entered the barrier that my child has, record it
  assert(msg->num == (st->barrierNumber+1));
  st->waitingForBarrier++;
}

// my parent says everyone has entered barrier, I can leave it now
void
downBarrier(void)
{
  BarrierMsg* msg = (BarrierMsg*)thisChunk;
  SpanningTree* st = trees[msg->spid];
  st->outstanding++;
}


// wait til everyone gets to a barrier.  I.e., every node in spanning
// tree calls this function.  Will not return until done or timeout
// secs have elapsed.  If timeout is 0, never timeout.  return 1 if
// timedout, 0 if ok.
int 
treeBarrier(SpanningTree* spt, int timeout)
{
  spt->barrierNumber++;		 /* indicate we are entering a new barrier */
  spt->outstanding = spt->numchildren; /* how many children I am waiting for */
  BarrierMsg msg;
  msg.spid = spt->spantreeid;
  msg.num = spt->barrierNumber;

  blockprint(stderr, "StartBarrier:%d out:%d wait:%d\n", spt->barrierNumber, spt->outstanding, spt->waitingForBarrier);
  // check to see if any children got here before me
  spt->outstanding -= spt->waitingForBarrier;
  spt->waitingForBarrier = 0;

  // now we wait for all our children to report they have entered barrier
  while (spt->outstanding > 0) delayMS(100);

  blockprint(stderr, "ChildBarrier:%d out:%d wait:%d\n", spt->barrierNumber, spt->outstanding, spt->waitingForBarrier);

  // at this point all of my children have entered barrier.  Tell my
  // parent and then wait for him to report back to me that I can
  // continue
  if (spt->kind != Root) {
    sendMySpChunk(spt->myParent, (byte*)&msg, sizeof(BarrierMsg), (MsgHandler)&upBarrier);
    while (spt->outstanding == 0) delayMS(100);
  }

  blockprint(stderr, "ParntBarrier:%d out:%d wait:%d\n", spt->barrierNumber, spt->outstanding, spt->waitingForBarrier);

  // now tell all my children that they are done
  int i;
  for (i=0; i<NUM_PORTS; i++) {
    if (spt->neighbors[i] != Child) continue;
    sendMySpChunk(i, (byte*)&msg, sizeof(BarrierMsg), (MsgHandler)&downBarrier);
  }

  blockprint(stderr, "Done-Barrier:%d out:%d wait:%d\n", spt->barrierNumber, spt->outstanding, spt->waitingForBarrier);

  return 0;
}
 
////////////////////////////////////////////////////////////////
// test program
////////////////////////////////////////////////////////////////

void handler(void);

void donefunc(SpanningTree* spt, SpanTreeState status)
{ 
   
  blockprint(stderr, "DONEFUNC: %d %d\n", spt->spantreeid, status);

  if(status == STABLE)    {
    if (isSpanningTreeRoot(spt) == 1){
      setColor(YELLOW);
    }else{
      setColor(WHITE);
      if(spt->numchildren == 0){
	setColor(PINK);
      }
    }
  }else{ 
    setColor(RED);
  }

}

void 
myMain(void)
{
  volatile byte spFinished;
  SpanningTree* tree;
  int baseid;

  delayMS(1000);
  // setSpanningTreeDebug(1);
  blockprint(stderr, "init\n");
  baseid = initSpanningTrees(1);
  tree = getTree(baseid);
  spFinished = 0;
  createSpanningTree(tree, donefunc, 0);
  blockprint(stderr, "return\n");
  
  int xx = 5;
  while( xx-- > 0){ //wait for the tree to be created  and updated the tree every time
    char buffer[512];
    setColor(AQUA);
    blockprint(stderr, "%d:%d: IN MAIN: %s\n", xx, blockTickRunning, tree2str(buffer, baseid));
    delayMS(200);
  }
  blockprint(stderr, "bye bye\n");
  while(1){ //wait for the tree to be created  and updated the tree every time
    char buffer[512];
    setColor(AQUA);
    blockprint(stderr, "%d: main waiting: %s\n", blockTickRunning, tree2str(buffer, baseid));
    delayMS(500);
  }
  return;


  //byte data[1];  
  // data[0] = 2;
  
  while( spFinished != 1){ //wait for the tree to be created  and updated the tree every time
    char buffer[512];
    setColor(AQUA);
    blockprint(stderr, "%d: spFinish waiting: %s\n", blockTickRunning, tree2str(buffer, baseid));
    delayMS(100);
  }
  blockprint(stderr, "finished\n");  
  //treeBroadcast(tree,data, 1, handler );
  if ( treeBarrier(tree,5000) == 1 )
    {
      setColor(GREEN);
    }  
  else
    {
      setColor(INDIGO);
    }
  pauseForever();
  while(1);
}


void handler(void)
{
  setColor(BLUE);
}

void 
userRegistration(void)
{
  registerHandler(SYSTEM_MAIN, (GenericHandler)&myMain);
}

// Local Variables:
// mode: c
// tab-width: 8
// indent-tabs-mode: nil
// c-basic-offset: 2
// End:
