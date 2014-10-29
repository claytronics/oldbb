#include "span.bbh"
#include "block.bbh"
#include "../sim/sim.h"
#include "myassert.h"



// set by setSpanningTreeDebug
threadvar int debugmode = 0;

// list of ptrs to spanning tree structures
threadvar SpanningTree* trees[MAX_SPANTREE_ID];

// number of already allocated spanning tree structures.
threadvar int maxSpanId = 0;




// to know if all get into a barrier 
threadvar int allHaveBarrier = 0;
threadvar int reachBarrier = 0;


//-----------------PRIVATE FUNCTIONS FOR SPANNING TREE -------------
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
    st->bmcGeneration++;     /* track that we just got a bemychild call that we are acting on */
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

void 
yesWeAreInSameTree(void)
{
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
  BasicMsg* msg = (BasicMsg*)thisChunk;
  SpanningTree* st = trees[msg->spid];
  uint16_t senderValue = charToGUID((byte*)&(msg->value));
  if (senderValue == st->value) {
    st->stableChildren++;
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
  case MAYBESTABLE: return "MAYBE";
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
  if (st->outstanding == 0) {
    st->state = MAYBESTABLE;
    setColor(YELLOW);

  }
  else {
    st->state = WAITING;
    setColor(RED);
  }
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
      if (DEBUGSPAN) blockprint(stderr, "Aborting asking neighbors: %d->%d: %s\n", gen, st->bmcGeneration, tree2str(buffer, st->spantreeid));
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
          "%d: val:%d outstndg:%d parent:%d <%s> status:%d numchdrn:%d(%d) %s bn:%d wfb:%d [", 
          id, st->value, st->outstanding, st->myParent, 
          state2str(st->state), st->state, st->numchildren, st->stableChildren, kind2str(st->kind),
    st->barrierNumber, st->waitingForBarrier);
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
    if (DEBUGSPAN) blockprint(stderr, "Starting: %s\n", buffer);
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
    }
    // we might be stable, so check all neighbors first
    assert(spt->outstanding == 0);
    int oldvalue = spt->value;
    int i;
    BasicMsg msg;
    msg.spid = spt->spantreeid;
    GUIDIntoChar(spt->value, (byte*)&(msg.value));
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
    while ((spt->outstanding > 0) && (spt->state == MAYBESTABLE)) {
      if (DEBUGSPAN) {
  char buffer[512];
  blockprint(stderr, "Waiting 1 on Neighborhood check: %s\n", tree2str(buffer, spt->spantreeid));
      }
      delayMS(50);
    }
    setColor(GREEN);
    if (oldvalue != spt->value) continue;
    // all my neighbors agree on same value
    assert(spt->state == MAYBESTABLE);
    while ((oldvalue == spt->value) && (spt->stableChildren < spt->numchildren)) {
      if (DEBUGSPAN) {
  char buffer[512];
  blockprint(stderr, "Waiting 2 on Children check: %s\n", tree2str(buffer, spt->spantreeid));
      }
      delayMS(50);
    }
    spt->stableChildren = 0;
    if (oldvalue != spt->value) continue;
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
    while ((spt->state == MAYBESTABLE) && (spt->outstanding == 0)) {
      if (spt->kind == Root) break;
      if (DEBUGSPAN) {
  char buffer[512];
  blockprint(stderr, "Waiting 3 on Parent check: %s\n", tree2str(buffer, spt->spantreeid));
      }
      delayMS(50);
    }
    if (spt->state != MAYBESTABLE) continue;
    setColor(PINK);
    // parent says, everyone agreed
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
  char buffer[512];
  blockprint(stderr, "ALL DONE - RETURNING: %s\n", tree2str(buffer, spt->spantreeid));
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
  spt->barrierNumber++;    /* indicate we are entering a new barrier */
  spt->outstanding = spt->numchildren; /* how many children I am waiting for */
  BarrierMsg msg;
  msg.spid = spt->spantreeid;
  msg.num = spt->barrierNumber;

  char buffer[512];
  blockprint(stderr, "Starting Barrier: %s\n", tree2str(buffer, spt->spantreeid));
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
 




