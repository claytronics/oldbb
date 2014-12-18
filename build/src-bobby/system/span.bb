#include "span.bbh"
#include "block.bbh"

# include "../sim/sim.h"



// set by setSpanningTreeDebug
threadvar int debugmode = 0;

// list of ptrs to spanning tree structures
threadvar SpanningTree* trees[MAX_SPANTREE_ID];

// number of already allocated spanning tree structures.
threadvar int maxSpanId = 0;


threadvar Timeout nryTimeout;
threadvar byte haveTimeout = 0;



// Message Handlers
void beMyChild(void);
void notReadyYet(void);
void youAreMyParent(void);
void alreadyInYourTree(void);
void sorry(void);
void areWeInSameTree(void);
void treeStable(void);
void childrenInSameTree(void);
void yesWeAreInSameTree(void);


// General routines
void startAskingNeighors(SpanningTree* st, byte gen);
void resetNeighbors(SpanningTree* st);
byte sendMySpChunk(byte myport, byte *data, byte size, MsgHandler mh);
char* tree2str(char* buffer, byte id);
void checkStatus(SpanningTree* st);


#if 0
void
retrySlowBlocks(void)
{
	//printDebug(__FUNCTION__);
	fprintf(stdout,"%d: %s\n",getGUID(),__FUNCTION__);
  // indicate timeout is inactive now (in case we get a notReadyYet
  // msg from someone while we are in this function
  haveTimeout = 0;

  DEBUGPRINT(1, "In Not Ready Timeout\n");
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
        msg.flag = spt->startedByRoot;
	GUIDIntoChar(spt->value, (byte*)&(msg.value));
	assert(spt->value == oldvalue);
        sendMySpChunk(i, (byte*)&msg, sizeof(BasicMsg), (MsgHandler)&beMyChild); 
        DEBUGPRINT(1, "NRT -> port %d\n", i);
	rsbCounter++;
      }
    }
  }
  DEBUGPRINT(1, "NRT sent %d msgs\n", rsbCounter);
}
#endif



void
retrySlowBlocks(void)
{
	//printDebug(__FUNCTION__);
	fprintf(stdout,"%d: %s\n",getGUID(),__FUNCTION__);
  // indicate timeout is inactive now (in case we get a notReadyYet
  // msg from someone while we are in this function
  haveTimeout = 0;

  DEBUGPRINT(1, "In Not Ready Timeout\n");
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
        msg.flag = spt->startedByRoot;
	GUIDIntoChar(spt->value, (byte*)&(msg.value));
	assert(spt->value == oldvalue);
        sendMySpChunk(i, (byte*)&msg, sizeof(BasicMsg), (MsgHandler)&beMyChild); 
        DEBUGPRINT(1, "NRT -> port %d\n", i);
	rsbCounter++;
      }
    }
  }
  DEBUGPRINT(1, "NRT sent %d msgs\n", rsbCounter);
}





// sent from a neighbor that hasn't set up his spanningtree structure
// yet.  Try again soon.
void
notReadyYet(void)
{
	//printDebug(__FUNCTION__);
	fprintf(stdout,"%d: %s\n",getGUID(),__FUNCTION__);
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
	//printDebug(__FUNCTION__);
	fprintf(stdout,"%d: %s\n",getGUID(),__FUNCTION__);
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
  setColor(PURPLE);
  printDebug("1");
  checkStatus(st);
}

// sent from a neighbor already in this tree, who won't be my child
void
alreadyInYourTree(void)
{
	//printDebug(__FUNCTION__);
	fprintf(stdout,"%d: %s\n",getGUID(),__FUNCTION__);
  BasicMsg* msg = (BasicMsg*)thisChunk;
  SpanningTree* st = trees[msg->spid];
  PRef senderPort = faceNum(thisChunk); /* face we got sender's msg from */
  uint16_t senderValue = charToGUID((byte*)&(msg->value));
  if (senderValue != st->value) return; /* this is now old information */

  assert(st->outstanding > 0);
  st->outstanding--;
  st->neighbors[senderPort] = NoLink;

  //setColor(BROWN);
  printDebug("2");
  checkStatus(st);
}

