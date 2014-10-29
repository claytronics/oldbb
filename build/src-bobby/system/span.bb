#include "span.bbh"
#include "block.bbh"
#include "../sim/sim.h"
#include "myassert.h"

////////////////////////////////////////////////////////////////
// private functions for spanning tree code, see below for public interface
////////////////////////////////////////////////////////////////

static char* state2str(SpanTreeState x)
{
  switch (x) {
  case FREE: return "FREE";
  case STARTED: return "STARTED";
  case DONE: return "DONE";
  case HAVEPARENT: return "HAVEPARENT";
  case WAITING: return "WAITING";
  case FORMED: return "FORMED";
  }
  return "????";
}

threaddef #define MAX_SPANTREE_ID 16

// set by setSpanningTreeDebug
//static int debugmode = 0;

// list of ptrs to spanning tree structures
threadvar SpanningTree* trees[MAX_SPANTREE_ID];

// number of already allocated spanning tree structures.
threadvar int maxSpanId = 0;

// to know if all get into a barrier 
threadvar int allHaveBarrier = 0;
threadvar int reachBarrier = 0;

//variable for debugging
//static int colorDebug = 0;
//static int logDebug = 0;

//private functions
byte sendMySpChunk(byte myport, byte *data, byte size, MsgHandler mh);
void screwyou(void);
void iamyourchild(void);
void spComplete( void);
void treeBroadcastMsg(void);
void treeBroadcastBackMsg(void);
void cstHelper(void);
byte countSPChildren(byte id);
void sendToLeaf(void);
void allHaveBarrierMsg(void);
void finishingSpan(void) ;
void barrierAck(void);

//Timeout for the creation of the spanning tree
threadvar Timeout barrierTimeout;
threadvar Timeout finishTimeout;

void
initSpanningTreeInformation(void)
{
  int i;
  for (i=0; i<MAX_SPANTREE_ID; i++) {
    trees[i] = NULL;
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
          "%d: val:%d outstndg:%d parent:%d <%s> status:%d numchdrn:%d [", 
          id, st->value, st->outstanding, st->myParent, 
          state2str(st->state), st->status, st->numchildren);
  char* bp = buffer + strlen(buffer);
  int i;
  for (i=0; i<NUM_PORTS; i++) {
    sprintf(bp, " %d", st->myChildren[i]);
    bp += strlen(bp);
  }
  strcat(bp, " ]");
  return buffer;
}

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
    if ((spt->state == DONE)||(spt->state == FORMED)) continue;
    // we might have children that weren't ready last time we checked
    int i;
    for (i=0; i<NUM_PORTS; i++) {
      if (spt->myChildren[i] == 2) {
        // this was a slow child.  First unmark as slow
        spt->myChildren[i] = 0; /* indicate no longer on delay list */
        // now, resend msg to create tree
        byte data[3];
        data[0] = id;
        GUIDIntoChar(spt->value, &(data[1]));
        sendMySpChunk(i, data, 3, (MsgHandler)&cstHelper); 
      }
    }
  }
}

// this is a response to a cstHelper that says that block wasn't ready
// to start constructing tree yet.  Try again.
void
notReadyYet(void)
{
  byte id = thisChunk->data[0];
  blockprint(stderr, "%d says not ready for tree %d\n", 
             faceNum(thisChunk), id);
  // say which face needs a retry
  trees[id]->myChildren[faceNum(thisChunk)] = 2;
  // if we don't have a timeout yet, register it
  if (haveTimeout) return;
  haveTimeout = 1;
  nryTimeout.callback = (GenericHandler)(&retrySlowBlocks);
  nryTimeout.calltime = getTime()+100;
  registerTimeout(&nryTimeout);
}