//-----------------------------------------------------------------
//                  old code 
//-----------------------------------------------------------------
// ////////////////////////////////////////////////////////////////
// // private functions for spanning tree code, see below for public interface
// ////////////////////////////////////////////////////////////////

// static char* state2str(SpanTreeState x)
// {
//   switch (x) {
//   case FREE: return "FREE";
//   case STARTED: return "STARTED";
//   case DONE: return "DONE";
//   case HAVEPARENT: return "HAVEPARENT";
//   case WAITING: return "WAITING";
//   case FORMED: return "FORMED";
//   }
//   return "????";
// }

// threaddef #define MAX_SPANTREE_ID 16

// // set by setSpanningTreeDebug
// //static int debugmode = 0;

// // list of ptrs to spanning tree structures
// threadvar SpanningTree* trees[MAX_SPANTREE_ID];

// // number of already allocated spanning tree structures.
// threadvar int maxSpanId = 0;

// // to know if all get into a barrier 
// threadvar int allHaveBarrier = 0;
// threadvar int reachBarrier = 0;

// //variable for debugging
// //static int colorDebug = 0;
// //static int logDebug = 0;

// //private functions
// byte sendMySpChunk(byte myport, byte *data, byte size, MsgHandler mh);
// void screwyou(void);
// void iamyourchild(void);
// void spComplete( void);
// void treeBroadcastMsg(void);
// void treeBroadcastBackMsg(void);
// void cstHelper(void);
// byte countSPChildren(byte id);
// void sendToLeaf(void);
// void allHaveBarrierMsg(void);
// void finishingSpan(void) ;
// void barrierAck(void);

// //Timeout for the creation of the spanning tree
// threadvar Timeout barrierTimeout;
// threadvar Timeout finishTimeout;

// void
// initSpanningTreeInformation(void)
// {
//   int i;
//   for (i=0; i<MAX_SPANTREE_ID; i++) {
//     trees[i] = NULL;
//   }
// }

// char* 
// tree2str(char* buffer, byte id)
// {
//   SpanningTree* st = trees[id];
//   if (st == NULL) {
//     sprintf(buffer, "%d: TREE NOT ALLOCATED", id);
//     return buffer;
//   }
//   sprintf(buffer, 
//           "%d: val:%d outstndg:%d parent:%d <%s> status:%d numchdrn:%d [", 
//           id, st->value, st->outstanding, st->myParent, 
//           state2str(st->state), st->status, st->numchildren);
//   char* bp = buffer + strlen(buffer);
//   int i;
//   for (i=0; i<NUM_PORTS; i++) {
//     sprintf(bp, " %d", st->myChildren[i]);
//     bp += strlen(bp);
//   }
//   strcat(bp, " ]");
//   return buffer;
// }

// threadvar Timeout nryTimeout;
// threadvar byte haveTimeout = 0;

// void
// retrySlowBlocks(void)
// {
//   // indicate timeout is inactive now (in case we get a notReadyYet
//   // msg from someone while we are in this function
//   haveTimeout = 0;

//   blockprint(stderr, "In Not Ready Timeout\n");

//   // now search all trees for children that might not have been ready before
//   int id;
//   for (id=0; id<MAX_SPANTREE_ID; id++) {
//     SpanningTree* spt = trees[id];
//     if (spt == NULL) continue;
//     if ((spt->state == DONE)||(spt->state == FORMED)) continue;
//     // we might have children that weren't ready last time we checked
//     int i;
//     for (i=0; i<NUM_PORTS; i++) {
//       if (spt->myChildren[i] == 2) {
//         // this was a slow child.  First unmark as slow
//         spt->myChildren[i] = 0; /* indicate no longer on delay list */
//         // now, resend msg to create tree
//         byte data[3];
//         data[0] = id;
//         GUIDIntoChar(spt->value, &(data[1]));
//         sendMySpChunk(i, data, 3, (MsgHandler)&cstHelper); 
//       }
//     }
//   }
// }