// sent from a neighbor that is in a better tree already
void
sorry(void)
{
	//printDebug(__FUNCTION__);
	fprintf(stdout,"%d: %s\n",getGUID(),__FUNCTION__);
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
	//printDebug(__FUNCTION__);
	fprintf(stdout,"%d: %s\n",getGUID(),__FUNCTION__);
  BasicMsg* msg = (BasicMsg*)thisChunk;
  SpanningTree* st = trees[msg->spid];
  PRef senderPort = faceNum(thisChunk); /* face we got sender's msg from */
  if ((st == NULL)||(st->value == 0)) {
    // we haven't inited our tree yet.
    // tell sender to try again soon.
    sendMySpChunk(senderPort, 
                  thisChunk->data, 
                  sizeof(BasicMsg), 
                  (MsgHandler)&notReadyYet);
    return;
  } 
  // tree has been inited.  Check to see how this tree is being created.
  if (msg->flag && (st->state == INITED)) {
    // setup tree.  This creation request orginates from a single node
    st->state = WAITING;
    st->kind = Root;
    st->mydonefunc = 0;
    st->value = (0 & rand()<<8)|getGUID();
    st->startedByRoot = 1;
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

    setColor(RED);
  printDebug("3");
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





void 
yesWeAreInSameTree(void)
{
	//printDebug(__FUNCTION__);
	fprintf(stdout,"%d: %s\n",getGUID(),__FUNCTION__);
  BasicMsg* msg = (BasicMsg*)thisChunk;
  SpanningTree* st = trees[msg->spid];
  uint16_t senderValue = charToGUID((byte*)&(msg->value));
  if (senderValue == st->value) {
    assert(st->outstanding > 0);
    st->outstanding--;
  } 
}

void 
areWeInSameTree(void)
{
	//printDebug(__FUNCTION__);
	fprintf(stdout,"%d: %s\n",getGUID(),__FUNCTION__);
  BasicMsg* msg = (BasicMsg*)thisChunk;
  SpanningTree* st = trees[msg->spid];
  uint16_t senderValue = charToGUID((byte*)&(msg->value));
  PRef senderPort = faceNum(thisChunk); /* face we got sender's msg from */
  if (senderValue == st->value) {
    sendMySpChunk(senderPort, 
                  thisChunk->data, 
                  sizeof(BasicMsg), 
                  (MsgHandler)&yesWeAreInSameTree);
  } 
}

// my parent claims everyone agrees, we are done!
void treeStable(void)
{
	//printDebug(__FUNCTION__);
	fprintf(stdout,"%d: %s\n",getGUID(),__FUNCTION__);
  BasicMsg* msg = (BasicMsg*)thisChunk;
  SpanningTree* st = trees[msg->spid];
  uint16_t senderValue = charToGUID((byte*)&(msg->value));
  if (senderValue == st->value) {
    assert(st->outstanding == 0);
    st->outstanding++;
    return;
  }
  char buffer[512];
  blockprint(stderr, "got treestable, but my value changed!!: %s\n", tree2str(buffer, st->spantreeid));
}

// one of my children says its subtree is all in same tree
void childrenInSameTree(void)
{
	//printDebug(__FUNCTION__);
	fprintf(stdout,"%d: %s\n",getGUID(),__FUNCTION__);
  BasicMsg* msg = (BasicMsg*)thisChunk;
  SpanningTree* st = trees[msg->spid];
  uint16_t senderValue = charToGUID((byte*)&(msg->value));
  if (senderValue == st->value) {
    st->stableChildren++;
  }
}


////////////////////////////////////////////////////////////////

char* kind2str(SpanTreeKind x)
{
	//printDebug(__FUNCTION__);
	fprintf(stdout,"%d: %s\n",getGUID(),__FUNCTION__);
  switch (x) {
  case Root: return "Root";
  case Interior: return "Interior";
  case Leaf: return "Leaf";
  }
  return "???";
}

static char* state2str(SpanTreeState x)
{
	//printDebug(__FUNCTION__);
	fprintf(stdout,"%d: %s\n",getGUID(),__FUNCTION__);
  switch (x) {
  case INITED: return "INITED";
  case STABLE: return "STABLE";
  case MAYBESTABLE: return "MAYBE";
  case WAITING: return "WAITING";
  case CANCELED: return "CANCELED";
  }
  return "???";
}

static char* nstate2str(SpanTreeNeighborKind x)
{
	//printDebug(__FUNCTION__);
	fprintf(stdout,"%d: %s\n",getGUID(),__FUNCTION__);
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
	//printDebug(__FUNCTION__);
	fprintf(stdout,"%d: %s\n",getGUID(),__FUNCTION__);
  freeChunk(thisChunk);
  thisChunk->status = CHUNK_FREE;
}

byte sendMySpChunk(byte myport, byte *data, byte size, MsgHandler mh) 
{ 
	//printDebug(__FUNCTION__);
	fprintf(stdout,"%d: %s\n",getGUID(),__FUNCTION__);
  Chunk *c=getSystemTXChunk();
#ifdef BBSIM
  IFSIMDEBUG(1) {
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
    DEBUGPRINT(1, "== %d->%p [%s]   %s using %p\n", 
               myport, mh, buffer, tbuff, c);
  }
#endif
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
	//printDebug(__FUNCTION__);
	fprintf(stdout,"%d: %s\n",getGUID(),__FUNCTION__);
  if (st->outstanding == 0) {
    st->state = MAYBESTABLE;
    setColor(ORANGE);
    
  } else {
    st->state = WAITING;
    //printDebug("checkstatus");
    setColor(PURPLE);
  }
  char buffer[512];
  DEBUGPRINT(1, "Status: %s: %s\n", state2str(st->state), tree2str(buffer, st->spantreeid));
}

// called when we are starting to enter a new tree.  Reset everything
void
resetNeighbors(SpanningTree* st)
{
	//printDebug(__FUNCTION__);
	fprintf(stdout,"%d: %s\n",getGUID(),__FUNCTION__);
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
	//printDebug(__FUNCTION__);
	fprintf(stdout,"%d: %s\n",getGUID(),__FUNCTION__);
  BasicMsg msg;
  msg.spid = st->spantreeid;
  msg.flag = st->startedByRoot;
  GUIDIntoChar(st->value, (byte*)&(msg.value));
  uint16_t oldvalue = st->value;

  for (int i=0; i<NUM_PORTS; i++) {
    if (gen != st->bmcGeneration) {
      // another bemychild msg came in with a better value for a tree, so abort the rest of this one.
#ifdef BBSIM
	    
      IFSIMDEBUG(0) {
        char buffer[512];
        blockprint(stderr, "Aborting asking neighbors: %d->%d: %s\n", gen, st->bmcGeneration, tree2str(buffer, st->spantreeid));
      }
#endif
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
	//printDebug(__FUNCTION__);
	fprintf(stdout,"%d: %s\n",getGUID(),__FUNCTION__);
  SpanningTree* st = trees[id];
  if (st == NULL) {
    sprintf(buffer, "%d: TREE NOT ALLOCATED", id);
    return buffer;
  }
  sprintf(buffer, 
          "%d: val:%d outstndg:%d parent:%d <%s> status:%d numchdrn:%d(%d) %s bn:%d wfb:%d Ns:%d [", 
          id, st->value, st->outstanding, st->myParent, 
          state2str(st->state), st->state, st->numchildren, st->stableChildren, kind2str(st->kind),
	  st->barrierNumber, st->waitingForBarrier, st->lastNeighborCount);
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
	//printDebug(__FUNCTION__);
	fprintf(stdout,"%d: %s\n",getGUID(),__FUNCTION__);
  int i;
  for (i=0; i<MAX_SPANTREE_ID; i++) {
    trees[i] = NULL;
  }
}

threadvar GenericHandler oldNbrChgHander;

// called when there has been a change in who is my neighbor
// ONLY WORKS FOR ADDING NEIGHBORS!!
void
neighborsChanged(void)
{
	//printDebug(__FUNCTION__);
#if 0
	fprintf(stdout,"%d: %s\n",getGUID(),__FUNCTION__);
  int ns = getNeighborCount();
  int i; 
  for (i=0; i<MAX_SPANTREE_ID; i++) {
    SpanningTree* spt = trees[i];
    if (spt == NULL) continue;
    while (spt->lastNeighborCount != ns) {
      DEBUGPRINT(0, "new neighbors added!  was:%d, now: %d\n", spt->lastNeighborCount, ns);
      int i;
      for (i=0; i<NUM_PORTS; i++) {
        if (!isPortVacant(i) && (spt->neighbors[i] == VACANT)) 
          spt->neighbors[i] = Unknown;
      }
      spt->state = WAITING;
      startAskingNeighors(spt, spt->bmcGeneration);
      spt->lastNeighborCount = ns;
      ns = getNeighborCount();
    }
  }
#endif
  // call next in chain
  if (oldNbrChgHander) (*oldNbrChgHander)();
}

void 
initSpanNbrsHandler(void)
{
	//printDebug(__FUNCTION__);
	fprintf(stdout,"%d: %s\n",getGUID(),__FUNCTION__);
  oldNbrChgHander = unregisterHandler(EVENT_NEIGHBOR_CHANGE);
  registerHandler(EVENT_NEIGHBOR_CHANGE, (GenericHandler)neighborsChanged);
}


// allocate a set of <num> spanning trees.  If num is 0, use system default.
// returns the base of num spanning tree to be used ids.
int 
initSpanningTrees(int num)
{
	//printDebug(__FUNCTION__);
	fprintf(stdout,"%d: %s\n",getGUID(),__FUNCTION__);
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
	//printDebug(__FUNCTION__);
	fprintf(stdout,"%d: %s\n",getGUID(),__FUNCTION__);
  assert(id < MAX_SPANTREE_ID);
  SpanningTree* ret = trees[id];
  assert(ret != 0);
  return ret;
}

#if 0
// check to see if our neighborhood has changed.  At this point we only handle the addition of new neighbors.
bool
checkNeighborhood(SpanningTree* spt)
{
	//printDebug(__FUNCTION__);
	fprintf(stdout,"%d: %s\n",getGUID(),__FUNCTION__);
  int ns = getNeighborCount();
  if (spt->lastNeighborCount != ns) {
    while (spt->lastNeighborCount != ns) {
      DEBUGPRINT(0, "new neighbors added!  was:%d, now: %d\n", spt->lastNeighborCount, ns);
      int i;
      for (i=0; i<NUM_PORTS; i++) {
        if (!isPortVacant(i) && (spt->neighbors[i] == VACANT)) 
          spt->neighbors[i] = Unknown;
      }
      spt->state = WAITING;
      startAskingNeighors(spt, spt->bmcGeneration);
      spt->lastNeighborCount = ns;
      ns = getNeighborCount();
    }
    return true;
  }
  return false;
}
#endif

// start a spanning tree with a random root, all nodes must initiate this.
// if timeout == 0, never time out
// if howStart == 0 -> all participate, 1 -> only 1 node which will be root
// if howStart == 0 & makeMeRoot == 1, then gaurantee that this node is root.  Only 1 can have makeMeRoot
void 
createSpanningTree(SpanningTree* spt, SpanningTreeHandler donefunc, int timeout, byte howStart, byte makeMeRoot)
{
	//printDebug(__FUNCTION__);
	fprintf(stdout,"%d: %s\n",getGUID(),__FUNCTION__);
  assert(spt->state == WAITING);

  setColor(WHITE);
  assert(trees[spt->spantreeid] == spt);
  // set the state to WAITING
  spt->state = WAITING;
  spt->kind = Root;
  spt->startedByRoot = howStart;
  //done function for the spanning tree
  spt->mydonefunc = donefunc;

  // pick a tree->value = rand()<<8|myid 
  // (unless debug mode, then it is just id)
  spt->value = (0 & rand()<<7)|getGUID();
  if (makeMeRoot || howStart) spt->value |= 0x80;
  {
    char buffer[1024];
    tree2str(buffer, spt->spantreeid);
#ifdef BBSIM
    
    IFSIMDEBUG(1) blockprint(stderr, "Starting: %s\n", buffer);
#endif
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
       
  // now we wait til we reach the STABLE state
  while (spt->state != STABLE) {
    // check to see if our neighborhood has changed
    //checkNeighborhood(spt);

    int counter = 0;
    while (spt->state != MAYBESTABLE) {
      if (DEBUGSPAN) {
	if (counter++ > 30) {
	  char buffer[256];
	  blockprint(stderr, "creating Waiting: %s\n", tree2str(buffer, spt->spantreeid));
	  counter = 0;
	}
      }
      delayMS(100);
      //checkNeighborhood(spt);
    }
    // we might be stable, so check all neighbors first
    assert(spt->outstanding == 0);
    int oldvalue = spt->value;
    int i;
    BasicMsg msg;
    msg.spid = spt->spantreeid;
    GUIDIntoChar(spt->value, (byte*)&(msg.value));
    msg.flag = spt->startedByRoot;
    for (i=0; i<NUM_PORTS; i++) {
      if (spt->value != oldvalue) {
	// someone changed me, I wasn't stable
	char buffer[512];
	blockprint(stderr, "value changed from %d: %s\n", oldvalue, tree2str(buffer, spt->spantreeid));
	break;
      }
      if (spt->neighbors[i] != Vacant) {
	spt->outstanding++;
	sendMySpChunk(i, 
		      (byte*)&msg, 
		      sizeof(BasicMsg), 
		      (MsgHandler)&areWeInSameTree);
      }
    }
    if (spt->value != oldvalue) continue;
    // we might have had a neighbor change event
    if (spt->state == WAITING) continue;

    // same tree val and no neighbor change, so wait til all neighbors respond with insametree or not
    while ((spt->outstanding > 0) && (spt->state == MAYBESTABLE)) {
#ifdef BBSIM
	    
      IFSIMDEBUG(1) {
	char buffer[512];
	blockprint(stderr, "Waiting 1 on Neighborhood check: %s\n", tree2str(buffer, spt->spantreeid));
      }
#endif
      delayMS(50);
    }
    setColor(GREEN);
    if (oldvalue != spt->value) continue;
    // we might have had a neighbor change event
    if (spt->state == WAITING) continue;

    // all my neighbors agree on same value
    assert(spt->state == MAYBESTABLE);
    while ((oldvalue == spt->value) && (spt->stableChildren < spt->numchildren)) {
#ifdef BBSIM
	    
      IFSIMDEBUG(1) {
	char buffer[512];
	blockprint(stderr, "Waiting 2 on Children check: %s\n", tree2str(buffer, spt->spantreeid));
      }
#endif
      delayMS(50);
    }
    spt->stableChildren = 0;
    if (oldvalue != spt->value) continue;
    // we might have had a neighbor change event
    if (spt->state == WAITING) continue;

    // all my children also agree on same value
    assert(spt->state == MAYBESTABLE);
    assert(spt->outstanding == 0);
    if (spt->kind != Root) {
      sendMySpChunk(spt->myParent, 
		    (byte*)&msg, 
		    sizeof(BasicMsg), 
		    (MsgHandler)&childrenInSameTree);
    }
    setColor(ORANGE);
    // now wait on parent to confirm all subtrees report ok
    while ((oldvalue == spt->value) && (spt->state == MAYBESTABLE) && (spt->outstanding == 0)) {
#ifdef BBSIM
	    
      IFSIMDEBUG(1) {
	char buffer[512];
	blockprint(stderr, "Waiting 3 on Parent check: %s\n", tree2str(buffer, spt->spantreeid));
      }
#endif
      if (spt->kind == Root) break;
      delayMS(50);
    }
    if (oldvalue != spt->value) continue;
    if (spt->state != MAYBESTABLE) continue;
    assert((spt->outstanding == 1)||(spt->kind == Root));
    setColor(PINK);
    // either I am root, or my parent says, everyone agreed
    for (i=0; i<NUM_PORTS; i++) {
      if (spt->neighbors[i] == Child) {
	sendMySpChunk(i, 
		      (byte*)&msg, 
		      sizeof(BasicMsg), 
		      (MsgHandler)&treeStable);
      }
    }
    spt->state = STABLE;
  }
#ifdef BBSIM
  
  IFSIMDEBUG(1) {
    char buffer[512];
    blockprint(stderr, "ALL DONE - RETURNING: %s\n", tree2str(buffer, spt->spantreeid));
  }
#endif
}

byte isSpanningTreeRoot(SpanningTree* spt)
{
	//printDebug(__FUNCTION__);
	fprintf(stdout,"%d: %s\n",getGUID(),__FUNCTION__);
  return (spt->kind == Root);
}

// my and all my children have entered barrier
void
upBarrier(void)
{
	//printDebug(__FUNCTION__);
	fprintf(stdout,"%d: %s\n",getGUID(),__FUNCTION__);
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
	//printDebug(__FUNCTION__);
	fprintf(stdout,"%d: %s\n",getGUID(),__FUNCTION__);
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
	//printDebug(__FUNCTION__);
	fprintf(stdout,"%d: %s\n",getGUID(),__FUNCTION__);
  spt->barrierNumber++;		 /* indicate we are entering a new barrier */
  spt->outstanding = spt->numchildren; /* how many children I am waiting for */
  BarrierMsg msg;
  msg.spid = spt->spantreeid;
  msg.num = spt->barrierNumber;
#ifdef BBSIM

  IFSIMDEBUG(1) {
    char buffer[512];
    blockprint(stderr, "Starting Barrier: %s\n", tree2str(buffer, spt->spantreeid));
    blockprint(stderr, "StartBarrier:%d out:%d wait:%d\n", spt->barrierNumber, spt->outstanding, spt->waitingForBarrier);
  }
#endif
  // check to see if any children got here before me
  spt->outstanding -= spt->waitingForBarrier;
  spt->waitingForBarrier = 0;

  // now we wait for all our children to report they have entered barrier
  while (spt->outstanding > 0) delayMS(100);

  DEBUGPRINT(1, "ChildBarrier:%d out:%d wait:%d\n", spt->barrierNumber, spt->outstanding, spt->waitingForBarrier);

  // at this point all of my children have entered barrier.  Tell my
  // parent and then wait for him to report back to me that I can
  // continue
  if (spt->kind != Root) {
    sendMySpChunk(spt->myParent, (byte*)&msg, sizeof(BarrierMsg), (MsgHandler)&upBarrier);
    while (spt->outstanding == 0) delayMS(100);
  }

  DEBUGPRINT(1, "ParntBarrier:%d out:%d wait:%d\n", spt->barrierNumber, spt->outstanding, spt->waitingForBarrier);

  // now tell all my children that they are done
  int i;
  for (i=0; i<NUM_PORTS; i++) {
    if (spt->neighbors[i] != Child) continue;
    sendMySpChunk(i, (byte*)&msg, sizeof(BarrierMsg), (MsgHandler)&downBarrier);
  }

  DEBUGPRINT(1, "Done-Barrier:%d out:%d wait:%d\n", spt->barrierNumber, spt->outstanding, spt->waitingForBarrier);

  return 0;
}
 

// broadcast a message to all nodes in tree.
// can be called by any node.
// returns as soon as it sends its messages, i.e., no ack
void
treeBroadcast(SpanningTree* spt, byte* data, byte size, BroadcastHandler mh)
{
	//printDebug(__FUNCTION__);
	fprintf(stdout,"%d: %s\n",getGUID(),__FUNCTION__);
  BroadcastMsg msg;
  DEBUGPRINT(1, "max size of bcast is %d\n", BroadcastPayloadSize);
  assert(size <= BroadcastPayloadSize);
  memset(&msg, 0, sizeof(BroadcastMsg));
  msg.packet.header.spid = spt->spantreeid;
  msg.packet.header.handler = mh;
  msg.packet.header.len = size;
  memcpy(BroadcastDataOffset(&msg), data, size);
  if(mh == getCount){
	  printDebug("tt");
  }
  (*mh)(BroadcastDataOffset(&msg));
  finishTreeBroadcast(0, 0, &msg);
}

void
handleBroadcast(void)
{
	//printDebug(__FUNCTION__);
	fprintf(stdout,"%d: %s\n",getGUID(),__FUNCTION__);
  BroadcastMsg* msg = (BroadcastMsg*)thisChunk;
  PRef fromFace = faceNum(thisChunk); /* face we got sender's msg from */
  BroadcastHandler mh = msg->packet.header.handler;
  (*mh)(BroadcastDataOffset(msg));
  finishTreeBroadcast(1, fromFace, msg);
}

// called as the last step of any msg being broadcast through the spanning tree
void
finishTreeBroadcast(int revd, int fromFace, BroadcastMsg* msg)
{
	fprintf(stdout,"%d: %s\n",getGUID(),__FUNCTION__);
  SpanningTree* spt = trees[msg->packet.header.spid];
  assert(spt != NULL);
  int size = BroadcastHeaderSize+msg->packet.header.len;
  int i;
  for (i=0; i<NUM_PORTS; i++) {
    if (revd && (i == fromFace)) continue;
    if ((spt->neighbors[i] == Child)||(spt->neighbors[i] == Parent)) {
      sendMySpChunk(i, (byte*)msg, size, (MsgHandler)handleBroadcast); 
    }
  }
}

////////////////////////////////////////////////////////////////
// code for counting nodes in spanning tree
// code does NOT allow two spanning trees to execute this code at once
//


threadvar byte collectedCountChildren = 0;
threadvar byte collectedCount = 0;


void
upCount(void)
{
	printDebug(__FUNCTION__);
	fprintf(stdout,"%d: %s\n",getGUID(),__FUNCTION__);
  CountMsg* msg = (CountMsg*)thisChunk;
  collectedCountChildren++;
  collectedCount += msg->count;
  SpanningTree* spt = trees[msg->spid];
  if ((spt->kind != Root) && (collectedCountChildren == spt->numchildren)) sendUpMsg(spt, collectedCount+1);
}

void
sendUpMsg(SpanningTree* spt, int count)
{
	printDebug(__FUNCTION__);
	fprintf(stdout,"%d: %s\n",getGUID(),__FUNCTION__);
  CountMsg cm;
  cm.spid = spt->spantreeid;
  cm.count = count;
  sendMySpChunk(spt->myParent,  (byte*)&cm, sizeof(CountMsg), (MsgHandler)&upCount);
}


void
getCount(BroadcastMsg *msg)
{
	printDebug(__FUNCTION__);
	fprintf(stdout,"%d: %s\n",getGUID(),__FUNCTION__);
  setColor(PINK);
#if 0
  delayMS(2000);
#endif

  collectedCount = 0;
  collectedCountChildren = 0;
  //SpanningTree* spt = trees[msg->packet.header.spid];
  SpanningTree* spt = trees[0];
  printf("%d : msg->packet.header.spid = %d\n",getGUID(),msg->packet.header.spid);
  printf("spt %p\n",spt);
  if (spt->kind == Leaf) {
	  setColor(BROWN);
	  //delayMS(2000);
	  sendUpMsg(spt, 1);
	//printDebug("leaf");
  }
  else {
	setColor(BLUE);
	printDebug("not leaf");
  }
#if 0
  int cnt = 2;
  while(cnt){
	  setColor(RED);
	  delayMS(1000);
	  setColor(BLUE);
	  delayMS(1000);
	  cnt--;
  }
#endif
	  setColor(BLUE);
	  delayMS(1000);
}

// called by root to count nodes in tree
int
treeCount(SpanningTree* spt, int timeout)
{
#if 0
	printDebug(__FUNCTION__);
	printDebug("tc");
	fprintf(stdout,"%d: %s\n",getGUID(),__FUNCTION__);
  byte data[2];
  printf("tree count for tree = %d\n",spt->spantreeid);
  
  collectedCount = 0;
  collectedCountChildren = 0;
  treeBroadcast(spt, data, 1, (BroadcastHandler)getCount);
  //setColor(GREEN);
  while (collectedCountChildren != spt->numchildren){
	 /* if(collectedCountChildren == 0) { setColor(GREEN);}
	  else if(collectedCountChildren == 1) { setColor(PINK);}
	  else if(collectedCountChildren == 2) { setColor(BROWN);}
	  else if(collectedCountChildren == 3) { setColor(AQUA);}
	  else if(collectedCountChildren == 4) { setColor(YELLOW);}
	  else { setColor(YELLOW);}*/
	  delayMS(10);
  }
  setColor(BROWN);
  return collectedCount+1;
#endif
  return 2;
}