// message handler for messages sent by a potential myParent 
// trying to make me a child
// 0: id
// 1-2: value
void 
cstHelper(void)
{
  byte potentialID = thisChunk->data[0];
  uint16_t potentialValue = charToGUID(&(thisChunk->data[1]));
  blockprint(stderr, "CST: %d %d\n", potentialID, potentialValue);

  assert(potentialID <= MAX_SPANTREE_ID);
  SpanningTree* st = trees[potentialID];

  if (st == NULL) {
    // we haven't started creating a tree here yet, so tell sender to
    // try again soon
    sendMySpChunk(faceNum(thisChunk), 
                  thisChunk->data, 
                  1, 
                  (MsgHandler)&notReadyYet);
    return;
  }

  char buffer[256];
  blockprint(stderr, "CST %s\n", tree2str(buffer, potentialID));

  // if we already have a value that is more than potentialValue, then
  // send back a NACK (screwyou) with our current value.
  if( st->value >=  potentialValue )    {
    byte data[5]; 
    data[0] = st->spantreeid;
    GUIDIntoChar(potentialValue, &(data[1]));
    GUIDIntoChar(st->value, &(data[3]));
    sendMySpChunk(faceNum(thisChunk), data, 5, (MsgHandler)&screwyou); 
  } else {
    // otherwise recursively start process for all my other ports
    // (meaning, send a cstHelper msg) when recusrive procedure is
    // done, make sender my myParent and send back an ACK
    // (iamyourchild)
    st->outstanding = 0;
    st->value = potentialValue;
    st->myParent = faceNum(thisChunk);
    st->state = HAVEPARENT;
    byte i;
    for( i = 0; i<NUM_PORTS; i++) {
      st->myChildren[i] = 0;
    }
    st->numchildren = 0;
    st->state = WAITING;
    byte data[3];
    data[0] = potentialID;
    GUIDIntoChar(st->value, &(data[1]));
    /* Send add yourself to all neighbors */
    byte p;
    for( p = 0 ; p < NUM_PORTS; p++) {
      if ((thisNeighborhood.n[p] != VACANT) 
          && (p != st->myParent)) {	
        st->outstanding++;
        sendMySpChunk(p, data,3, (MsgHandler)&cstHelper); 
      }
    }
    /* Send iamyourchild to parent */
    sendMySpChunk((st->myParent), data, 3, (MsgHandler)&iamyourchild); 

    // check to see if we are a leaf in a potentially completed tree
    if (st->outstanding == 0) {
      // if outstanding == 0, then we don't have any neighbors but the parent.
      st->state = FORMED;
    }
  }
  blockprint(stderr, "After cstHelper: %s\n", tree2str(buffer, potentialID));
}

void
finishCreation(SpanningTree* st)
{
  st->state = DONE;
  st->status = COMPLETED;
  deregisterTimeout(&st->spantimeout);
  st->mydonefunc(st,COMPLETED);
}


//message back to the root to complete the tree 
void 
spComplete(void)
{
  byte potentialID = thisChunk->data[0];
  uint16_t potentialValue = charToGUID(&(thisChunk->data[1]));
  SpanningTree* st = trees[potentialID];

  // if the value potentialValue is different from the tree value just ignore,
  if( st->value != potentialValue ) {
    blockprint(stderr, "%d -> spcomplete with %d, but I have %d\n", 
               faceNum(thisChunk), potentialValue, st->value);
    return;
  }

  // if my state is not FORMED, then error
  assert(st->state == FORMED);

  // we should have outstanding > 0
  assert(st->outstanding > 0);
  st->outstanding--;

  // when received all the messages from the children send spComplete
  // to myParent if root send message to leaves
  if( st->outstanding == 0 ) {
    
    byte data[3];
    data[0] = st->spantreeid;
    GUIDIntoChar(st->value, &(data[1]));
    if(!isSpanningTreeRoot(st) == 0) {
      sendMySpChunk( st->myParent, data, 3, (MsgHandler)&spComplete);   
    } else {
      /* IS ROOT */
      // if the root receive the spComplete, send message 
      // to the leaves to tell them to execute their done function
      byte i;
      for(i = 0; i<NUM_PORTS; i++){
        if(st->myChildren[i] == 1) {
          sendMySpChunk(i , data, 3, (MsgHandler)&sendToLeaf);   
        }
      }
      // we are done!
      finishCreation(st);
    }
  }
}

//send message to the leaves to launch their mydonefunc
void 
sendToLeaf(void)
{
  byte potentialID = thisChunk->data[0];
  uint16_t potentialValue = charToGUID(&(thisChunk->data[1]));
  SpanningTree* st = trees[potentialID];

  byte data[3];
  data[0] = st->spantreeid;
  GUIDIntoChar(st->value, &(data[1]));
  
  // if the value potentialValue is different from the tree value just ignore,
  if( st->value != potentialValue ) {
    blockprint(stderr, "%d -> sendToLeaf with %d, but I have %d\n", 
               faceNum(thisChunk), potentialValue, st->value);
    return;
  }

  // we are in final tree, so send to children that we are done

  // if my state is not FORMED, then error
  assert(st->state == FORMED);
  // we should also have no outstanding msgs
  assert(st->outstanding == 0);

  byte i;
  for ( i = 0; i<NUM_PORTS; i++) {
    if (st->myChildren[i] == 1) {
      sendMySpChunk(i , data, 3, (MsgHandler)&sendToLeaf);   
    }
  }
  // now we are DONE
  finishCreation(st);
}