// // this is a response to a cstHelper that says that block wasn't ready
// // to start constructing tree yet.  Try again.
// void
// notReadyYet(void)
// {
//   byte id = thisChunk->data[0];
//   blockprint(stderr, "%d says not ready for tree %d\n", 
//              faceNum(thisChunk), id);
//   // say which face needs a retry
//   trees[id]->myChildren[faceNum(thisChunk)] = 2;
//   // if we don't have a timeout yet, register it
//   if (haveTimeout) return;
//   haveTimeout = 1;
//   nryTimeout.callback = (GenericHandler)(&retrySlowBlocks);
//   nryTimeout.calltime = getTime()+100;
//   registerTimeout(&nryTimeout);
// }

// // message handler for messages sent by a potential myParent 
// // trying to make me a child
// // 0: id
// // 1-2: value
// void 
// cstHelper(void)
// {
//   byte potentialID = thisChunk->data[0];
//   uint16_t potentialValue = charToGUID(&(thisChunk->data[1]));
//   blockprint(stderr, "CST: %d %d\n", potentialID, potentialValue);

//   assert(potentialID <= MAX_SPANTREE_ID);
//   SpanningTree* st = trees[potentialID];

//   if (st == NULL) {
//     // we haven't started creating a tree here yet, so tell sender to
//     // try again soon
//     sendMySpChunk(faceNum(thisChunk), 
//                   thisChunk->data, 
//                   1, 
//                   (MsgHandler)&notReadyYet);
//     return;
//   }

//   char buffer[256];
//   blockprint(stderr, "CST %s\n", tree2str(buffer, potentialID));

//   // if we already have a value that is more than potentialValue, then
//   // send back a NACK (screwyou) with our current value.
//   if( st->value >=  potentialValue )    {
//     byte data[5]; 
//     data[0] = st->spantreeid;
//     GUIDIntoChar(potentialValue, &(data[1]));
//     GUIDIntoChar(st->value, &(data[3]));
//     sendMySpChunk(faceNum(thisChunk), data, 5, (MsgHandler)&screwyou); 
//   } else {
//     // otherwise recursively start process for all my other ports
//     // (meaning, send a cstHelper msg) when recusrive procedure is
//     // done, make sender my myParent and send back an ACK
//     // (iamyourchild)
//     st->outstanding = 0;
//     st->value = potentialValue;
//     st->myParent = faceNum(thisChunk);
//     st->state = HAVEPARENT;
//     byte i;
//     for( i = 0; i<NUM_PORTS; i++) {
//       st->myChildren[i] = 0;
//     }
//     st->numchildren = 0;
//     st->state = WAITING;
//     byte data[3];
//     data[0] = potentialID;
//     GUIDIntoChar(st->value, &(data[1]));
//     /* Send add yourself to all neighbors */
//     byte p;
//     for( p = 0 ; p < NUM_PORTS; p++) {
//       if ((thisNeighborhood.n[p] != VACANT) 
//           && (p != st->myParent)) {	
//         st->outstanding++;
//         sendMySpChunk(p, data,3, (MsgHandler)&cstHelper); 
//       }
//     }
//     /* Send iamyourchild to parent */
//     sendMySpChunk((st->myParent), data, 3, (MsgHandler)&iamyourchild); 

//     // check to see if we are a leaf in a potentially completed tree
//     if (st->outstanding == 0) {
//       // if outstanding == 0, then we don't have any neighbors but the parent.
//       st->state = FORMED;
//     }
//   }
//   blockprint(stderr, "After cstHelper: %s\n", tree2str(buffer, potentialID));
// }

