# 1 "/home/anaz/blinkyblocks/build/src-bobby/meldinterp-runtime/model.bbh"
#ifndef __MODEL_H_
#define __MODEL_H_

#include <inttypes.h>
#include "../system/defs.h"

/* allocation for tuples */
#define ALLOC_TUPLE(x) malloc(x)
#define FREE_TUPLE(x) free(x)

 typedef void* tuple_t;
 typedef int32_t meld_int;
 typedef float meld_float;
 typedef short tuple_type;
 typedef unsigned long int Register;

 typedef struct _tuple_entry tuple_entry;
 typedef struct _tuple_queue { tuple_entry *head; tuple_entry *tail; } tuple_queue;
 typedef union { int count; tuple_queue *agg_queue; } record_type;
 struct _tuple_entry { struct _tuple_entry *next; record_type records; void *tuple; };
 typedef struct _tuple_pentry { meld_int priority; struct _tuple_pentry *next; record_type records; void *tuple; void *rt;} tuple_pentry;
 typedef struct {struct _tuple_pentry *queue;} tuple_pqueue;

extern tuple_queue *tuples;
extern void **oldTuples;
extern meld_int *proved;

 typedef struct _persistent_set { void *array; int total; int current; } persistent_set;

extern NodeID blockId;

#ifdef BBSIM
typedef Block Node;
#else
typedef int Node;
#endif

extern void enqueueNewTuple(tuple_t tuple, record_type isNew);

#define TUPLES tuples
#define OLDTUPLES oldTuples

#define EVAL_HOST (&blockId)
#define PERSISTENT_INITIAL 2
#define PERSISTENT persistent
#define PROVED proved
#define PUSH_NEW_TUPLE(tuple) (enqueueNewTuple(tuple, (record_type)1))
#define TERMINATE_CURRENT() /* NOT IMPLEMENTED FOR BBs */

#ifndef BBSIM
typedef char bool;
#define true 1
#define false 0
#define EXIT_FAILURE -1
#define NULL ((void *)0)
#endif

#endif
