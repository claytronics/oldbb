#include <stdlib.h> // min
#include "block.bbh"
#include "bbassert.bbh"
#include "bfsTree.bbh"

threaddef #define GET_MIN(a,b) ((a) < (b) ? (a) : (b))
threaddef #define GET_MAX(a,b) ((a) > (b) ? (a) : (b))
			       
#define __MY_FILENAME__ "abccenterV2.bbh"
			       
#define ABC_CENTER_V2_DEBUG_PRINT(...) //printf(__VA_ARGS__)
#define GET_NUM_STEP(i) (i/3 + 1)
#define GET_ROLE(i) (i%3)
#define LOCAL_GRADIENT GET_MAX(abc2.distancesBC.B,abc2.distancesBC.C)
			       
threaddef #define DEBUG_SET_COLOR(c) setColor(c)

#define ABC_CENTER_V2_BFS_HEADER_ITERATION_INDEX (BFS_GO_DATA_INDEX)

#define ABC_CENTER_V2_BFS_HEADER_FARTHEST_INDEX (BFS_BACK_DATA_INDEX+1)
#define ABC_CENTER_V2_BFS_HEADER_GMIN_INDEX (BFS_BACK_DATA_INDEX+2)

#define ABC_CENTER_V2_BC_GMIN_INDEX (BFS_BROADCAST_DATA_INDEX)

#define ABC_CENTER_V2_BC_NUM_CANDIDATES_INDEX (BFS_CONV_DATA_INDEX)
			       
#define ABC_CENTER_V2_SYS_SIZE_INDEX 0
#define ABC_CENTER_V2_ITERATION_INDEX 1

#define ABC_CENTER_V2_TIMEOUT_MS 600

threadtype enum roleID_t {A = 0, B, C, CENTER, UNDEFINED_ROLE = 255};
threaddef #define UNDEFINED_ITERATION 255
threadtype typedef byte role_t;

threadtype typedef struct _DistancesBC_t {distance_t B; distance_t C;} DistancesBC_t;
threadtype typedef struct _ABCCenterV2_t {byte iteration; byte candidate; DistancesBC_t distancesBC; byte childrenMaxCandidateDistance[NUM_PORTS];  byte childrenMinCandidateGradient[NUM_PORTS]; byte numCandidates; byte electing;} ABCCenterV2_t;

threadtype typedef struct ABCCenterV2LocalLog {role_t role; byte iteration; Time start; Time end; byte sent;} ABCCenterV2LocalLog_t;

threadextern Chunk* thisChunk;

threadvar BFSTraversal_t* _bfs = NULL;
threadvar ABCCenterV2_t abc2;

threadvar ABCCenterV2LocalLog_t localLog;

threadvar Timeout bfsTimeOut;

static void initABCCenterV2(void);
static void updateDistancesBC(void);

// ABC2 callbacks
static void A1_Elected(BFSTraversal_t *bfs);
static void centerElected(void);

static void startElection(void);
static void resetElection(void);
static void scheduleElection(void);
static void initElection(void);
static void abc2HandleNeighborChange(void);

// BFS handlers:
static void bfsReset(void);
static void bfsGoVisit(Chunk *c);
static void bfsAddChild(Chunk *c);
static void bfsRemoveChild(Chunk *c);
static void fillBFSGoData(byte *data, byte index);
static void fillBFSBackData(byte *data, byte index);
static void bfsTerminationHandler(BFSTraversal_t *bfs);

// BC (broadcast convergecast) handlers:
static void resetBCData(void);
static void bcBroadcastVisit(Chunk *c);
static void bcConvergecastVisit(Chunk *c);
static void bcFillBroadcastData(byte *data, byte l);
static void bcFillConvergecastData(byte *data, byte l);
static void bcTerminationHandler(BFSTraversal_t *bfs);

// msg handlers
static byte handle_NEXT(void);
static byte handle_ELECTED(void);
static byte handle_ELECTION_END(void);

// msg senders
static void sendABCCenterV2Msg(PRef p, byte *data, byte size, MsgHandler handler);
static void sendNext(PRef p);
static void sendElected(PRef p);
static void broadcastElectionEnd(PRef ignore);

// tools/utils
static byte argmin(byte* d);
static byte argmax(byte *d);