// void
// finishCreation(SpanningTree* st)
// {
//   st->state = DONE;
//   st->status = COMPLETED;
//   deregisterTimeout(&st->spantimeout);
//   st->mydonefunc(st,COMPLETED);
// }


// //message back to the root to complete the tree 
// void 
// spComplete(void)
// {
//   byte potentialID = thisChunk->data[0];
//   uint16_t potentialValue = charToGUID(&(thisChunk->data[1]));
//   SpanningTree* st = trees[potentialID];

//   // if the value potentialValue is different from the tree value just ignore,
//   if( st->value != potentialValue ) {
//     blockprint(stderr, "%d -> spcomplete with %d, but I have %d\n", 
//                faceNum(thisChunk), potentialValue, st->value);
//     return;
//   }

//   // if my state is not FORMED, then error
//   assert(st->state == FORMED);

//   // we should have outstanding > 0
//   assert(st->outstanding > 0);
//   st->outstanding--;

//   // when received all the messages from the children send spComplete
//   // to myParent if root send message to leaves
//   if( st->outstanding == 0 ) {
    
//     byte data[3];
//     data[0] = st->spantreeid;
//     GUIDIntoChar(st->value, &(data[1]));
//     if(!isSpanningTreeRoot(st) == 0) {
//       sendMySpChunk( st->myParent, data, 3, (MsgHandler)&spComplete);   
//     } else {
//       /* IS ROOT */
//       // if the root receive the spComplete, send message 
//       // to the leaves to tell them to execute their done function
//       byte i;
//       for(i = 0; i<NUM_PORTS; i++){
//         if(st->myChildren[i] == 1) {
//           sendMySpChunk(i , data, 3, (MsgHandler)&sendToLeaf);   
//         }
//       }
//       // we are done!
//       finishCreation(st);
//     }
//   }
// }

// //send message to the leaves to launch their mydonefunc
// void 
// sendToLeaf(void)
// {
//   byte potentialID = thisChunk->data[0];
//   uint16_t potentialValue = charToGUID(&(thisChunk->data[1]));
//   SpanningTree* st = trees[potentialID];

//   byte data[3];
//   data[0] = st->spantreeid;
//   GUIDIntoChar(st->value, &(data[1]));
  
//   // if the value potentialValue is different from the tree value just ignore,
//   if( st->value != potentialValue ) {
//     blockprint(stderr, "%d -> sendToLeaf with %d, but I have %d\n", 
//                faceNum(thisChunk), potentialValue, st->value);
//     return;
//   }

//   // we are in final tree, so send to children that we are done

//   // if my state is not FORMED, then error
//   assert(st->state == FORMED);
//   // we should also have no outstanding msgs
//   assert(st->outstanding == 0);

//   byte i;
//   for ( i = 0; i<NUM_PORTS; i++) {
//     if (st->myChildren[i] == 1) {
//       sendMySpChunk(i , data, 3, (MsgHandler)&sendToLeaf);   
//     }
//   }
//   // now we are DONE
//   finishCreation(st);
// }

// // see whether we should initiate msgs to the route to indicate we are done.
// void
// adjustChildren(SpanningTree* st)
// {
//   st->numchildren = countSPChildren(st->spantreeid);
//   if (st->outstanding == 0) {
//     st->state = FORMED;
//     st->outstanding = st->numchildren;
//     // init spComplete message chain up tree if we are a leaf
//     if( st->numchildren == 0) {
//       byte data[3];
//       data[0] = st->spantreeid;
//       GUIDIntoChar(st->value, &(data[1]));
//       if (!isSpanningTreeRoot(st)) {
//         sendMySpChunk(st->myParent, data, 3, (MsgHandler)&spComplete);   
//       } else {
//         // this is a single block ensemble??
//         assert(getNeighborCount() == 0);
//         finishCreation(st);
//       }
//     }
//   }
// }