// see whether we should initiate msgs to the route to indicate we are done.
void
adjustChildren(SpanningTree* st)
{
  st->numchildren = countSPChildren(st->spantreeid);
  if (st->outstanding == 0) {
    st->state = FORMED;
    st->outstanding = st->numchildren;
    // init spComplete message chain up tree if we are a leaf
    if( st->numchildren == 0) {
      byte data[3];
      data[0] = st->spantreeid;
      GUIDIntoChar(st->value, &(data[1]));
      if (!isSpanningTreeRoot(st)) {
        sendMySpChunk(st->myParent, data, 3, (MsgHandler)&spComplete);   
      } else {
        // this is a single block ensemble??
        assert(getNeighborCount() == 0);
        finishCreation(st);
      }
    }
  }
}

// a message from neighbor when it has been late to the game asking
// for a cstHelper msg
// data[0] = tree id
void
pleaseTryMe(void)
{
  byte potentialID = thisChunk->data[0];
  SpanningTree* st = trees[potentialID];
}

// a message receives when a neighbor block doesn't want me to enter a tree.
// data[0] = tree id
// data[1-2] = value I sent
// data[3-4] = value of neighbor
void screwyou(void)
{
  byte potentialID = thisChunk->data[0];
  uint16_t potentialValue = charToGUID(&(thisChunk->data[1]));
  SpanningTree* st = trees[potentialID];
  blockprint(stderr, "face %d screws me for tree %d with value %d\n", 
             faceNum(thisChunk), potentialID, potentialValue);

  if( potentialValue == st->value ) {
    // I got screwed with same value I sent, so this msg is important.
    uint16_t neighborValue = charToGUID(&(thisChunk->data[2]));
    if (neighborValue == potentialValue) {
      // we both have same value, so I am already in the same tree
      st->myChildren[faceNum(thisChunk)] = 0;
      st->outstanding--;
      adjustChildren(st);
    } else if (neighborValue > potentialValue) {
      // neighborValue has higher value, lets join it
      st->myChildren[faceNum(thisChunk)] = 0;
      st->outstanding--;
      // don't call adjust child, don't want to change my state
      byte data[1];
      data[0] = 0;//spId;
      sendMySpChunk(faceNum(thisChunk), data, 1, (MsgHandler)&pleaseTryMe); 
    }
  }
  char buffer[512];
  blockprint(stderr, "After screwing: %s\n", tree2str(buffer, potentialID));
}

// Received from a block that has entered my tree as a child.  
void iamyourchild(void)
{
  byte potentialID = thisChunk->data[0];
  uint16_t potentialValue = charToGUID(&(thisChunk->data[1]));
  SpanningTree* st = trees[potentialID];
  if( potentialValue == st->value ){
    st->myChildren[faceNum(thisChunk)] = 1;
    st->outstanding--;
    adjustChildren(st);
  }
  char buffer[512];
  blockprint(stderr, "After getting child on %d: %s\n", 
             faceNum(thisChunk), tree2str(buffer, potentialID));
}

byte countSPChildren(byte spId)
{
  byte count = 0;
  byte i;
  for(i = 0 ; i<NUM_PORTS; i++)  {
    if(trees[spId]->myChildren[i] == 1) {
      count++;
    }
  }	
  return count;
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
  if (sendMessageToPort(c, myport, data, size, mh, NULL) == 0) {
    freeChunk(c);
    blockprint(stderr, "FAILED TO SEND\n");
    return 0;
  } else {
    return 1;
  }
}

// called when spanning tree times out 
void spCreation(void)
{
  blockprint(stderr, "Spanning tree timed out!!\n");
  /* byte d = trees[id]->spantimeout.arg; */
  /* trees[targetid]->mydonefunc(trees[id],TIMEDOUT); */
  /* trees[id]->status = TIMEDOUT; */
}

//message send to myParent if only all the children get into a barrier 
void 
barrierMsg(void) 
{
  byte  spID = thisChunk->data[0];
 check:
  if( allHaveBarrier == 1 ){
    trees[spID]->outstanding--; 
    byte buf[1];
    buf[0] = spID;
    if( trees[spID]->outstanding == 0)
      {
 
        if (isSpanningTreeRoot(trees[spID]) == 1){
          //if root received all messages from its children, it will send
          //messages to all the tree to tell that all the block get to a
          //barrier
          trees[spID]->status = COMPLETED;  // The status is changed to
          // break the while loop inside
          // the treeBarrier function so
          // that the root is able to
          // send allHaveBarrier
          // children to the whole
          // ensemble
        }
        else{
          // send message to myParent to make their status COMPLETED
          sendMySpChunk(trees[spID]->myParent, 
                        buf, 
                        1, 
                        (MsgHandler)&barrierMsg); 
        }
      }
  } else {
    goto check;
  }
}

//timeout for checking if all the blocks get into a barrier
void 
checkBarrier(void)
{
  /* byte id = barrierTimeout.arg; */
  /* trees[id]->status = TIMEDOUT; */
}

//message sent from the root to the leaves to say that all the blocks
//get into a barrier
void 
allHaveBarrierMsg(void)
{
  
  byte  spID = thisChunk->data[0];
  byte buf[1];
  buf[0] = spID;
  byte p ;
  if( reachBarrier == 0){
    for( p = 0 ; p < NUM_PORTS ; p++){ 
      //send the message allHaveBarrier to all the neighbor except the
      //sender of this message
      if (thisNeighborhood.n[p] != VACANT && p != faceNum(thisChunk)) {	
        //this message will change the status of the tree into
        //completed and break their while loop inside the treeBarrier
        //function
        sendMySpChunk(p, buf, 1, (MsgHandler)&allHaveBarrierMsg);   
      }
    }
    reachBarrier = 1;
  }
  trees[spID]->status = COMPLETED;
  
}

//handler for sending the data to all the tree
void 
treeBroadcastMsg(void)
{
  byte  spID = thisChunk->data[0];
  byte  size = thisChunk->data[1];
  byte buf[size + 2];
  buf[0] = spID;
  buf[1] = size;
  //the data will start from buf[2] if users want to use it
  memcpy(buf+2, thisChunk->data+2, size*sizeof(byte));
  byte p; 
  for( p = 0; p < NUM_PORTS;p++){ //send data to all the children
    if (trees[spID]->myChildren[p] == 1) {	
      sendMySpChunk(p, buf, size + 2, (MsgHandler)&treeBroadcastMsg); 
    }
  }
  if( trees[spID]->numchildren == 0 ){
    byte data[1];
    data[0] = spID;
    sendMySpChunk(trees[spID]->myParent, 
                  data, 
                  1, 
                  (MsgHandler)&treeBroadcastBackMsg); 
    trees[spID]->broadcasthandler();
  } 
}

//back message handler, when the block receive all the message from
//its children execute the broadcasthandler
void 
treeBroadcastBackMsg(void)
{
  byte  spID = thisChunk->data[0];
  if(trees[spID]->outstanding != 0) {
    trees[spID]->outstanding --;
  }
  if( trees[spID]->outstanding == 0 ) {
    if(isSpanningTreeRoot(trees[spID]) != 1) {
      byte data[1];
      data[0] = spID;
      sendMySpChunk(trees[spID]->myParent, 
                    data, 
                    1, 
                    (MsgHandler)&treeBroadcastBackMsg); 
      trees[spID]->broadcasthandler();
    } else {
      trees[spID]->broadcasthandler();
    }
  }
}

////////////////////////////////////////////////////////////////
// public interface to spanning tree code
////////////////////////////////////////////////////////////////

// for debugging to have some determinism
// 0 -> no debugging, random ids
// 1 -> above + colors for states
// 2 -> above + send log msgs back to host
void 
setSpanningTreeDebug(int val)
{
}

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
      spt->state= WAITING; /* state i am in forming the spanning tree */
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

// start a spanning tree with id#, id, where I am the root.  Must be
// starte by only one node.
// if timeout == 0, never time out
void 
startTreeByParent(SpanningTree* treee, byte spID, 
                  SpanningTreeHandler donefunc, int timeout)
{
  //  niy("startTreeByParent");
}

// start a spanning tree with a random root, more than one node can
// initiate this.
// if timeout == 0, never time out
void 
createSpanningTree(SpanningTree* treee, SpanningTreeHandler donefunc, 
                   int timeout)
{
  assert(treee->state == WAITING);

  setColor(WHITE);
  byte spId = treee->spantreeid;
  trees[spId] = treee;
  // set the state to STARTED
  trees[spId]->state = STARTED;
  trees[spId]->status = WAIT;
  //done function for the spanning tree
  trees[spId]->mydonefunc = donefunc;
  //initialize myParent, every blocks thinks they are root at the beginning 
  trees[spId]->myParent = 255;
  //initialize children number
  trees[spId]->numchildren = 0;
  int i; 
  for(  i = 0; i<NUM_PORTS; i++) {
    trees[spId]->myChildren[i] = 0;
  }

  // pick a tree->value = rand()<<8|myid 
  // (unless debug mode, then it is just id)
  trees[spId]->value = (0 & rand()<<8)|getGUID();

  // set tree->outstanding = counter to number of neighbors.
  // send to all neighbors: tree->id, tree->value, cstHelper
  trees[spId]->outstanding = 0;
  byte data[3];
  data[0] = spId;
  GUIDIntoChar(trees[spId]->value, &(data[1]));

  // if timeout > 0, set a timeout
  if(timeout > 0) {
    trees[spId]->spantimeout.callback = (GenericHandler)(&spCreation);
    trees[spId]->spantimeout.arg = spId;
    trees[spId]->spantimeout.calltime = getTime() + timeout;
    registerTimeout(&(trees[spId]->spantimeout)); 
  }
       
  //send message to add children
  byte p;
  for( p = 0; p < NUM_PORTS;p++) {
    if (thisNeighborhood.n[p] != VACANT) {	
      trees[spId]->outstanding++;
      sendMySpChunk(p, data, 3, (MsgHandler)&cstHelper); 
    }
  }
  // now we wait til we reach the DONE state
  int counter = 0;
  while (trees[spId]->state != DONE) {
    if (counter++ > 150000) {
      char buffer[256];
      blockprint(stderr, "Waiting: %s\n", tree2str(buffer, spId));
      counter = 0;
    }
  }
  blockprint(stderr, "ALL DONE - RETURNING\n");
}

// send msg in data to everyone in the spanning tree, call handler
// when everyone has gotten the msg
void 
treeBroadcast(SpanningTree* treee, byte* data, byte size, MsgHandler handler)
{  
  //wait for the creation of the spanning in case it is not finished
  delayMS(500); 
  // start broadcast
  byte id = treee->spantreeid;      
  trees[id]->broadcasthandler = handler;
     
  //the root will send the data its children and it will be propagate
  //until the leaves
  if( isSpanningTreeRoot(trees[id]) == 1) {
    byte buf[size + 2];
    buf[0] = id;
    buf[1] = size;
    memcpy(buf+2, data, size*sizeof(byte));
    byte p;
    for( p = 0; p < NUM_PORTS;p++){
      if (trees[id]->myChildren[p] == 1) {
        sendMySpChunk(p, buf, size + 2 , (MsgHandler)&treeBroadcastMsg); 
      }
    }
  }
  trees[id]->outstanding = trees[id]->numchildren;
}


// wait til everyone gets to a barrier.  I.e., every node in spanning
// tree calls this function with the same id.  Will not return until
// done or timeout secs have elapsed.  If timeout is 0, never timeout.
// return 1 if timedout, 0 if ok.
int 
treeBarrier(SpanningTree* treee, byte id, int timeout)
{
  setColor(RED);
  reachBarrier = 0;
  byte spID = treee->spantreeid;
  trees[spID] = treee;
  trees[spID]->status = WAIT; 
  if( timeout > 0){             //timeout for creating the barrier
    barrierTimeout.callback = (GenericHandler)(&checkBarrier);
    barrierTimeout.arg = spID;
    barrierTimeout.calltime = getTime() + timeout;
    registerTimeout(&barrierTimeout); 
  }
  byte buf[1];
  buf[0] = spID;
  trees[spID]->outstanding = trees[spID]->numchildren;
  //the treeBarrier start with the leaves 
  if( trees[spID]->numchildren == 0) {
    byte buf[1];
    buf[0] = spID;
    // send message to myParent to make their status COMPLETED
    sendMySpChunk(trees[spID]->myParent, buf, 1, (MsgHandler)&barrierMsg); 
  }
  allHaveBarrier = 1; 

  while (  trees[spID]->status == WAIT ) { 
    //wait for the message from the root which is telling that all the
    //block get into a barrier
    setColor(BROWN);  
  } 
  if( trees[spID]->status == COMPLETED ) {
    if(timeout > 0){
      deregisterTimeout(&barrierTimeout); 
    }
    if( isSpanningTreeRoot(trees[spID]) == 1){
      byte p;
      for(p  = 0; p < NUM_PORTS ; p++){
        if (trees[spID]->myChildren[p] == 1){
          // send messages to all the tree to tell that all the block
          // get to a barrier
          sendMySpChunk(p, buf, 1, (MsgHandler)&allHaveBarrierMsg);
        }}
    }
    return 1;
  } else {
    //return 0 if the status is TIMEDOUT
    
    return 0;
  }
}
  



// find out if I am root
byte 
isSpanningTreeRoot(SpanningTree* treee)
{
  byte stId = treee->spantreeid;
  if(trees[stId]->myParent == 255) {
    return 1;
  } else {
    return 0;
  }
}

// Local Variables:
// mode: c
// tab-width: 8
// indent-tabs-mode: nil
// c-basic-offset: 2
// End:
