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

/******************************************************************************
@Description: meldvm.bb is the VM's main file, it initializes the VM,
introduces and updates axioms, sends tuples to other blocks, and triggers
the execution of all tuples and rules through the program's main loop.
*******************************************************************************/

/* Various DEBUG Modes for troubleshooting */
//#define DEBUG
#define DEBUG_NEIGHBORHOOD
#define DEBUG_SEND
#define DEBUG_RULES

void vm_init(void);
byte updateRuleState(byte rid);

/* Queue for tuples to send with delay */
threadvar tuple_pqueue *delayedTuples; 
/* Contains a queueu for each type, this is essentially the database */
threadvar tuple_queue *tuples;	      
/* Where stratified tuples are enqueued for execution  */
threadvar tuple_pqueue *newStratTuples;
/* Where non-stratified tuples are enqueued for execution */
threadvar tuple_queue *newTuples;
/* Received tuples are enqueued both to a normal queue
 * and to this one. Tuples store in this one will be used to remove 
 * remove tuples from the database.*/
threadvar tuple_queue receivedTuples[NUM_PORTS];

/* This block's ID */
threadvar NodeID blockId;

/* An array of 32 registers (pointers) */
threadvar Register reg[32];

/* thr\dvar byte retractionOccured = 0; */
/* thr\dvar byte inNeighborCountUpdate = 0; */

#ifdef BBSIM
#include <sys/timeb.h>
#endif

int myGetTime ()
{
#ifdef BBSIM
	struct timeb t;
	
	ftime(&t);

	return t.millitm + 1000 * (t.time % (1 <<  20));
#else
   return getTime();
#endif
}

/* Print the content of the newTuples queue */
void
print_newTuples(void)
{
#ifdef BBSIM
  pthread_mutex_lock(&(printMutex));
#endif
  fprintf(stderr, "\x1b[34m--%d--\tContent of queue newTuples: \n", blockId);
  tuple_entry *tupleEntry;
  for (tupleEntry = newTuples->head; 
       tupleEntry != NULL; 
       tupleEntry = tupleEntry->next) {
    tuple_print(tupleEntry->tuple, stderr);
    fprintf(stderr, " -- isNew = %d\n", tupleEntry->records.count);
  }
  fprintf(stderr, "\x1b[0m");
#ifdef BBSIM
  pthread_mutex_unlock(&(printMutex));
#endif
}

/* Prints the content of the newStartTuples queue */
void
print_newStratTuples(void)
{
#ifdef BBSIM
  pthread_mutex_lock(&(printMutex));
#endif
  fprintf(stderr, "\x1b[34m--%d--\tContent of queue newStratTuples: \n",
	  blockId);
  if (newStratTuples) {
    tuple_pentry *tupleEntry;
    for (tupleEntry = newStratTuples->queue; 
	 tupleEntry != NULL; 
	 tupleEntry = tupleEntry->next) {
      tuple_print(tupleEntry->tuple, stderr);
      fprintf(stderr, " -- isNew = %d\n", tupleEntry->records.count);
    }
  }
  fprintf(stderr, "\x1b[0m");
#ifdef BBSIM
  pthread_mutex_unlock(&(printMutex));
#endif
}

/* Gets ID of neighbor on face 'face' */
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

/* Enqueue a tuple for execution */
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

/* Enqueue a neighbor or vacant tuple */
void enqueue_face(NodeID neighbor, meld_int face, int isNew)
{
  tuple_t tuple = NULL;

  if (neighbor <= 0) {
     if(TYPE_VACANT == -1) /* no such predicate in the program */
        return;
     tuple = tuple_alloc(TYPE_VACANT);
     SET_TUPLE_FIELD(tuple, 0, &face);
  }
  else {
     if(TYPE_NEIGHBOR == -1) /* no such predicate in the program */
        return;

     tuple = tuple_alloc(TYPE_NEIGHBOR);
     SET_TUPLE_FIELD(tuple, 0, &neighbor);
     SET_TUPLE_FIELD(tuple, 1, &face);
  }

  enqueueNewTuple(tuple, (record_type) isNew);
}