// // a message from neighbor when it has been late to the game asking
// // for a cstHelper msg
// // data[0] = tree id
// void
// pleaseTryMe(void)
// {
//   byte potentialID = thisChunk->data[0];
//   SpanningTree* st = trees[potentialID];
// }

// // a message receives when a neighbor block doesn't want me to enter a tree.
// // data[0] = tree id
// // data[1-2] = value I sent
// // data[3-4] = value of neighbor
// void screwyou(void)
// {
//   byte potentialID = thisChunk->data[0];
//   uint16_t potentialValue = charToGUID(&(thisChunk->data[1]));
//   SpanningTree* st = trees[potentialID];
//   blockprint(stderr, "face %d screws me for tree %d with value %d\n", 
//              faceNum(thisChunk), potentialID, potentialValue);

//   if( potentialValue == st->value ) {
//     // I got screwed with same value I sent, so this msg is important.
//     uint16_t neighborValue = charToGUID(&(thisChunk->data[2]));
//     if (neighborValue == potentialValue) {
//       // we both have same value, so I am already in the same tree
//       st->myChildren[faceNum(thisChunk)] = 0;
//       st->outstanding--;
//       adjustChildren(st);
//     } else if (neighborValue > potentialValue) {
//       // neighborValue has higher value, lets join it
//       st->myChildren[faceNum(thisChunk)] = 0;
//       st->outstanding--;
//       // don't call adjust child, don't want to change my state
//       byte data[1];
//       data[0] = 0;//spId;
//       sendMySpChunk(faceNum(thisChunk), data, 1, (MsgHandler)&pleaseTryMe); 
//     }
//   }
//   char buffer[512];
//   blockprint(stderr, "After screwing: %s\n", tree2str(buffer, potentialID));
// }

// // Received from a block that has entered my tree as a child.  
// void iamyourchild(void)
// {
//   byte potentialID = thisChunk->data[0];
//   uint16_t potentialValue = charToGUID(&(thisChunk->data[1]));
//   SpanningTree* st = trees[potentialID];
//   if( potentialValue == st->value ){
//     st->myChildren[faceNum(thisChunk)] = 1;
//     st->outstanding--;
//     adjustChildren(st);
//   }
//   char buffer[512];
//   blockprint(stderr, "After getting child on %d: %s\n", 
//              faceNum(thisChunk), tree2str(buffer, potentialID));
// }

// byte countSPChildren(byte spId)
// {
//   byte count = 0;
//   byte i;
//   for(i = 0 ; i<NUM_PORTS; i++)  {
//     if(trees[spId]->myChildren[i] == 1) {
//       count++;
//     }
//   }	
//   return count;
// }

// byte sendMySpChunk(byte myport, byte *data, byte size, MsgHandler mh) 
// { 
//   Chunk *c=getSystemTXChunk();
//   char buffer[128];
  
//   buffer[0] = 0;
//   int i;
//   for (i=0; i<size; i++) {
//     char mbuf[10];
//     sprintf(mbuf, "%2x ", data[i]);
//     strcat(buffer, mbuf);
//   }
//   char tbuff[256];
//   tree2str(tbuff, data[0]);
//   blockprint(stderr, "== %d->%p [%s]   %s using %p\n", 
//              myport, mh, buffer, tbuff, c);
//   if (sendMessageToPort(c, myport, data, size, mh, NULL) == 0) {
//     freeChunk(c);
//     blockprint(stderr, "FAILED TO SEND\n");
//     return 0;
//   } else {
//     return 1;
//   }
// }

// // called when spanning tree times out 
// void spCreation(void)
// {
//   blockprint(stderr, "Spanning tree timed out!!\n");
//   /* byte d = trees[id]->spantimeout.arg; */
//   /* trees[targetid]->mydonefunc(trees[id],TIMEDOUT); */
//   /* trees[id]->status = TIMEDOUT; */
// }

