#include "bfsTree.bbh"
#include "bbassert.bbh"
#include "message.bbh"
#include "serial.bbh"

#define BFS_DEBUG_PRINT(...) //printf(__VA_ARGS__)
#define BC_DEBUG_PRINT(...) //printf(__VA_ARGS__)

#define __MY_FILENAME__ "bfsTree.bbh"

threaddef #define NUM_MAX_REGISTERED_BFS_TRAVERSALS 10
threadvar byte numRegisteredBFSTravs = 0;
threadvar BFSTraversal_t *traversals[NUM_MAX_REGISTERED_BFS_TRAVERSALS];

#define GET_BFS(c) (traversals[c->data[BFS_ID_INDEX]])
#define GET_GUID_FROM_PORT(p) (thisNeighborhood.n[p])

// MSG FCT:
static void checkAndBroadcastBFSGo(BFSTraversal_t *bfs);
static void checkAndForwardBFSBack(BFSTraversal_t *bfs);

static byte sumDataBFS(byte *t);
static void sendBFSGo(PRef p, BFSTraversal_t *bfs);
static void broadcastBFSGo(BFSTraversal_t *bfs); 
static void forwardBFSBack(BFSTraversal_t *bfs);
static void sendBFSAckGo(void);
static void sendBFSDelete(BFSTraversal_t * bfs, PRef dest, Uid root, distance_t d);

static void handle_BFS_SP_GO(void);
static void handle_BFS_SP_ACK_GO(void);
static void handle_BFS_SP_BACK(void);
static void handle_BFS_SP_DELETE(void);

// Broadcast + convergecast			     
		     
static void broadcastBFSChildren(BFSTraversal_t *bfs, byte *data);
static void sendBFSConvergecast(BFSTraversal_t *bfs);
static void checkBFSConvergecastEnd(BFSTraversal_t *bfs);
  
static void handle_BFS_BROADCAST(void);
static void handle_BFS_CONVERGECAST(void);

// default empty callbacks
static void defaultBFSGenericHandler(void) {}
static void defaultBFSStatusHandler(BFSTraversal_t* bfs) {}
static void defaultBFSChildHandler(Chunk* c) {}
static void defaultBFSDataHandler(byte* data, byte l) {}

void initBFSTraversal(BFSTraversal_t *bfs) {
  byte i = 0;
  
  bfs->sysSize = 0;
  bfs->electing = 0;
  bfs->id = 0;

  bfs->callbacks.bfsGoVisit = (BFSChildHandler) &defaultBFSChildHandler;
  bfs->callbacks.addChild = (BFSChildHandler) &defaultBFSChildHandler;
  bfs->callbacks.removeChild = (BFSChildHandler) &defaultBFSChildHandler;
  bfs->callbacks.reset = (BFSGenericHandler) &defaultBFSGenericHandler;
  
  bfs->callbacks.fillBFSGoData = (BFSDataHandler) &defaultBFSDataHandler;
  bfs->callbacks.fillBFSBackData = (BFSDataHandler) &defaultBFSDataHandler;

  bfs->callbacks.terminationHandler = (BFSStatusHandler) &defaultBFSStatusHandler;

  bfs->tree.broadConv.callbacks.broadcastVisit = (BFSChildHandler) &defaultBFSChildHandler;
  bfs->tree.broadConv.callbacks.convergecastVisit = (BFSChildHandler) &defaultBFSChildHandler;
  bfs->tree.broadConv.callbacks.fillBroadcastData = (BFSDataHandler) &defaultBFSDataHandler;
  bfs->tree.broadConv.callbacks.fillConvergecastData = (BFSDataHandler) &defaultBFSDataHandler;
  bfs->tree.broadConv.callbacks.terminationHandler = (BFSStatusHandler) &defaultBFSStatusHandler;
  
  resetBFSTraversal(bfs);

  for (i = 0; i < NUM_PORTS; i++) {
    bfs->prevGoAcked[i] = 1;
  }
}

void resetBFSTraversal(BFSTraversal_t *bfs) {
  byte i = 0;
  
  bfs->tree.root = 0;
  bfs->tree.distance = MAX_DISTANCE;
  bfs->finished = 0;
  bfs->tree.parent = UNDEFINED_PORT;

  bfs->tree.broadConv.waiting = 0;
  bfs->tree.broadConv.height = 0;
  bfs->tree.broadConv.size = 0;
  bfs->tree.broadConv.farthest = UNDEFINED_PORT;
  
  for (i = 0; i < NUM_PORTS; i++) {
    bfs->tree.children[i] = 0;
    bfs->branchSize[i] = 0;
    bfs->branchHeight[i] = 0;
    bfs->waiting[i] = 0;
  }

  bfs->callbacks.reset();
}

byte registerBFSTraversal(BFSTraversal_t *bfs) {

  if (bfs == NULL) {
    return 0;
  }

  if (numRegisteredBFSTravs+1 < NUM_MAX_REGISTERED_BFS_TRAVERSALS) {
    traversals[numRegisteredBFSTravs] = bfs;
    bfs->id = numRegisteredBFSTravs;

    numRegisteredBFSTravs++;
    return 1;
  }
  
  return 0;
}


void takePartInBFS(Chunk *c) {
  BFSTraversal_t *bfs = GET_BFS(c);
  Uid root = charToGUID(&(c->data[BFS_ROOT_INDEX]));
  bfs->tree.root = root;
  bfs->tree.distance = MAX_DISTANCE;
  bfs->tree.parent = UNDEFINED_PORT;
}

void setBFSRoot(BFSTraversal_t *bfs) {
  bfs->tree.root = getGUID();
  bfs->tree.distance = 0;
  bfs->tree.parent = UNDEFINED_PORT;
}

byte sumDataBFS(byte *t) {
  byte p = 0;
  byte d = 0;

  for (p = 0; p < NUM_PORTS; p++) {
    d += t[p];
  }
  
  return d;
}

sysSize_t getSysSize(BFSTraversal_t *bfs) {
  PRef p = 0;
  sysSize_t s = 1;

  for (p = 0; p < NUM_PORTS; p++) {  
      s += bfs->branchSize[p];
  }
  
  return s;
}

distance_t getHeight(BFSTraversal_t *bfs) {
  PRef p = 0;
  distance_t h = 0; 
  
  for (p = 0; p < NUM_PORTS; p++) {
    if (bfs->branchHeight[p] > h) {
      h = bfs->branchHeight[p];
    }
  }
  
  return h;
}

byte getBFSNumChildren(BFSTraversal_t *bfs) {
  return sumDataBFS(bfs->tree.children);
}

void startBFSTraversal(BFSTraversal_t *bfs) {
  BFS_DEBUG_PRINT("%d: start BFS\n",getGUID());
  
  checkAndBroadcastBFSGo(bfs);
  checkAndForwardBFSBack(bfs);
}

void checkAndBroadcastBFSGo(BFSTraversal_t *bfs) {
  broadcastBFSGo(bfs);
}

void checkAndForwardBFSBack(BFSTraversal_t *bfs) {
  byte w = sumDataBFS(bfs->waiting);

  BFS_DEBUG_PRINT("%u: BACK #waiting = %u\n", getGUID(), w);
  
  if (w == 0) {
    
    if (bfs->tree.root == getGUID()) {
      sysSize_t s = getSysSize(bfs);
      BFS_DEBUG_PRINT("%u: size = %u, bfs->sysSize %u\n", getGUID(), s, bfs->sysSize);
      if (bfs->sysSize == 0 || s == bfs->sysSize) {
	bfs->finished = 1;
	bfs->callbacks.terminationHandler(bfs);
      }
    } else {
      forwardBFSBack(bfs);
    }
  }
}

void broadcastBFSGo(BFSTraversal_t *bfs) {
  byte p = 0, s = 0;
  
  for (p = 0; p < NUM_PORTS; p++) {
    if ((p == bfs->tree.parent) ||
	(thisNeighborhood.n[p] == VACANT)) {
      continue;
    }
    s++;
    sendBFSGo(p,bfs);
  }
  BFS_DEBUG_PRINT("%u: BFS-BROAD-GO #sent = %u vs %u\n", getGUID(), s,getNeighborCount());
  BFS_DEBUG_PRINT("%u: (current: root=%u distance=%u parent=%u)\n", getGUID(), bfs->tree.root, bfs->tree.distance,GET_GUID_FROM_PORT(bfs->tree.parent));
}

void sendBFSGo(PRef d, BFSTraversal_t *bfs) {

  if (bfs->prevGoAcked[d]) {
    byte data[DATA_SIZE] = {0};
    
    bfs->prevGoAcked[d] = 0;
    data[BFS_ID_INDEX] = bfs->id;
    GUIDIntoChar(bfs->tree.root, &(data[BFS_ROOT_INDEX]));
    data[BFS_DISTANCE_INDEX] = bfs->tree.distance;
    
    // get APP data
    bfs->callbacks.fillBFSGoData(data,BFS_GO_DATA_INDEX);
    
    // send Chunk
    sendUserMessage(d, data, DATA_SIZE, (MsgHandler)& handle_BFS_SP_GO, (GenericHandler)&freeUserChunk);
  }
  
  bfs->waiting[d] = 1;
}


void forwardBFSBack(BFSTraversal_t *bfs) {
  byte data[DATA_SIZE] = {0};
    
  data[BFS_ID_INDEX] = bfs->id;
  GUIDIntoChar(bfs->tree.root, &(data[BFS_ROOT_INDEX]));
  data[BFS_DISTANCE_INDEX] = bfs->tree.distance-1;
  
  data[BFS_HEIGHT_INDEX] = getHeight(bfs);
  data[BFS_SIZE_INDEX] = getSysSize(bfs);
  
  // get APP data
  bfs->callbacks.fillBFSBackData(data,BFS_BACK_DATA_INDEX);
  
  // send Chunk
  sendUserMessage(bfs->tree.parent, data, DATA_SIZE, (MsgHandler)& handle_BFS_SP_BACK, (GenericHandler)&freeUserChunk);
}

void sendBFSAckGo(void) {
  PRef dest = faceNum(thisChunk);
  sendUserMessage(dest, thisChunk->data, DATA_SIZE, (MsgHandler)& handle_BFS_SP_ACK_GO, (GenericHandler)&freeUserChunk);  
}

void sendBFSDelete(BFSTraversal_t * bfs, PRef dest, Uid root, distance_t d) {
  byte data[DATA_SIZE] = {0};
  
  data[BFS_ID_INDEX] = bfs->id;
  GUIDIntoChar(root, &(data[BFS_ROOT_INDEX]));
  data[BFS_DISTANCE_INDEX] = d;
  
  // send Chunk
  sendUserMessage(dest, data, DATA_SIZE, (MsgHandler)& handle_BFS_SP_DELETE, (GenericHandler)&freeUserChunk);
}

void handle_BFS_SP_ACK_GO(void) {
  PRef from = faceNum(thisChunk);
  BFSTraversal_t *bfs = GET_BFS(thisChunk);
  Uid root = charToGUID(&(thisChunk->data[BFS_ROOT_INDEX]));
  distance_t distance = thisChunk->data[BFS_DISTANCE_INDEX];

  bbassert(!bfs->prevGoAcked[from]);

  BFS_DEBUG_PRINT("%d: BFS ACK_GO root %u distance %u from %u\n",getGUID(),root,distance,GET_GUID_FROM_PORT(from));
  
  bfs->prevGoAcked[from] = 1;
  
  if (bfs->tree.root != root || bfs->tree.distance != distance) {
    if (bfs->tree.parent != from)
      sendBFSGo(from,bfs);
  }
}

void handle_BFS_SP_GO(void) {
  PRef from = faceNum(thisChunk);
  BFSTraversal_t *bfs = GET_BFS(thisChunk);
  Uid root = charToGUID(&(thisChunk->data[BFS_ROOT_INDEX]));
  distance_t distance = thisChunk->data[BFS_DISTANCE_INDEX]+1;

  //bbassert(!bfs->finished);

  BFS_DEBUG_PRINT("%u: BFS_GO electing %u, root %u (vs %u), distance %u (vs %u)\n",getGUID(),bfs->electing, root,bfs->tree.root,distance,bfs->tree.distance);
  
  bfs->callbacks.bfsGoVisit(thisChunk);

  // ackGo
  sendBFSAckGo();
  
  if ((bfs->electing == 1) &&
      (root < bfs->tree.root)) {
    
    // reset election
    resetBFSTraversal(bfs);
    takePartInBFS(thisChunk);
  }
  
  if (bfs->tree.root == root &&
      distance < bfs->tree.distance) {

    BFS_DEBUG_PRINT("%u: BFS_GO OK root %u distance %u\n",getGUID(),root,distance);
    
    if (bfs->tree.parent != UNDEFINED_PORT) {
      sendBFSDelete(bfs, bfs->tree.parent, bfs->tree.root, bfs->tree.distance-1);
    }

    // reset
    resetBFSTraversal(bfs);
    
    bfs->tree.root = root;
    bfs->tree.distance = distance;
    bfs->tree.parent = from;
    
    // user reset
    bfs->callbacks.reset();

    // Check if can broadcast GO msg (avoid clogging up the message queue)
    checkAndBroadcastBFSGo(bfs);
    checkAndForwardBFSBack(bfs);

  } else if (root == bfs->tree.root) {
    sendBFSDelete(bfs,from,root,distance-1);
  }
}

void handle_BFS_SP_BACK(void) {
  PRef from = faceNum(thisChunk);
  BFSTraversal_t *bfs = GET_BFS(thisChunk);
  Uid root = charToGUID(&(thisChunk->data[BFS_ROOT_INDEX]));
  distance_t distance = thisChunk->data[BFS_DISTANCE_INDEX];

  distance_t height = thisChunk->data[BFS_HEIGHT_INDEX];
  sysSize_t size = thisChunk->data[BFS_SIZE_INDEX];
    
  if (bfs->tree.root == root &&
      bfs->tree.distance == distance &&
      !bfs->finished) {
    
    BFS_DEBUG_PRINT("%u: BFS_BACK OK root %u distance %u from %u\n",getGUID(),root,distance,GET_GUID_FROM_PORT(from));
    
    bfs->waiting[from] = 0;
    bfs->tree.children[from] = 1;

    bfs->branchHeight[from] = height + 1;
    bfs->branchSize[from] = size;

    bfs->callbacks.addChild(thisChunk);
    
    checkAndForwardBFSBack(bfs);
  }

}

void handle_BFS_SP_DELETE(void) {
  PRef from = faceNum(thisChunk);
  BFSTraversal_t *bfs = GET_BFS(thisChunk);
  Uid root = charToGUID(&(thisChunk->data[BFS_ROOT_INDEX]));
  distance_t distance = thisChunk->data[BFS_DISTANCE_INDEX];
  
  if (bfs->tree.root == root &&
      bfs->tree.distance == distance &&
      !bfs->finished) {

    BFS_DEBUG_PRINT("%u: BFS_DELETE OK root %u distance %u from %u\n",getGUID(),root,distance,GET_GUID_FROM_PORT(from));
    
    bfs->waiting[from] = 0;
    bfs->tree.children[from] = 0;
    
    bfs->branchHeight[from] = 0;
    bfs->branchSize[from] = 0;

    bfs->callbacks.removeChild(thisChunk);

    checkAndForwardBFSBack(bfs);
  }
}


// BROADCAST + CONVERGECAST

// User functions
void startBFSBroadcast(BFSTraversal_t *bfs, byte convergecast) {
  byte data[DATA_SIZE] = {0};  

  data[BFS_ID_INDEX] = bfs->id;
  data[BFS_CONV_BOOL_INDEX] = convergecast;

  bfs->tree.broadConv.size = 1;
  bfs->tree.broadConv.height = 0;
  
  // fill app data
  bfs->tree.broadConv.callbacks.fillBroadcastData(data,BFS_BROADCAST_DATA_INDEX);

  // broadcast over the tree
  broadcastBFSChildren(bfs,data);

  if (convergecast) {
    checkBFSConvergecastEnd(bfs);
  }
}

