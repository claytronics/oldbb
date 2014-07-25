//#include "bb.h"
#include "../system/led.bbh"
#include "../system/ensemble.bbh"
#include "../system/accelerometer.bbh"
#include "../system/message.bbh"
#include "../hw-api/hwMemory.h"
#include "../system/myassert.h"

#ifdef BBSIM
#include <unistd.h>
#include <sys/types.h>
#else
#endif

#include "../system/defs.bbh"

#include "set_runtime.h"
#include "list_runtime.h"

//#include "util.h"

#include "core.h"
#include "api.h"
// include here to make sure we include the threadypes, etc.
#include "model.bbh"

void vm_init(void);
byte updateRuleState(byte rid);

threadvar tuple_t *oldTuples;

threadvar tuple_pqueue *delayedTuples;
threadvar tuple_queue *tuples;
threadvar tuple_pqueue *newStratTuples;
threadvar tuple_queue *newTuples;
threadvar tuple_queue receivedTuples[NUM_PORTS];

threadvar meld_int *proved;

threadvar NodeID blockId;

threadvar persistent_set *persistent;

threadvar Register reg[32];

static tuple_type TYPE_TAP = -1;

//#define DEBUG
/* #define DEBUG_NEIGHBORHOOD */
/* #define DEBUG_SEND */
#define DEBUG_RULES

#ifdef BBSIM
#include <sys/timeb.h>
#endif

static inline
NodeID get_neighbor_ID(int face)
{
  if (face == UP)
    return up();
  else if (face == DOWN)
    return down();
  else if (face == WEST)
    return west();
  else if (face == EAST)
    return east();
  else if (face == NORTH)
    return north();
  else if (face == SOUTH)
    return south();
  else {
    assert(0);
    return -1;
  }
}

void enqueueNewTuple(tuple_t tuple, record_type isNew)
{
  assert (TUPLE_TYPE(tuple) < NUM_TYPES);

  if (TYPE_IS_STRATIFIED(TUPLE_TYPE(tuple))) {
    /* pthread_mutex_lock(&(printMutex)); */
    /* fprintf(stderr, "\x1b[1;35m--%d--\tStrat enqueuing tuple ", getBlockId()); */
    /* tuple_print (tuple, stderr); */
    /* fprintf(stderr, "\x1b[0m\n"); */
    /* pthread_mutex_unlock(&(printMutex)); */
    p_enqueue(newStratTuples, 
	      TYPE_STRATIFICATION_ROUND(TUPLE_TYPE(tuple)), tuple, 0, isNew);
  }
  else {
    /* pthread_mutex_lock(&(printMutex)); */
    /* fprintf(stderr, "\x1b[1;35m--%d--\tBase enqueuing tuple ", getBlockId()); */
    /* tuple_print (tuple, stderr); */
    /* fprintf(stderr, "\x1b[0m\n"); */
    /* pthread_mutex_unlock(&(printMutex)); */
    queue_enqueue(newTuples, tuple, isNew);
  }
}

void enqueue_face(NodeID neighbor, meld_int face, int isNew)
{
  tuple_t tuple = NULL;

  if (neighbor <= 0) {
    tuple = tuple_alloc(TYPE_VACANT);
    SET_TUPLE_FIELD(tuple, 0, &face);
  }
  else {
    tuple = tuple_alloc(TYPE_NEIGHBOR);
    SET_TUPLE_FIELD(tuple, 0, &neighbor);
    SET_TUPLE_FIELD(tuple, 1, &face);
  }

  enqueueNewTuple(tuple, (record_type) isNew);
}

static
void enqueue_count(meld_int count, int isNew)
{
  tuple_t tuple = tuple_alloc(TYPE_NEIGHBORCOUNT);

  SET_TUPLE_FIELD(tuple, 0, &count);

  enqueueNewTuple(tuple, (record_type) isNew);
}

static
void enqueue_tap(void)
{
  tuple_t tuple = tuple_alloc(TYPE_TAP);

  enqueueNewTuple(tuple, (record_type) 1);

  /* #if DEBUG */
  facts_dump();
  /* #endif */
}