// //message send to myParent if only all the children get into a barrier 
// void 
// barrierMsg(void) 
// {
//   byte  spID = thisChunk->data[0];
//  check:
//   if( allHaveBarrier == 1 ){
//     trees[spID]->outstanding--; 
//     byte buf[1];
//     buf[0] = spID;
//     if( trees[spID]->outstanding == 0)
//       {
 
//         if (isSpanningTreeRoot(trees[spID]) == 1){
//           //if root received all messages from its children, it will send
//           //messages to all the tree to tell that all the block get to a
//           //barrier
//           trees[spID]->status = COMPLETED;  // The status is changed to
//           // break the while loop inside
//           // the treeBarrier function so
//           // that the root is able to
//           // send allHaveBarrier
//           // children to the whole
//           // ensemble
//         }
//         else{
//           // send message to myParent to make their status COMPLETED
//           sendMySpChunk(trees[spID]->myParent, 
//                         buf, 
//                         1, 
//                         (MsgHandler)&barrierMsg); 
//         }
//       }
//   } else {
//     goto check;
//   }
// }

// //timeout for checking if all the blocks get into a barrier
// void 
// checkBarrier(void)
// {
//   /* byte id = barrierTimeout.arg; */
//   /* trees[id]->status = TIMEDOUT; */
// }

// //message sent from the root to the leaves to say that all the blocks
// //get into a barrier
// void 
// allHaveBarrierMsg(void)
// {
  
//   byte  spID = thisChunk->data[0];
//   byte buf[1];
//   buf[0] = spID;
//   byte p ;
//   if( reachBarrier == 0){
//     for( p = 0 ; p < NUM_PORTS ; p++){ 
//       //send the message allHaveBarrier to all the neighbor except the
//       //sender of this message
//       if (thisNeighborhood.n[p] != VACANT && p != faceNum(thisChunk)) {	
//         //this message will change the status of the tree into
//         //completed and break their while loop inside the treeBarrier
//         //function
//         sendMySpChunk(p, buf, 1, (MsgHandler)&allHaveBarrierMsg);   
//       }
//     }
//     reachBarrier = 1;
//   }
//   trees[spID]->status = COMPLETED;
  
// }

// //handler for sending the data to all the tree
// void 
// treeBroadcastMsg(void)
// {
//   byte  spID = thisChunk->data[0];
//   byte  size = thisChunk->data[1];
//   byte buf[size + 2];
//   buf[0] = spID;
//   buf[1] = size;
//   //the data will start from buf[2] if users want to use it
//   memcpy(buf+2, thisChunk->data+2, size*sizeof(byte));
//   byte p; 
//   for( p = 0; p < NUM_PORTS;p++){ //send data to all the children
//     if (trees[spID]->myChildren[p] == 1) {	
//       sendMySpChunk(p, buf, size + 2, (MsgHandler)&treeBroadcastMsg); 
//     }
//   }
//   if( trees[spID]->numchildren == 0 ){
//     byte data[1];
//     data[0] = spID;
//     sendMySpChunk(trees[spID]->myParent, 
//                   data, 
//                   1, 
//                   (MsgHandler)&treeBroadcastBackMsg); 
//     trees[spID]->broadcasthandler();
//   } 
// }

// //back message handler, when the block receive all the message from
// //its children execute the broadcasthandler
// void 
// treeBroadcastBackMsg(void)
// {
//   byte  spID = thisChunk->data[0];
//   if(trees[spID]->outstanding != 0) {
//     trees[spID]->outstanding --;
//   }
//   if( trees[spID]->outstanding == 0 ) {
//     if(isSpanningTreeRoot(trees[spID]) != 1) {
//       byte data[1];
//       data[0] = spID;
//       sendMySpChunk(trees[spID]->myParent, 
//                     data, 
//                     1, 
//                     (MsgHandler)&treeBroadcastBackMsg); 
//       trees[spID]->broadcasthandler();
//     } else {
//       trees[spID]->broadcasthandler();
//     }
//   }
// }