/* Enqueue a neighborCount tuple */
static
void enqueue_count(meld_int count, int isNew)
{
   if(TYPE_NEIGHBORCOUNT == -1) /* no such predicate in the program */
      return;

  tuple_t tuple = tuple_alloc(TYPE_NEIGHBORCOUNT);

  SET_TUPLE_FIELD(tuple, 0, &count);

  enqueueNewTuple(tuple, (record_type) isNew);
}

/* Enqueue a tap tuple and also prints database if DEBUG is ON */
static
void enqueue_tap(void)
{
   if(TYPE_TAP == -1) /* no such predicate in the program */
      return;

  tuple_t tuple = tuple_alloc(TYPE_TAP);

  enqueueNewTuple(tuple, (record_type) 1);

  /* #if DEBUG */
  facts_dump();
  /* #endif */
}

/* Enqueue init tuple, triggers derivation of RULE 0, which derives axioms */
static
void enqueue_init(void)
{
  if(TYPE_INIT == -1)
    return;

  tuple_t tuple = tuple_alloc(TYPE_INIT);
  enqueueNewTuple(tuple, (record_type) 1);
}

/* Saves the ID of useful types */
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

/* Can be used to directly process neighborCount instead of enqueing it */
/* static
void handle_count(meld_int count, int isNew)
{
  tuple_t tuple = tuple_alloc(TYPE_NEIGHBORCOUNT);

  SET_TUPLE_FIELD(tuple, 0, &count);

  tuple_handle(tuple, isNew, reg);
  } */

#ifdef BBSIM
pthread_mutex_t printMutex;
#endif