void startBFSConvergecast(BFSTraversal_t *bfs) {
  checkBFSConvergecastEnd(bfs);
}

// Utils functions
void broadcastBFSChildren(BFSTraversal_t *bfs, byte *data) {
  PRef p = 0;
  byte convergecast = data[BFS_CONV_BOOL_INDEX];

  for (p = 0; p < NUM_PORTS; p++) {
    if (bfs->tree.children[p]) {
      byte s = sendUserMessage(p, data, DATA_SIZE, (MsgHandler)&handle_BFS_BROADCAST, (GenericHandler)&freeUserChunk);
      bbassert(s);
      if (convergecast) {
	bfs->tree.broadConv.waiting++;
      }
    }
  }
}

void sendBFSConvergecast(BFSTraversal_t *bfs) {
  byte data[DATA_SIZE] = {0};

  data[BFS_ID_INDEX] = bfs->id;

  data[BFS_CONV_HEIGHT_INDEX] = bfs->tree.broadConv.height;
  data[BFS_CONV_SIZE_INDEX] = bfs->tree.broadConv.size;

  bfs->tree.broadConv.callbacks.fillConvergecastData(data,BFS_CONV_DATA_INDEX);
  
  sendUserMessage(bfs->tree.parent, data, DATA_SIZE, (MsgHandler)&handle_BFS_CONVERGECAST, (GenericHandler)&freeUserChunk);
}

void checkBFSConvergecastEnd(BFSTraversal_t *bfs) {
  if (bfs->tree.broadConv.waiting == 0) {
    if (bfs->tree.root == getGUID()) {
      bfs->tree.broadConv.callbacks.terminationHandler(bfs);
    } else {      
      sendBFSConvergecast(bfs);
    }
  }
}

// Message handlers
void handle_BFS_BROADCAST(void) {
  BFSTraversal_t *bfs = GET_BFS(thisChunk);
  byte convergecast = thisChunk->data[BFS_CONV_BOOL_INDEX];

  BC_DEBUG_PRINT("%u: BROADCAST root %u distance %u from %u #children %u\n",getGUID(),bfs->tree.root,bfs->tree.distance,GET_GUID_FROM_PORT(bfs->tree.parent),getBFSNumChildren(bfs));
    
  // visit
  bfs->tree.broadConv.callbacks.broadcastVisit(thisChunk);

  bfs->tree.broadConv.size = 1;
  bfs->tree.broadConv.height = 0;
  
  // send broacasted message to all children
  broadcastBFSChildren(bfs,thisChunk->data);

  if(convergecast) {
    checkBFSConvergecastEnd(bfs);
  }
}

void handle_BFS_CONVERGECAST(void) {
  BFSTraversal_t *bfs = GET_BFS(thisChunk);
  byte from = faceNum(thisChunk);

  distance_t height = thisChunk->data[BFS_CONV_HEIGHT_INDEX] + 1;
  sysSize_t size = thisChunk->data[BFS_CONV_SIZE_INDEX];

  BC_DEBUG_PRINT("%u: CONVERGECAST waiting %u root %u distance %u from %u #children %u\n",getGUID(),bfs->tree.broadConv.waiting, bfs->tree.root,bfs->tree.distance,GET_GUID_FROM_PORT(from),getBFSNumChildren(bfs));

  // update aggregates
  if (height > bfs->tree.broadConv.height) {
    bfs->tree.broadConv.height = height;
    bfs->tree.broadConv.farthest = from;
  }
  
  bfs->tree.broadConv.size += size;
  
  bfs->tree.broadConv.waiting--;
  
  bfs->tree.broadConv.callbacks.convergecastVisit(thisChunk);
  checkBFSConvergecastEnd(bfs);
}
