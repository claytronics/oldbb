#include "block.bbh"
#include "block_config.bbh"
#include "memory.bbh"
#include "audio.bbh"
#include <stdlib.h>
#include "../sim/sim.h"

threadtype typedef enum { STABLE, WAITING } SpanTreeState;
threadtype typedef enum { Root, Interior, Leaf } SpanTreeKind;
threadtype typedef enum { Vacant, Parent, Child, NoLink, Unknown, Slow } SpanTreeNeighborKind;

threadtype typedef struct _spanningtree SpanningTree;

struct _spanningtree{
  PRef myParent; 		// port for my parent if there is one, else 255 == I am root
  byte numchildren;		/* number of children I have */
  SpanTreeNeighborKind neighbors[NUM_PORTS];	// what kind of node is my neighbor on each port 
  byte spantreeid;		// the id of this spanning tree
  SpanTreeState state;		// state i am in in forming the spanning tree
  SpanTreeKind kind;		// kind of node I am
  uint16_t value; 		// used in the creation of spanning trees, the name of the root
  int outstanding;		// used to count messages when we are in Waiting state
  SpanningTreeHandler mydonefunc;	// handler to call when we reach first stable state
  MsgHandler broadcasthandler;
  byte lastNeighborCount	 // last time I checked, how many neighbors I had
}; 

typedef struct _basicMsg {
  byte spid;			 /* tree it */
  char value[2];		 /* value of sender */
} BasicMsg;

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
void startAskingNeighors(SpanningTree* st);
void resetNeighbors(SpanningTree* st);

//////////////////////////////////////////////////////////////////
// Handlers
//////////////////////////////////////////////////////////////////

// timeout called to handle slow blocks

threadvar Timeout nryTimeout;
threadvar byte haveTimeout = 0;

void
retrySlowBlocks(void)
{
  // indicate timeout is inactive now (in case we get a notReadyYet
  // msg from someone while we are in this function
  haveTimeout = 0;

  blockprint(stderr, "In Not Ready Timeout\n");

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
        spt->myChildren[i] = Unknown; /* indicate no longer on delay list */
        // now, resend msg to create tree
	BasicMsg msg;
	msg.spid = id;
	GUIDIntoChar(spt->value, &(msg.value));
	assert(spt->value == oldvalue);
        sendMySpChunk(i, &msg, 3, (MsgHandler)&beMyChild); 
      }
    }
  }
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
  senderPort = faceNum(thisChunk); /* face we got sender's msg from */
  assert(st->outstanding > 0);
  st->outstanding--;
  st->neighbors[senderPort] = Child;
  st->numchildren++;
  if (st->kind == LEAF) st->kind = INTERIOR;
  checkStatus(st);
}

// sent from a neighbor already in this tree, who won't be my child
void
alreadyInYourTree(void)
{
  BasicMsg* msg = (BasicMsg*)thisChunk;
  SpanningTree* st = trees[msg->spid];
  senderPort = faceNum(thisChunk); /* face we got sender's msg from */
  assert(st->outstanding > 0);
  st->outstanding--;
  st->neighbors[senderPort] = NoLink;
  checkStatus(st);
}

// sent from a neighbor that is in a better tree already
// TODO: treat this as a child request
void
sorry(void)
{
  BasicMsg* msg = (BasicMsg*)thisChunk;
  SpanningTree* st = trees[msg->spid];
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
  senderPort = faceNum(thisChunk); /* face we got sender's msg from */
  if (st == NULL) {
    // we haven't started creating a tree here yet, so tell sender to
    // try again soon
    sendMySpChunk(senderPort, 
                  thisChunk->data, 
                  1, 
                  (MsgHandler)&notReadyYet);
    return;
  } 
  // lets see what we should do
  uint16_t senderValue = charToGUID(&(msg->value));
  if (senderValue > st->value) {
    // I will become sender's child
    st->value = senderValue;
    resetNeighbors(st);
    st->myParent = senderPort;
    st->neighbors[senderPort] = Parent;
    sendMySpChunk(senderPort, 
                  thisChunk->data, 
                  1, 
                  (MsgHandler)&youAreMyParent);
    // set kind of node
    st->kind = LEAF; 
    // now talk to all other neighbors and ask them to be my children
    startAskingNeighors(st);
    return;
  } else if (senderValue == st->value) {
    // I am already in a tree with this value
    st->neighbors[senderPort] = NoLink;
    sendMySpChunk(senderPort, 
                  thisChunk->data, 
                  1, 
                  (MsgHandler)&alreadyInYourTree);
    return;
  } else if (senderValue < st->value) {
    // I am in a better tree, tell sender no luck
    sendMySpChunk(senderPort, 
                  thisChunk->data, 
                  1, 
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

// called when a neighbor indicates they are in my tree, either a child, or some other node
void
checkStatus(SpanningTree* st)
{
  if (st->outstanding == 0) st->state = STABLE;
  blockprint(stderr, "Status: %s\n", state2str(st->state));
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
startAskingNeighors(SpanningTree* st)
{
  BasicMsg msg;
  msg.spid = st->spid;
  GUIDIntoChar(st->value, &(msg.value));
  uint16_t oldvalue = st->value;

  for (int i=0; i<NUM_PORTS; i++) {
    if (st->neighbors[i] == Unknown) {
      st->outstanding++;
      assert(oldvalue == st->value);
      sendMySpChunk(i, 
		    &msg, 
		    1, 
		    (MsgHandler)&beMyChild);
      
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
          state2str(st->state), st->status, st->numchildren, kind2str(st->kind));
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

// allocate a set of <num> spanning trees.  If num is 0, use system default.
// returns the base of num spanning tree to be used ids.
int 
initSpanningTrees(int num)
{
  if((maxSpanId + num) < MAX_SPANTREE_ID) {
    int i; 
    for( i = 0 ; i<num; i++) {
      SpanningTree* spt = (SpanningTree*)malloc(sizeof(SpanningTree));
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
  spt->kind = ROOT;
  //done function for the spanning tree
  spt->mydonefunc = donefunc;

  // pick a tree->value = rand()<<8|myid 
  // (unless debug mode, then it is just id)
  spt->value = (0 & rand()<<8)|getGUID();

  startAskingNeighors(spt);

  // if timeout > 0, set a timeout
  if(timeout > 0) {
    spt->spantimeout.callback = (GenericHandler)(&spCreation);
    spt->spantimeout.arg = spId;
    spt->spantimeout.calltime = getTime() + timeout;
    registerTimeout(&(spt->spantimeout)); 
  }
       
  // now we wait til we reach the DONE state
  int counter = 0;
  while (trees[spId]->state != STABLE) {
    if (counter++ > 150000) {
      char buffer[256];
      blockprint(stderr, "Waiting: %s\n", tree2str(buffer, spId));
      counter = 0;
    }
  }
  blockprint(stderr, "ALL DONE - RETURNING\n");
}

////////////////////////////////////////////////////////////////
// test program
////////////////////////////////////////////////////////////////

void handler(void);
void donefunc(SpanningTree* spt, SpanningTreeStatus status);



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
  blockprint(stderr, "get\n");
  tree = getTree(baseid);
  spFinished = 0;
  blockprint(stderr, "create\n");
  createSpanningTree(tree, donefunc, 0);
  blockprint(stderr, "return\n");

  //byte data[1];  
  // data[0] = 2;
  
  while( spFinished != 1){ //wait for the tree to be created  and updated the tree every time
    char buffer[512];
    setColor(AQUA);
    blockprint(stderr, "%d: waiting: %s\n", blockTickRunning, tree2str(buffer, baseid));
    delayMS(100);
  }
  blockprint(stderr, "finished\n");  
  //treeBroadcast(tree,data, 1, handler );
  if ( treeBarrier(tree,1,5000) == 1 )
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



void donefunc(SpanningTree* spt, SpanningTreeStatus status)
{ 
   
  blockprint(stderr, "DONEFUNC: %d %d\n", spt->spantreeid, status);

  if(status == COMPLETED)
  {
   if (isSpanningTreeRoot(spt) == 1)
  {
    setColor(YELLOW);
  }
  else
  {
    setColor(WHITE);
    if(spt->numchildren == 0)
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