// debug and benchmark
static void debugSetRole(role_t role, byte iteration);
static void sendLocalLog(void);

static void initABCCenterV2(void) {
  PRef p = 0;
  
  DEBUG_SET_COLOR(WHITE);
  
  abc2.iteration = 0;
  abc2.candidate = 1;
  abc2.distancesBC.B = 0;
  abc2.distancesBC.C = 0;
  abc2.numCandidates = 0;
  abc2.electing = 0;
  
  for (p = 0; p < NUM_PORTS; p++) {
    abc2.childrenMaxCandidateDistance[p] = 0;
    abc2.childrenMinCandidateGradient[p] = 0;
  }

  localLog.iteration = UNDEFINED_ITERATION;
  localLog.role = UNDEFINED_ROLE;
  localLog.start = 0;
  localLog.end = 0;
  localLog.sent = 0;
  
  // init BFS
  resetBFSTraversal(_bfs);
  
  _bfs->electing = 1;
  _bfs->sysSize = 0;
  setBFSRoot(_bfs);
  
  _bfs->callbacks.terminationHandler = (BFSStatusHandler) &bfsTerminationHandler;
  
}

void updateDistancesBC(void) {
  role_t r = GET_ROLE(abc2.iteration);

  if (r == B) {
    abc2.distancesBC.B = _bfs->tree.distance;
  }
  if (r == C) {
    abc2.distancesBC.C = _bfs->tree.distance;
  }
}

/** ABC2 callbacks **/

static void endElection(void) {
  bbassert(abc2.electing);
  abc2.electing = 0;
}

static void startElection(void) {
  if (!abc2.electing) {
    byte d = getNeighborCount();
    
    ABC_CENTER_V2_DEBUG_PRINT("%u: start ABC-Center!\n",getGUID());
    deregisterTimeout(&bfsTimeOut);
    resetElection();
    abc2.electing = 1;
    localLog.start = getTime();

    if (d == 0) {
      endElection();
      centerElected();
    } else {
      startBFSTraversal(_bfs);
    }
  }
}

static void initElection(void) { // called once

  bfsTimeOut.calltime = 0;
  bfsTimeOut.callback = (GenericHandler)(&startElection);
  
  _bfs = malloc(sizeof(BFSTraversal_t));

  bbassert(_bfs);
  
  initBFSTraversal(_bfs);
  registerBFSTraversal(_bfs);

  _bfs->callbacks.reset = (BFSGenericHandler) &bfsReset;
  _bfs->callbacks.bfsGoVisit = (BFSChildHandler) &bfsGoVisit;
  _bfs->callbacks.addChild = (BFSChildHandler) &bfsAddChild;
  _bfs->callbacks.removeChild = (BFSChildHandler) &bfsRemoveChild;
  
  _bfs->callbacks.fillBFSGoData = (BFSDataHandler) &fillBFSGoData;
  _bfs->callbacks.fillBFSBackData = (BFSDataHandler) &fillBFSBackData;

  _bfs->tree.broadConv.callbacks.broadcastVisit = (BFSChildHandler) &bcBroadcastVisit;
  _bfs->tree.broadConv.callbacks.convergecastVisit = (BFSChildHandler) &bcConvergecastVisit;
  _bfs->tree.broadConv.callbacks.fillBroadcastData = (BFSDataHandler) &bcFillBroadcastData;
  _bfs->tree.broadConv.callbacks.fillConvergecastData = (BFSDataHandler) &bcFillConvergecastData;
}

static void resetElection(void) { // called to start a fresh election
  initABCCenterV2();
}

static void scheduleElection(void) {
   if (!abc2.electing) {
     deregisterTimeout(&bfsTimeOut);
     bfsTimeOut.calltime = getTime() + ABC_CENTER_V2_TIMEOUT_MS;
     registerTimeout(&bfsTimeOut);
   }
}

static void abc2HandleNeighborChange(void) {
  if (!abc2.electing) {
    ABC_CENTER_V2_DEBUG_PRINT("%u: neighbor change!\n",getGUID());
    scheduleElection();
  } else {
    // else aie ... (case not handled yet!)
    bbassert(0);
  }
}