// ////////////////////////////////////////////////////////////////
// // public interface to spanning tree code
// ////////////////////////////////////////////////////////////////

// // for debugging to have some determinism
// // 0 -> no debugging, random ids
// // 1 -> above + colors for states
// // 2 -> above + send log msgs back to host
// void 
// setSpanningTreeDebug(int val)
// {
// }

// // allocate a set of <num> spanning trees.  If num is 0, use system default.
// // returns the base of num spanning tree to be used ids.
// int 
// initSpanningTrees(int num)
// {
//   if((maxSpanId + num) < MAX_SPANTREE_ID) {
//     int i; 
//     for( i = 0 ; i<num; i++) {
//       SpanningTree* spt = (SpanningTree*)malloc(sizeof(SpanningTree));
//       trees[maxSpanId] = spt;
//       spt->spantreeid = maxSpanId; /* the newId of this spanning tree */
//       spt->state= WAITING; /* state i am in forming the spanning tree */
//       maxSpanId++;
//     }
//     return (maxSpanId-num);     /* return base number to be used */
//   } else {
//     return -1; //the wanted allocation number exceed the maximun
//   }
// }

// // return the tree structure for a given tree id
// SpanningTree*
// getTree(int id)
// {
//   assert(id < MAX_SPANTREE_ID);
//   SpanningTree* ret = trees[id];
//   assert(ret != 0);
//   return ret;
// }

// // start a spanning tree with id#, id, where I am the root.  Must be
// // starte by only one node.
// // if timeout == 0, never time out
// void 
// startTreeByParent(SpanningTree* treee, byte spID, 
//                   SpanningTreeHandler donefunc, int timeout)
// {
//   //  niy("startTreeByParent");
// }

// // start a spanning tree with a random root, more than one node can
// // initiate this.
// // if timeout == 0, never time out
// void 
// createSpanningTree(SpanningTree* treee, SpanningTreeHandler donefunc, 
//                    int timeout)
// {
//   assert(treee->state == WAITING);

//   setColor(WHITE);
//   byte spId = treee->spantreeid;
//   trees[spId] = treee;
//   // set the state to STARTED
//   trees[spId]->state = STARTED;
//   trees[spId]->status = WAIT;
//   //done function for the spanning tree
//   trees[spId]->mydonefunc = donefunc;
//   //initialize myParent, every blocks thinks they are root at the beginning 
//   trees[spId]->myParent = 255;
//   //initialize children number
//   trees[spId]->numchildren = 0;
//   int i; 
//   for(  i = 0; i<NUM_PORTS; i++) {
//     trees[spId]->myChildren[i] = 0;
//   }

//   // pick a tree->value = rand()<<8|myid 
//   // (unless debug mode, then it is just id)
//   trees[spId]->value = (0 & rand()<<8)|getGUID();

//   // set tree->outstanding = counter to number of neighbors.
//   // send to all neighbors: tree->id, tree->value, cstHelper
//   trees[spId]->outstanding = 0;
//   byte data[3];
//   data[0] = spId;
//   GUIDIntoChar(trees[spId]->value, &(data[1]));

//   // if timeout > 0, set a timeout
//   if(timeout > 0) {
//     trees[spId]->spantimeout.callback = (GenericHandler)(&spCreation);
//     trees[spId]->spantimeout.arg = spId;
//     trees[spId]->spantimeout.calltime = getTime() + timeout;
//     registerTimeout(&(trees[spId]->spantimeout)); 
//   }
       
//   //send message to add children
//   byte p;
//   for( p = 0; p < NUM_PORTS;p++) {
//     if (thisNeighborhood.n[p] != VACANT) {	
//       trees[spId]->outstanding++;
//       sendMySpChunk(p, data, 3, (MsgHandler)&cstHelper); 
//     }
//   }
//   // now we wait til we reach the DONE state
//   int counter = 0;
//   while (trees[spId]->state != DONE) {
//     if (counter++ > 150000) {
//       char buffer[256];
//       blockprint(stderr, "Waiting: %s\n", tree2str(buffer, spId));
//       counter = 0;
//     }
//   }
//   blockprint(stderr, "ALL DONE - RETURNING\n");
// }