/* The VM's main function */
void meldMain(void)
{
  vm_init();

  //block initialization
#if DEBUG
  printf("meld program started\n");
#endif

  //setColor(0);
  setLED(128,0,128,32);

  /* Enqueue init to derive the program's axioms */
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
	       && p_peek(delayedTuples)->priority <= myGetTime()) {
      tuple_pentry *entry = p_dequeue(delayedTuples);

      tuple_send(entry->tuple, entry->rt, 0, entry->records.count);
      free(entry);
    } else if (!(p_empty(newStratTuples))) {
      tuple_pentry *entry = p_dequeue(newStratTuples);
      tuple_handle(entry->tuple, entry->records.count, reg);
      
      free(entry);
    } else {
      /* If all tuples have been processed
       * update rule state and process them if they are ready */
      for (i = 0; i < NUM_RULES; ++i) {

	if (updateRuleState(i)) {
	  /* Set state byte used by DEBUG */
	  byte processState = PROCESS_RULE | (i << 4);
	  
	  /* Don't process persistent rules (which is useless) 
	   * as they all have only a RETURN instruction.
	   */
	  if (!RULE_ISPERSISTENT(i)) {
#ifdef DEBUG_RULES
	    printf ("\n\x1b[35m--%d--\tRule %d READY!\x1b[0m\n", getBlockId(), i);
#endif
	    /* Trigger execution */
	  process_bytecode (NULL, RULE_START(i), 1, reg, processState);
	  }
	}
	/* else: Rule not ready yet, will re-check at next main loop run */
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

      /* Delete received tuples from database
       * This may need to be reviewed, 
       * I am not sure what LM is supposed to do with received tuples
       */
      while(!queue_is_empty(&(receivedTuples[i]))) {
         tuple_t tuple = queue_dequeue(&receivedTuples[i], NULL);
         printf("--%d--\tDelete received ", blockId);
         tuple_print(tuple, stdout);
         printf("\n");
         enqueueNewTuple(tuple, (record_type) -1);
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

/* Receive a tuple and enqueue it to both receivedTuples and newTuples */
void receive_tuple(int isNew)
{
  tuple_t rcvdTuple = (tuple_t)thisChunk->data;
  tuple_t tuple;
  tuple_type type = TUPLE_TYPE(rcvdTuple);
  size_t tuple_size = TYPE_SIZE(type);
  
#ifdef DEBUG_SEND
#ifdef BBSIM
  pthread_mutex_lock(&(printMutex));
#endif
  printf ("\x1b[33m--%d--\t Tuple received from %d: %s -- isNew = %d\x1b[0m\n",
	  getBlockId(), get_neighbor_ID(faceNum(thisChunk)), 
	  tuple_names[TUPLE_TYPE(rcvdTuple)], isNew);
/* tuple_print (rcvdTuple, stdout); */
#ifdef BBSIM
  pthread_mutex_unlock(&(printMutex));
#endif
#endif

  if(!TYPE_IS_LINEAR(type) && !TYPE_IS_ACTION(type)) {
     tuple_queue *queue = receivedTuples + faceNum(thisChunk);
     if(isNew > 0) {
        tuple = malloc(tuple_size);
        memcpy(tuple, rcvdTuple, tuple_size);
        queue_enqueue(queue, tuple, (record_type)isNew);
     } else {
        // delete tuple from queue because it must invalidate some other tuple
        tuple_entry **current;
        for (current = &queue->head;
            *current != NULL;
            current = &(*current)->next) {
          if(memcmp((*current)->tuple, rcvdTuple, tuple_size) == 0) {
             FREE_TUPLE(queue_dequeue_pos(queue, current));
             break;
          }
        }
     }
  }

  tuple = malloc(tuple_size);
  memcpy(tuple, rcvdTuple, tuple_size);
  enqueueNewTuple(tuple, (record_type)isNew);
}

/* Received tuple is a retraction fact */
void receive_tuple_delete(void)
{
  receive_tuple(-1);
}

/* Received tuple is a normal fact */
void receive_tuple_add(void)
{
  receive_tuple(1);
}

void free_chunk(void) {
  free(thisChunk);
}

/* Sends a tuple to Block of ID rt, with or without delay */
void tuple_send(tuple_t tuple, NodeID rt, meld_int delay, int isNew)
{
  assert (TUPLE_TYPE(tuple) < NUM_TYPES);

  if (delay > 0) {
    p_enqueue(delayedTuples, myGetTime() + delay, tuple, rt, (record_type) isNew);
    return;
  }

  NodeID target = rt;

#ifdef DEBUG_SEND
#ifdef BBSIM
  pthread_mutex_lock(&(printMutex));
#endif
  printf ("\x1b[33m--%d--\t Sending tuple: %s to %d -- isNew = %d\x1b[0m\n", 
	  getBlockId(), tuple_names[TUPLE_TYPE(tuple)], target, isNew);
  /* tuple_print (tuple, stdout); */
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
      /* This may happen when you delete a block in the simulator */
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
  /* A rule is ready if all included predicates are present in the database */
  for (i = 0; i < RULE_NUM_INCLPREDS(rid); ++i) {
    if (TUPLES[RULE_INCLPRED_ID(rid, i)].length == 0)
      return INACTIVE_RULE;
  }

  /* Rule is ready, enqueue it or process it rightaway */
  return ACTIVE_RULE;
}

/* Simply calls tuple_do_handle located in core.c to handle tuple  */
void tuple_handle(tuple_t tuple, int isNew, Register *registers)
{
  tuple_type type = TUPLE_TYPE(tuple);
  assert (type < NUM_TYPES);

  /* if (isNew > 0 */
  /*     && !inNeighborCountUpdate)  */
  /*   retractionOccured = 1; */

  tuple_do_handle(type, tuple, isNew, registers);
}

/* Used to call setColor functions from core.c */
void
setColorWrapper (byte color) { setColor(color % NUM_COLORS);}
void
setLEDWrapper (byte r, byte g, byte b, byte intensity) 
{ setLEDWrapper (r, g, b, intensity);}
/* Used to get blockId from core.c */
NodeID 
getBlockId (void) { return blockId;}

#ifdef BBSIM
extern int alreadyExecuted(int flag);
#endif

/* VM initialization routine */
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

/* Called upon block init (block.bb) 
 * to ensure that data structures are allocated before
 * VM start in case other blocks send us tuples - Would seg fault otherwise */
void
vm_alloc(void)
{
  /* Get node ID */
  blockId = getGUID();

  // init stuff
  tuples = calloc(NUM_TYPES, sizeof(tuple_queue));
  newTuples = calloc(1, sizeof(tuple_queue));
  newStratTuples = calloc(1, sizeof(tuple_pqueue));
  delayedTuples = calloc(1, sizeof(tuple_pqueue));

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