static void bfsTerminationHandler(BFSTraversal_t *bfs) {
  role_t r = 0;
  
  ABC_CENTER_V2_DEBUG_PRINT("### %u BFS terminated ###\n", getGUID());

  updateDistancesBC();
  
  if (abc2.iteration == 0) {
    _bfs->electing = 0;
    localLog.end = getTime();
    bfs->tree.broadConv.callbacks.terminationHandler = (BFSStatusHandler) &A1_Elected;   
    startBFSBroadcast(bfs,1);
    return;
  }

  // A and B:
  r = GET_ROLE(abc2.iteration);
  if (r == A || r == B) {
    PRef next = argmax(abc2.childrenMaxCandidateDistance);
    bbassert(next != UNDEFINED_PORT);
    sendNext(next);
  } else {  // C:
    bfs->tree.broadConv.callbacks.terminationHandler = (BFSStatusHandler)&bcTerminationHandler;
    startBFSBroadcast(bfs,1);
  }
  
}

static void A1_Elected(BFSTraversal_t *bfs) {
  
  debugSetRole(A,abc2.iteration);
  
  bfs->sysSize = bfs->tree.broadConv.size;

  ABC_CENTER_V2_DEBUG_PRINT("%u: size: %u, height: %u\n", getGUID(), bfs->sysSize, bfs->tree.broadConv.height);
  
  // check size
  if (bfs->sysSize < 3) {
    centerElected();
  } else {
    sendNext(bfs->tree.broadConv.farthest);
  }
}

static void centerElected(void) {
  debugSetRole(CENTER,abc2.iteration);

  localLog.end = getTime();
  abc2.electing = 0;
  broadcastElectionEnd(UNDEFINED_PORT);
}

/** BFS Handlers **/

static void bfsReset(void) {
  PRef p = 0;
 
  for (p = 0; p < NUM_PORTS; p++) {
      abc2.childrenMaxCandidateDistance[p] = 0;
      abc2.childrenMinCandidateGradient[p] = MAX_DISTANCE;
  }
}

static void bfsGoVisit(Chunk *c) {
  byte i = c->data[ABC_CENTER_V2_BFS_HEADER_ITERATION_INDEX];

  if (!abc2.electing) {
    if (i != 0) {return;} // restart only if this is not an old message
    startElection();
  }
  
  if (i > abc2.iteration) {    
    abc2.iteration = i;
    ABC_CENTER_V2_DEBUG_PRINT("%u : %u iteration\n", getGUID(), abc2.iteration);
    
    if (i == 1) {
      _bfs->electing = 0;
    }

    resetBFSTraversal(_bfs);
    takePartInBFS(c);
  }
}

static void bfsAddChild(Chunk *c) {
  PRef p = faceNum(c);
  distance_t f = c->data[ABC_CENTER_V2_BFS_HEADER_FARTHEST_INDEX];
  distance_t g = c->data[ABC_CENTER_V2_BFS_HEADER_GMIN_INDEX];

  abc2.childrenMaxCandidateDistance[p] = f;
  abc2.childrenMinCandidateGradient[p] = g;
}

static void bfsRemoveChild(Chunk *c) {
  PRef p = faceNum(c);

  abc2.childrenMaxCandidateDistance[p] = 0;
  abc2.childrenMinCandidateGradient[p] = MAX_DISTANCE;
}

static void fillBFSGoData(byte *data, byte index) {
  data[ABC_CENTER_V2_BFS_HEADER_ITERATION_INDEX] = abc2.iteration;
}

static void fillBFSBackData(byte *data, byte index) {
  distance_t f = 0, g = MAX_DISTANCE;
  PRef vf = UNDEFINED_PORT, vg = UNDEFINED_PORT;

  updateDistancesBC();
  
  if (abc2.candidate) {
    f = _bfs->tree.distance;
    g = LOCAL_GRADIENT;
  }

  ABC_CENTER_V2_DEBUG_PRINT("%u gradient = %u, candidate = %u\n", getGUID(), g, abc2.candidate);

  vf = argmax(abc2.childrenMaxCandidateDistance);
  if (vf != UNDEFINED_PORT)
    f = GET_MAX(abc2.childrenMaxCandidateDistance[vf],f);
  
  vg = argmin(abc2.childrenMinCandidateGradient);
  if (vg != UNDEFINED_PORT)
    g = GET_MIN(abc2.childrenMinCandidateGradient[vg],g);
  
  data[ABC_CENTER_V2_BFS_HEADER_FARTHEST_INDEX] = f;
  data[ABC_CENTER_V2_BFS_HEADER_GMIN_INDEX] = g;
}

/** Broadcast/Convergecast Handlers **/

void resetBCData(void) {  
  if (abc2.candidate) {
    abc2.numCandidates = 1;
  } else {
    abc2.numCandidates = 0;
  }
}

static void bcBroadcastVisit(Chunk *c) {
  distance_t g = c->data[ABC_CENTER_V2_BC_GMIN_INDEX];

  resetBCData();
  
  if (LOCAL_GRADIENT > g) {
    abc2.candidate = 0; abc2.numCandidates = 0;
  }

  ABC_CENTER_V2_DEBUG_PRINT("%u mingradient = %u, gradient = %u, candidate = %u, candidates = %u\n", getGUID(), g, LOCAL_GRADIENT, abc2.candidate, abc2.numCandidates);
  
}

static void bcConvergecastVisit(Chunk *c) {
  sysSize_t s = c->data[ABC_CENTER_V2_BC_NUM_CANDIDATES_INDEX];
  abc2.numCandidates += s;
}

static void bcFillBroadcastData(byte *data, byte l) {
  // nothing
  PRef p = argmin(abc2.childrenMinCandidateGradient);
  bbassert(p != UNDEFINED_PORT);
  
  data[ABC_CENTER_V2_BC_GMIN_INDEX] = abc2.childrenMinCandidateGradient[p];
  resetBCData();
}

static void bcFillConvergecastData(byte *data, byte l) {
  data[ABC_CENTER_V2_BC_NUM_CANDIDATES_INDEX] = abc2.numCandidates;
}

static void bcTerminationHandler(BFSTraversal_t *bfs) {
  PRef nextHopToGMin = argmin(abc2.childrenMinCandidateGradient);

  bbassert(nextHopToGMin != UNDEFINED_PORT);

  ABC_CENTER_V2_DEBUG_PRINT("%u : num candidates = %u, next hop = %u,\n", getGUID(), abc2.numCandidates, nextHopToGMin);

  if (abc2.numCandidates > 2) {
    sendNext(nextHopToGMin);
  } else {
    ABC_CENTER_V2_DEBUG_PRINT("%u a winner need to be contacted\n", getGUID());
    sendElected(nextHopToGMin);
  }
}

/** Msg handlers **/
static byte handle_NEXT(void) {  
  PRef p = argmax(abc2.childrenMaxCandidateDistance);
  
  _bfs->sysSize =  thisChunk->data[ABC_CENTER_V2_SYS_SIZE_INDEX];
  
  if (abc2.iteration == 0) { // A1
    p = _bfs->tree.broadConv.farthest;
  } else if (GET_ROLE(abc2.iteration) == C) {
    
    p = argmin(abc2.childrenMinCandidateGradient);
    
    if (p != UNDEFINED_PORT && abc2.childrenMinCandidateGradient[p] > LOCAL_GRADIENT) {
      p = UNDEFINED_PORT;
    }
    
    ABC_CENTER_V2_DEBUG_PRINT("%u : forward to %u gradient  %u\n", getGUID(), p, abc2.childrenMinCandidateGradient[p]);
  }
  
  if (p == UNDEFINED_PORT) {

    bbassert(abc2.candidate);
    
    abc2.iteration++;
    debugSetRole(GET_ROLE(abc2.iteration),abc2.iteration);

    if (GET_ROLE(abc2.iteration) != 0) { // B or C
      abc2.candidate = 0;
    }

    resetBFSTraversal(_bfs);
    setBFSRoot(_bfs);
    startBFSTraversal(_bfs);
  
  } else {
    sendNext(p);
  }

  return 1;
}

static byte handle_ELECTED(void) {
  if (abc2.candidate == 1) {
    endElection();
    centerElected();
  } else {
    PRef p = argmin(abc2.childrenMinCandidateGradient);
    sendElected(p);
  }

  return 1;
}

static byte handle_ELECTION_END(void) {
  if (abc2.electing == 1) {
    PRef p = faceNum(thisChunk);
    endElection();
    broadcastElectionEnd(p);
  }
  
  return 1;
}

/** msg senders **/