static
void enqueue_init(void)
{
  if(TYPE_INIT == -1)
    return;

  tuple_t tuple = tuple_alloc(TYPE_INIT);
  enqueueNewTuple(tuple, (record_type) 1);
}

static
void init_all_consts(void)
{
  init_consts();
  
  tuple_type i;
  for (i = 0; i < NUM_TYPES; i++) {
    if (strcmp(TYPE_NAME(i), "tap") == 0)
      TYPE_TAP = i;
    else if (strcmp(TYPE_NAME(i), "neighbor") == 0)
      TYPE_NEIGHBOR = i;
    else if ( (strcmp(TYPE_NAME(i), "neighborCount" ) == 0) ||
	      (strcmp(TYPE_NAME(i), "neighborcount" ) == 0) )
      TYPE_NEIGHBORCOUNT = i;
    else if (strcmp(TYPE_NAME(i), "vacant") == 0)
      TYPE_VACANT = i;
  }	
}

#ifdef BBSIM
pthread_mutex_t printMutex;
#endif

void meldMain(void)
{
  vm_init();

  //block initialization
#if DEBUG
  printf("meld program started\n");
#endif

  //setColor(0);
  setLED(128,0,128,32);

  enqueue_init();

  // introduce intial set of axioms
  NodeID neighbors[6];
  int numNeighbors = getNeighborCount();

  enqueue_count(numNeighbors, 1);
	
  int i;
  for (i = 0; i < NUM_PORTS; i++) {
    neighbors[i] = get_neighbor_ID(i);

    enqueue_face(neighbors[i], i, 1);
  }

  // loop forever, processing new facts and updating axioms
  while(1) {
    // loop for new facts to process

    if(!queue_is_empty(newTuples)) {
      int isNew = 0;
      tuple_t tuple = queue_dequeue(newTuples, &isNew);
      
      tuple_handle(tuple, isNew, reg);
    } else if (!p_empty(delayedTuples) 
	       && p_peek(delayedTuples)->priority <= getTime()) {
      tuple_pentry *entry = p_dequeue(delayedTuples);
      
      tuple_send(entry->tuple, entry->rt, 0, entry->records.count);
      free(entry);
    } else if (!(p_empty(newStratTuples))) {
      tuple_pentry *entry = p_dequeue(newStratTuples);
      tuple_handle(entry->tuple, entry->records.count, reg);
      
      free(entry);
    } else {
      /* Update rule state and process them */
      for (i = 0; i < NUM_RULES; ++i) {

	if (updateRuleState(i)) {
	  /* Set state byte used by DEBUG */
	  byte processState = PROCESS_RULE | (i << 4);
#ifdef DEBUG_RULES
	  if (!RULE_ISPERSISTENT(i))
	    printf ("\n\x1b[35m--%d--\tRule %d READY!\x1b[0m\n", getBlockId(), i);
#endif
	  /* Trigger execution */
	  process_bytecode (NULL, RULE_START(i), 1, reg, processState);
	}
	/* else: Rule not ready yet, will re-check at next loop run */
      }

      // if we've processed everything, sleep for the sake of letting other blocks run in the simulator
      delayMS(30);
    }

    updateAccel();

    // update axioms based upon any changes
    int newNumNeighbors = getNeighborCount();
    if (newNumNeighbors != numNeighbors) {
      enqueue_count(numNeighbors, -1);
      numNeighbors = newNumNeighbors;
      enqueue_count(numNeighbors, 1);			
    }

    for (i = 0; i < NUM_PORTS; i++) {
      NodeID neighbor = get_neighbor_ID(i);

      if (neighbor == neighbors[i])
	continue;

#ifdef DEBUG_NEIGHBORHOOD
      printf ("--%d--\tNew neighbor %d on face %d!\n",
	      blockId, neighbor, i);
#endif

      enqueue_face(neighbors[i], i, -1);

      /* Enqueue received tuples */
      while(!queue_is_empty(&(receivedTuples[i]))) {
	tuple_t tuple = queue_dequeue(&receivedTuples[i], NULL);
	enqueueNewTuple(tuple, (record_type) 1);
      }

      neighbors[i] = neighbor;
      enqueue_face(neighbors[i], i, 1);
    }
  }
}

void userRegistration(void)
{
  registerHandler(SYSTEM_MAIN, (GenericHandler)&meldMain);
  registerHandler(EVENT_ACCEL_TAP, (GenericHandler)&enqueue_tap);
}

#ifdef _LP64
static inline
void endian_swap(Register* x)
{
  *x = (*x>>56) | 
    ((*x<<40) & 0x00FF000000000000) |
    ((*x<<24) & 0x0000FF0000000000) |
    ((*x<<8)  & 0x000000FF00000000) |
    ((*x>>8)  & 0x00000000FF000000) |
    ((*x>>24) & 0x0000000000FF0000) |
    ((*x>>40) & 0x000000000000FF00) |
    (*x<<56);
}
#endif

void receive_tuple(int isNew)
{
  tuple_t rcvdTuple = (tuple_t)thisChunk->data;
  tuple_t tuple;
  size_t tuple_size = TYPE_SIZE(TUPLE_TYPE(rcvdTuple));
  
#ifdef DEBUG_SEND
#ifdef BBSIM
  pthread_mutex_lock(&(printMutex));
#endif
  printf ("\x1b[33m--%d--\t Tuple received from %d: ",
	  getBlockId(), get_neighbor_ID(faceNum(thisChunk)));
  tuple_print (rcvdTuple, stdout);
  printf("\x1b[0m\n"); 
#ifdef BBSIM
  pthread_mutex_unlock(&(printMutex));
#endif
#endif

  tuple = malloc(tuple_size);
  memcpy(tuple, rcvdTuple, tuple_size);
  queue_enqueue(&receivedTuples[faceNum(thisChunk)], tuple, (record_type)isNew);

  tuple = malloc(tuple_size);
  memcpy(tuple, rcvdTuple, tuple_size);
  enqueueNewTuple(tuple, (record_type)isNew);
}

void receive_tuple_delete(void)
{
  receive_tuple(-1);
}

void receive_tuple_add(void)
{
  receive_tuple(1);
}

void free_chunk(void) {
  free(thisChunk);
}

void tuple_send(tuple_t tuple, NodeID rt, meld_int delay, int isNew)
{
  assert (TUPLE_TYPE(tuple) < NUM_TYPES);

  if (delay > 0) {
    p_enqueue(delayedTuples, getTime() + delay, tuple, rt, (record_type) isNew);
    return;
  }

  NodeID target = rt;

#ifdef DEBUG_SEND
#ifdef BBSIM
  pthread_mutex_lock(&(printMutex));
#endif
  printf ("\x1b[33m--%d--\t Sending tuple: ", getBlockId());
  tuple_print (tuple, stdout);
  printf(" to %d\x1b[0m\n", target); 
#ifdef BBSIM
  pthread_mutex_unlock(&(printMutex));
#endif
#endif

  if (target == blockId) {
    enqueueNewTuple(tuple, (record_type) isNew);
  }
  else {
    int face = -1;

    if (target == up())
      face = UP;
    else if (target == down())
      face = DOWN;
    else if (target == west())
      face = WEST;
    else if (target == east())
      face = EAST;
    else if (target == north())
      face = NORTH;
    else if (target == south())
      face = SOUTH;

    if (face != -1) {		  
      Chunk *c=calloc(sizeof(Chunk), 1);
      MsgHandler receiver;

      if (isNew > 0) {
	receiver = (MsgHandler)receive_tuple_add;
      }
      else {
	receiver = (MsgHandler)receive_tuple_delete;
      }

      assert(TYPE_SIZE(TUPLE_TYPE(tuple)) <= 17);

      if (sendMessageToPort(c, face, tuple, TYPE_SIZE(TUPLE_TYPE(tuple)), (MsgHandler)receiver, (GenericHandler)&free_chunk) == 0) {
	// Send failed :(
	free(c);
	fprintf(stderr, "--%d--\tSEND FAILED EVEN THOUGH BLOCK IS PRESENT! TO %d\n", blockId, (int)target);
      };
    }
    else {
      fprintf(stderr, "--%d--\tUNABLE TO ROUTE MESSAGE! To %d\n", (int)blockId, (int)target);
      //exit(EXIT_FAILURE);
    }
    /* TODO-REAL: needs to free on real blinky blocks??? */
  }
}

/* Check if rule of ID rid is ready to be derived */
/* Returns 1 if true, 0 otherwise */
byte
updateRuleState(byte rid) 
{
  int i;
  for (i = 0; i < RULE_NUM_INCLPREDS(rid); ++i) {
    if (TUPLES[RULE_INCLPRED_ID(rid, i)].length == 0)
      return INACTIVE_RULE;
  }

  /* Rule is ready, enqueue it or process it rightaway */
  return ACTIVE_RULE;
}

void tuple_handle(tuple_t tuple, int isNew, Register *registers)
{
  #if DEBUG
  printf ("handling (%d): ", isNew);
  tuple_print(tuple, stdout);
  printf ("\n");
  #endif

  tuple_type type = TUPLE_TYPE(tuple);

  assert (type < NUM_TYPES);

  switch (type) {
  case TYPE_SETCOLOR:
    if (isNew <= 0) return;

    setLED(*(byte *)GET_TUPLE_FIELD(tuple, 1),
	   *(byte *)GET_TUPLE_FIELD(tuple, 2),
	   *(byte *)GET_TUPLE_FIELD(tuple, 3),
	   *(byte *)GET_TUPLE_FIELD(tuple, 4));
    FREE_TUPLE(tuple);
    return;

  case TYPE_SETCOLOR2:
    if (isNew <= 0) return;

    setColor(MELD_INT(GET_TUPLE_FIELD(tuple, 1)) % NUM_COLORS);
    FREE_TUPLE(tuple);
    return;

  default:
    tuple_do_handle(type, tuple, isNew, registers);
    return;
  }
}

void
setColorWrapper (byte color) { setColor (color % NUM_COLORS);}

void
setLEDWrapper (byte r, byte g, byte b, byte intensity) 
{ setLEDWrapper (r, g, b, intensity);}

NodeID 
getBlockId (void) { return blockId;}

#ifdef BBSIM
extern int alreadyExecuted(int flag);
#endif

/* NOTE: this must be executed before running the virtual machine */
void
vm_init(void)
{
#ifdef BBSIM
  // We only want to do this once per executable, so in simulator make sure it happens only once and everyone else waits for it to complete.

  if (alreadyExecuted(0)) return;
#endif

  fprintf(stderr, "In VM_init\n");

  init_all_consts();
  init_fields();
  set_init_descriptors();
  list_init_descriptors();
#if DEBUG
  print_program_info();
#endif

#ifdef BBSIM
  // indicate that the vm is inited.
  alreadyExecuted(1);
#endif
}

/* Called upon block init to ensure that data structures are allocated even before
   VM start in case other blocks send us tuples */
void
vm_alloc(void)
{
  /* Get node ID */
  blockId = getGUID();

  // init stuff
  tuples = calloc(NUM_TYPES, sizeof(tuple_queue));
  newTuples = calloc(1, sizeof(tuple_queue));
  newStratTuples = calloc(1, sizeof(tuple_pqueue));
  oldTuples = calloc(NUM_TYPES, sizeof(tuple_t));
  delayedTuples = calloc(1, sizeof(tuple_pqueue));
  proved = calloc(NUM_TYPES, sizeof(meld_int));

  /* Reset received tuples queue */
  memset(receivedTuples, 0, sizeof(tuple_queue) * NUM_PORTS);

#ifdef BBSIM
  pthread_mutex_init(&(printMutex), NULL);
#endif
}

#ifndef BBSIM
void __myassert(char* file, int line, char* exp) {
  while (1) {
    setColor(RED); delayMS(50); setColor(BLUE); delayMS(50);}
}
#endif