// // send msg in data to everyone in the spanning tree, call handler
// // when everyone has gotten the msg
// void 
// treeBroadcast(SpanningTree* treee, byte* data, byte size, MsgHandler handler)
// {  
//   //wait for the creation of the spanning in case it is not finished
//   delayMS(500); 
//   // start broadcast
//   byte id = treee->spantreeid;      
//   trees[id]->broadcasthandler = handler;
     
//   //the root will send the data its children and it will be propagate
//   //until the leaves
//   if( isSpanningTreeRoot(trees[id]) == 1) {
//     byte buf[size + 2];
//     buf[0] = id;
//     buf[1] = size;
//     memcpy(buf+2, data, size*sizeof(byte));
//     byte p;
//     for( p = 0; p < NUM_PORTS;p++){
//       if (trees[id]->myChildren[p] == 1) {
//         sendMySpChunk(p, buf, size + 2 , (MsgHandler)&treeBroadcastMsg); 
//       }
//     }
//   }
//   trees[id]->outstanding = trees[id]->numchildren;
// }


// // wait til everyone gets to a barrier.  I.e., every node in spanning
// // tree calls this function with the same id.  Will not return until
// // done or timeout secs have elapsed.  If timeout is 0, never timeout.
// // return 1 if timedout, 0 if ok.
// int 
// treeBarrier(SpanningTree* treee, byte id, int timeout)
// {
//   setColor(RED);
//   reachBarrier = 0;
//   byte spID = treee->spantreeid;
//   trees[spID] = treee;
//   trees[spID]->status = WAIT; 
//   if( timeout > 0){             //timeout for creating the barrier
//     barrierTimeout.callback = (GenericHandler)(&checkBarrier);
//     barrierTimeout.arg = spID;
//     barrierTimeout.calltime = getTime() + timeout;
//     registerTimeout(&barrierTimeout); 
//   }
//   byte buf[1];
//   buf[0] = spID;
//   trees[spID]->outstanding = trees[spID]->numchildren;
//   //the treeBarrier start with the leaves 
//   if( trees[spID]->numchildren == 0) {
//     byte buf[1];
//     buf[0] = spID;
//     // send message to myParent to make their status COMPLETED
//     sendMySpChunk(trees[spID]->myParent, buf, 1, (MsgHandler)&barrierMsg); 
//   }
//   allHaveBarrier = 1; 

//   while (  trees[spID]->status == WAIT ) { 
//     //wait for the message from the root which is telling that all the
//     //block get into a barrier
//     setColor(BROWN);  
//   } 
//   if( trees[spID]->status == COMPLETED ) {
//     if(timeout > 0){
//       deregisterTimeout(&barrierTimeout); 
//     }
//     if( isSpanningTreeRoot(trees[spID]) == 1){
//       byte p;
//       for(p  = 0; p < NUM_PORTS ; p++){
//         if (trees[spID]->myChildren[p] == 1){
//           // send messages to all the tree to tell that all the block
//           // get to a barrier
//           sendMySpChunk(p, buf, 1, (MsgHandler)&allHaveBarrierMsg);
//         }}
//     }
//     return 1;
//   } else {
//     //return 0 if the status is TIMEDOUT
    
//     return 0;
//   }
// }
  



// // find out if I am root
// byte 
// isSpanningTreeRoot(SpanningTree* treee)
// {
//   byte stId = treee->spantreeid;
//   if(trees[stId]->myParent == 255) {
//     return 1;
//   } else {
//     return 0;
//   }
// }

// // Local Variables:
// // mode: c
// // tab-width: 8
// // indent-tabs-mode: nil
// // c-basic-offset: 2
// // End:
