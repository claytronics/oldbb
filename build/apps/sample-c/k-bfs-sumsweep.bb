#include <stdlib.h> // min
#include "block.bbh"
#include "bbassert.bbh"
#include "bfsTree.bbh"

threaddef #define GET_MIN(a,b) ((a) < (b) ? (a) : (b))
threaddef #define GET_MAX(a,b) ((a) > (b) ? (a) : (b))
			       
#define __MY_FILENAME__ "k-bfs-sumsweep.bb"
			       
#define K_BFS_SUMSWEEP_DEBUG_PRINT(...) //printf(__VA_ARGS__)
#define GET_NUM_STEP(i) (i/3 + 1)
#define GET_ROLE(i) (i%3)
			       
threaddef #define DEBUG_SET_COLOR(c) setColor(c)

// BFS_GO_DATA_INDEX = 4
#define K_BFS_SUMSWEEP_BFS_HEADER_ITERATION_INDEX (BFS_GO_DATA_INDEX)

// BFS_BACK_DATA_INDEX = 6
#define K_BFS_SUMSWEEP_BFS_HEADER_MFAR_INDEX (BFS_BACK_DATA_INDEX)
#define K_BFS_SUMSWEEP_BFS_HEADER_CMIN_INDEX (BFS_BACK_DATA_INDEX+2)
	       
#define K_BFS_SUMSWEEP_SYS_SIZE_INDEX 0
#define K_BFS_SUMSWEEP_ITERATION_INDEX 1

#define K_BFS_SUMSWEEP_TIMEOUT_MS 600

// default
#define K_BFS_SUMSWEEP_K 10

// line,dumbbell: 3
//#define K_BFS_SUMSWEEP_K 3

// square: 5
//#define K_BFS_SUMSWEEP_K 5

// cube: 7
//#define K_BFS_SUMSWEEP_K 7

#define CENTER_VERSION

threadtype enum roleID_t {INITIATOR = 0, ROOT, CENTER, UNDEFINED_ROLE = 255};
threaddef #define UNDEFINED_ITERATION 255
threadtype typedef byte role_t;

threadtype typedef uint32_t longlongDistance_t;
#define MAX_LONGLONGDISTANCE UINT32_MAX

threadtype typedef uint16_t longDistance_t;
#define MAX_LONGDISTANCE UINT16_MAX

threadtype typedef struct _KBFSSumSweep_t {byte iteration; byte candidate; longDistance_t farness; longDistance_t centrality; longDistance_t childrenMaxCandidateFar[NUM_PORTS];  longDistance_t childrenMinCentrality[NUM_PORTS]; byte numCandidates; byte electing;} KBFSSumSweep_t;

threadtype typedef struct KBFSSumSweepLocalLog {role_t role; byte iteration; Time start; Time end; byte sent;} KBFSSumSweepLocalLog_t;

threadextern Chunk* thisChunk;

threadvar BFSTraversal_t* _bfs = NULL;
threadvar KBFSSumSweep_t kbfs;

threadvar KBFSSumSweepLocalLog_t localLog;

threadvar Timeout bfsTimeOut;

static void initKBFSSumSweep(void);
static void updateCentrality(void);

// ABC2 callbacks
static void InitiatorElected(BFSTraversal_t *bfs);
static void centerElected(void);

static void startElection(void);
static void resetElection(void);
static void scheduleElection(void);
static void initElection(void);
static void kbfsHandleNeighborChange(void);

// BFS handlers:
static void bfsReset(void);
static void bfsGoVisit(Chunk *c);
static void bfsAddChild(Chunk *c);
static void bfsRemoveChild(Chunk *c);
static void fillBFSGoData(byte *data, byte index);
static void fillBFSBackData(byte *data, byte index);
static void bfsTerminationHandler(BFSTraversal_t *bfs);

// msg handlers
static byte handle_NEXT(void);
static byte handle_ELECTED(void);
static byte handle_ELECTION_END(void);

// msg senders
static void sendKBFSSumSweepMsg(PRef p, byte *data, byte size, MsgHandler handler);
static void sendNext(PRef p);
static void sendElected(PRef p);
static void broadcastElectionEnd(PRef ignore);

// tools/utils
static longDistance_t argmin(longDistance_t *d);
static longDistance_t argmax(longDistance_t *d);
static void packLongDistance(longDistance_t d, byte *data);
static longDistance_t unpackLongDistance(byte *data);

// debug and benchmark
static void debugSetRole(role_t role, byte iteration);
static void sendLocalLog(void);

static void initKBFSSumSweep(void) {
  PRef p = 0;
  
  DEBUG_SET_COLOR(WHITE);
  
  kbfs.iteration = 0;
  kbfs.candidate = 1;
  kbfs.farness = 0;
  kbfs.centrality = 0;
  kbfs.numCandidates = 0;
  kbfs.electing = 0;
  
  for (p = 0; p < NUM_PORTS; p++) {
    kbfs.childrenMaxCandidateFar[p] = 0;
    kbfs.childrenMinCentrality[p] = MAX_LONGDISTANCE;
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

void updateCentrality(void) {
  
  longDistance_t d = _bfs->tree.distance;

  kbfs.farness += d;

#ifdef CENTER_VERSION
  kbfs.centrality = GET_MAX(kbfs.centrality,d);
#else
  kbfs.centrality = kbfs.farness;
#endif
}

/** K-BFS-SUMSWEEP callbacks **/

static void endElection(void) {
  bbassert(kbfs.electing);
  kbfs.electing = 0;
}

static void startElection(void) {
  if (!kbfs.electing) {
    byte d = getNeighborCount();
    
    K_BFS_SUMSWEEP_DEBUG_PRINT("%u: start K-BFS-SUMSWEEP!\n",getGUID());
    deregisterTimeout(&bfsTimeOut);
    resetElection();
    kbfs.electing = 1;
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
}

static void resetElection(void) { // called to start a fresh election
  initKBFSSumSweep();
}

static void scheduleElection(void) {
   if (!kbfs.electing) {
     deregisterTimeout(&bfsTimeOut);
     bfsTimeOut.calltime = getTime() + K_BFS_SUMSWEEP_TIMEOUT_MS;
     registerTimeout(&bfsTimeOut);
   }
}

static void kbfsHandleNeighborChange(void) {
  if (!kbfs.electing) {
    K_BFS_SUMSWEEP_DEBUG_PRINT("%u: neighbor change!\n",getGUID());
    scheduleElection();
  } else {
    // else aie ... (case not handled yet!)
    bbassert(0);
  }
}

static void bfsTerminationHandler(BFSTraversal_t *bfs) {

  if (kbfs.iteration == 0) {
    bfs->electing = 0;
    localLog.end = getTime();
    bfs->tree.broadConv.callbacks.terminationHandler = (BFSStatusHandler) &InitiatorElected;   
    startBFSBroadcast(bfs,1);
    return;
  }
  
  K_BFS_SUMSWEEP_DEBUG_PRINT("### %u BFS terminated ###\n", getGUID());
  
  if (kbfs.iteration + 1 == K_BFS_SUMSWEEP_K ||
      kbfs.iteration + 1 == bfs->sysSize) {

    PRef next = argmin(kbfs.childrenMinCentrality);
    
    if (next == UNDEFINED_PORT || kbfs.centrality < kbfs.childrenMinCentrality[next]) {
      endElection();
      centerElected();
    } else {
      K_BFS_SUMSWEEP_DEBUG_PRINT("%u a winner need to be contacted (centrality: %u)\n", getGUID(), kbfs.childrenMinCentrality[next]);
      sendElected(next);
    }
  } else {
    PRef next = argmax(kbfs.childrenMaxCandidateFar);
    bbassert(next != UNDEFINED_PORT);
    sendNext(next);
  }
}

static void InitiatorElected(BFSTraversal_t *bfs) {
  
  debugSetRole(INITIATOR,kbfs.iteration);
  bfs->sysSize = bfs->tree.broadConv.size;
  kbfs.candidate = 0;
  
  K_BFS_SUMSWEEP_DEBUG_PRINT("%u: size: %u, height: %u\n", getGUID(), bfs->sysSize, bfs->tree.broadConv.height);

  // check size
  if (bfs->sysSize == 1 || K_BFS_SUMSWEEP_K == 1) {
    endElection();
    centerElected();
  } else {
    sendNext(bfs->tree.broadConv.farthest);
  }
}

static void centerElected(void) {
  debugSetRole(CENTER,kbfs.iteration);

  localLog.end = getTime();
  kbfs.electing = 0;
  broadcastElectionEnd(UNDEFINED_PORT);
}

/** BFS Handlers **/

static void bfsReset(void) {
  PRef p = 0;
 
  for (p = 0; p < NUM_PORTS; p++) {
      kbfs.childrenMaxCandidateFar[p] = 0;
      kbfs.childrenMinCentrality[p] = MAX_LONGDISTANCE;
  }
}

static void bfsGoVisit(Chunk *c) {
  byte i = c->data[K_BFS_SUMSWEEP_BFS_HEADER_ITERATION_INDEX];

  if (!kbfs.electing) {
    if (i != 0) {return;} // restart only if this is not an old message
    startElection();
  }
  
  if (i > kbfs.iteration) {    
    kbfs.iteration = i;    
    updateCentrality();
    
    if (i == 1) {
      _bfs->electing = 0;
    }

    resetBFSTraversal(_bfs);
    takePartInBFS(c);
  }
}

static void bfsAddChild(Chunk *c) {
  PRef p = faceNum(c);

  longDistance_t _f = unpackLongDistance(&(c->data[K_BFS_SUMSWEEP_BFS_HEADER_MFAR_INDEX]));
  longDistance_t _c =  unpackLongDistance(&(c->data[K_BFS_SUMSWEEP_BFS_HEADER_CMIN_INDEX]));
  
  kbfs.childrenMaxCandidateFar[p] = _f;
  kbfs.childrenMinCentrality[p] = _c;
}

static void bfsRemoveChild(Chunk *c) {
  PRef p = faceNum(c);

  kbfs.childrenMaxCandidateFar[p] = 0;
  kbfs.childrenMinCentrality[p] = MAX_LONGDISTANCE;
}

static void fillBFSGoData(byte *data, byte index) {
  data[K_BFS_SUMSWEEP_BFS_HEADER_ITERATION_INDEX] = kbfs.iteration;
}

static void fillBFSBackData(byte *data, byte index) {
  longDistance_t f = 0, c = MAX_LONGDISTANCE;
  PRef vf = UNDEFINED_PORT, vc = UNDEFINED_PORT;
  longDistance_t d = _bfs->tree.distance;

  if (kbfs.candidate) {
    f = kbfs.farness + d;
  }

  vf = argmax(kbfs.childrenMaxCandidateFar);
  if (vf != UNDEFINED_PORT)
    f = GET_MAX(kbfs.childrenMaxCandidateFar[vf],f);

  
#ifdef CENTER_VERSION
  c = GET_MAX(kbfs.centrality,d);
#else
  c = kbfs.centrality + d;
#endif
  vc = argmin(kbfs.childrenMinCentrality);
  if (vc != UNDEFINED_PORT)
    c = GET_MIN(kbfs.childrenMinCentrality[vc],c);

  packLongDistance(f,&(data[K_BFS_SUMSWEEP_BFS_HEADER_MFAR_INDEX]));
  packLongDistance(c,&(data[K_BFS_SUMSWEEP_BFS_HEADER_CMIN_INDEX]));
}

/** Msg handlers **/
static byte handle_NEXT(void) {  
  PRef p = UNDEFINED_PORT;
  
  _bfs->sysSize =  thisChunk->data[K_BFS_SUMSWEEP_SYS_SIZE_INDEX];
  
  if (kbfs.iteration == 0) { // initiator just elected
    p = _bfs->tree.broadConv.farthest;
  } else {
    p = argmax(kbfs.childrenMaxCandidateFar);
  }
  
  if (p == UNDEFINED_PORT) {
    
    bbassert(kbfs.candidate);

    kbfs.candidate = 0;
    kbfs.iteration++;
    updateCentrality();
 
    debugSetRole(ROOT,kbfs.iteration);
    
    resetBFSTraversal(_bfs);
    setBFSRoot(_bfs);
    startBFSTraversal(_bfs);
  
  } else {
    sendNext(p);
  }

  return 1;
}

static byte handle_ELECTED(void) {
  PRef next = argmin(kbfs.childrenMinCentrality);

  updateCentrality();

  K_BFS_SUMSWEEP_DEBUG_PRINT("%u handle_ELECTED, centrality: %u\n", getGUID(), kbfs.centrality);
  
  if (next == UNDEFINED_PORT || kbfs.centrality < kbfs.childrenMinCentrality[next]) {
    endElection();
    centerElected();
  } else {
    sendElected(next);
  }

  return 1;
}

static byte handle_ELECTION_END(void) {
  if (kbfs.electing == 1) {
    PRef p = faceNum(thisChunk);
    endElection();
    broadcastElectionEnd(p);
  }
  
  return 1;
}

/** msg senders **/

static void sendKBFSSumSweepMsg(PRef p, byte *data, byte size, MsgHandler handler) {
  sendUserMessage(p, data, size, handler, (GenericHandler)&defaultUserMessageCallback);
}

static void sendNext(PRef p) {
  int size = 1;
  byte data[1];

  data[K_BFS_SUMSWEEP_SYS_SIZE_INDEX] = _bfs->sysSize;
  
  sendKBFSSumSweepMsg(p,data,size,(MsgHandler)&handle_NEXT);
}

static void sendElected(PRef p) {
  int size = 1;
  byte data[1]; // empty message ok? to check in firmware code
  data[0] = 0;
  
  sendKBFSSumSweepMsg(p,data,size,(MsgHandler)&handle_ELECTED);
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
  initKBFSSumSweep();
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
  registerHandler(EVENT_NEIGHBOR_CHANGE, (GenericHandler)&kbfsHandleNeighborChange);
}

/** Tools **/
longDistance_t argmin(longDistance_t* d) {
  PRef p = 0, mp = 0;
  for (p = 1; p < NUM_PORTS; p++) {
    if (d[p] < d[mp]) {
      mp = p;
    }
  }

  if (d[mp] == MAX_LONGDISTANCE) {
    mp = UNDEFINED_PORT;
  }
  
  return mp;
}

longDistance_t argmax(longDistance_t *d) {
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

void packLongDistance(longDistance_t d, byte *data) {
  data[0] = (d >> 8) & 0x00FF;
  data[1] = (d & 0x00FF); 
}

longDistance_t unpackLongDistance(byte *data) {
  longDistance_t d;

  d = (longDistance_t)(data[0]) << 8;
  d |= data[1];

  return d;
}

// debug and benchmark
void debugSetRole(role_t role, byte iteration) {

  localLog.iteration = iteration;
  localLog.role = role;
  
  switch(role) {
  case INITIATOR:
    DEBUG_SET_COLOR(BROWN);
    K_BFS_SUMSWEEP_DEBUG_PRINT("#### %u: Initiator elected! ####\n",getGUID());
    break;
  case ROOT:
    DEBUG_SET_COLOR(YELLOW);
    K_BFS_SUMSWEEP_DEBUG_PRINT("#### %u: Root %u elected! ####\n",getGUID(),iteration);
    break;
  case CENTER:
    DEBUG_SET_COLOR(RED);
    K_BFS_SUMSWEEP_DEBUG_PRINT("#### %u: CENTER ELECTED ####\n",getGUID());
    break;
  default:
    DEBUG_SET_COLOR(BLUE);
    break;
  }
}

static void sendLocalLog(void) {
  char s[50];
  byte r = localLog.role;
  
  if (r == CENTER) {
    snprintf(s, 49*sizeof(char), "r: %u, s: %u, t: %lu", r, GET_NUM_STEP(localLog.iteration), (long unsigned int) (localLog.end - localLog.start));
    s[49] = '\0';
#ifdef BBSIM
    printf("%u: %s\n", getGUID(),s);
#else
    printDebug(s);
#endif
  }
}