static void sendABCCenterV2Msg(PRef p, byte *data, byte size, MsgHandler handler) {
  sendUserMessage(p, data, size, handler, (GenericHandler)&defaultUserMessageCallback);
}

static void sendNext(PRef p) {
  int size = 1;
  byte data[1];

  data[ABC_CENTER_V2_SYS_SIZE_INDEX] = _bfs->sysSize;
  
  sendABCCenterV2Msg(p,data,size,(MsgHandler)&handle_NEXT);
}

static void sendElected(PRef p) {
  int size = 1;
  byte data[1]; // empty message ok? to check in firmware code
  data[0] = 0;
  
  sendABCCenterV2Msg(p,data,size,(MsgHandler)&handle_ELECTED);
}

static void broadcastElectionEnd(PRef ignore) {
  int size = 1;
  byte data[1]; // 0-byte message could be ok. But ok in firmware?
                    // to check!
  data[0] = 0;
  broadcastUserMessage(ignore,data,size,(MsgHandler)&handle_ELECTION_END, (GenericHandler)&defaultUserMessageCallback);
}

/** MAIN **/
void myMain(void) {
  
  initElection();
  initABCCenterV2();
  scheduleElection();
  
  while (1) {

#ifdef BBSIM
    if (localLog.iteration != UNDEFINED_ITERATION && !localLog.sent) {
      sendLocalLog();
      localLog.sent = 1;
    }
#else
    
#ifdef LOG_DEBUG
#ifdef NON_BLOCKING_LOGGING_SYSTEM
    if (localLog.iteration != UNDEFINED_ITERATION && !localLog.sent) {
      if (isConnectedToLog()) {
	sendLocalLog();
	localLog.sent = 1;
      }
    }
#endif
#endif
#endif
    delayMS(50);
  }
}

void userRegistration(void) {
  registerHandler(SYSTEM_MAIN, (GenericHandler)&myMain);
  registerHandler(EVENT_NEIGHBOR_CHANGE, (GenericHandler)&abc2HandleNeighborChange);
}

/** Tools **/
byte argmin(byte* d) {
  PRef p = 0, mp = 0;
  for (p = 1; p < NUM_PORTS; p++) {
    if (d[p] < d[mp]) {
      mp = p;
    }
  }

  if (d[mp] == MAX_DISTANCE) {
    mp = UNDEFINED_PORT;
  }
  
  return mp;
}

byte argmax(byte *d) {
  PRef p = 0, mp = 0;
  for (p = 1; p < NUM_PORTS; p++) {
    if (d[p] > d[mp]) {
      mp = p;
    }
  }

  if (d[mp] == 0) {
    mp = UNDEFINED_PORT;
  }
  
  return mp;
}

// debug and benchmark
void debugSetRole(role_t role, byte iteration) {

  localLog.iteration = iteration;
  localLog.role = role;
  
  switch(role) {
  case A:
    DEBUG_SET_COLOR(BROWN);
    ABC_CENTER_V2_DEBUG_PRINT("#### %u: A%u elected! ####\n",getGUID(),GET_NUM_STEP(iteration));
    break;
  case B:
    DEBUG_SET_COLOR(BLUE);
    ABC_CENTER_V2_DEBUG_PRINT("#### %u: B%u elected! ####\n",getGUID(),GET_NUM_STEP(iteration));
    break;
  case C:
    DEBUG_SET_COLOR(GREEN);
    ABC_CENTER_V2_DEBUG_PRINT("#### %u: C%u elected! ####\n",getGUID(),GET_NUM_STEP(iteration));
    break;
  case CENTER:
    DEBUG_SET_COLOR(RED);
    ABC_CENTER_V2_DEBUG_PRINT("#### %u: CENTER ELECTED ####\n",getGUID());
    break;
  default:
    DEBUG_SET_COLOR(YELLOW);
    break;
  }
}

static void sendLocalLog(void) {
  char s[50];
  byte r = localLog.role;
  
  if (r == A || r == CENTER) {
    snprintf(s, 49*sizeof(char), "r: %u, s: %u, t: %lu", r, GET_NUM_STEP(localLog.iteration), (long unsigned int) (localLog.end - localLog.start));
    s[49] = '\0';
#ifdef BBSIM
    printf("%u: %s\n", getGUID(),s);
#else
    printDebug(s);
#endif
  }
}
