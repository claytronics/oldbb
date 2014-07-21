#ifndef __CORE_H_
#define __CORE_H_

#include <stdlib.h>
#include <stdio.h>

#include "api.h"
#ifndef IGNORE_IN_PASS1_OFF_COMPILE_BB
#include "model.h"
#endif

/* print tuple allocations */
/* #define TUPLE_ALLOC_DEBUG 1 */
/* tuple allocation checks */
/* #define TUPLE_ALLOC_CHECKS 1 */

/* Size variables */
#define INSTR_SIZE 1

/* Instructions */
enum instr_type {
   RETURN_INSTR	        =  0x00,
   NEXT_INSTR		=  0x01,
   PERS_ITER_INSTR      =  0x02,
   TESTNIL_INSTR	=  0x03,
   OPERS_ITER_INSTR     =  0x04,
   LINEAR_ITER_INSTR    =  0x05,
   RLINEAR_ITER_INSTR   =  0x06,
   NOT_INSTR		=  0x07,
   SEND_INSTR 		=  0x08,
   FLOAT_INSTR          =  0x09,
   SELECT_INSTR         =  0x0A,
   RETURN_SELECT_INSTR  =  0x0B,
   OLINEAR_ITER_INSTR   =  0x0C,
   DELETE_INSTR         =  0x0D,
   RESET_LINEAR_INSTR   =  0x0E,
   END_LINEAR_INSTR     =  0x0F,
   RULE_INSTR           =  0x10,
   RULE_DONE_INSTR      =  0x11,
   ORLINEAR_ITER_INSTR  =  0x12,
   NEW_NODE_INSTR       =  0x13,
   NEW_AXIOMS_INSTR     =  0x14,
   SEND_DELAY_INSTR     =  0x15,
   PUSH_INSTR           =  0x16,
   POP_INSTR            =  0x17,
   PUSH_REGS_INSTR      =  0x18,
   POP_REGS_INSTR       =  0x19,
   CALLF_INSTR          =  0x1A,
   CALLE_INSTR          =  0x1B,
   SET_PRIORITY_INSTR   =  0x1C,
   MAKE_STRUCTR_INSTR   =  0x1D,
   MVINTFIELD_INSTR     =  0x1E,
   MVINTREG_INSTR       =  0x1F,
   CALL_INSTR	        =  0x20,
   MVFIELDFIELD_INSTR   =  0x21,
   MVFIELDREG_INSTR     =  0x22,
   MVPTRREG_INSTR       =  0x23,
   MVNILREG_INSTR       =  0x24,
   MVFIELDFIELDR_INSTR  =  0x25,
   MVREGFIELD_INSTR     =  0x26,
   MVREGFIELDR_INSTR    =  0x27,
   MVHOSTFIELD_INSTR    =  0x28,
   MVREGCONST_INSTR     =  0x29,
   MVCONSTFIELD_INSTR   =  0x2A,
   MVCONSTFIELDR_INSTR  =  0x2B,
   MVADDRFIELD_INSTR    =  0x2C,
   MVFLOATFIELD_INSTR   =  0x2D,
   MVFLOATREG_INSTR     =  0x2E,
   MVINTCONST_INSTR     =  0x2F,
   SET_PRIORITYH_INSTR  =  0x30,
   MVWORLDFIELD_INSTR   =  0x31,
   MVSTACKPCOUNTER_INSTR=  0x32,
   MVPCOUNTERSTACK_INSTR=  0x33,
   MVSTACKREG_INSTR     =  0x34,
   MVREGSTACK_INSTR     =  0x35,
   MVADDRREG_INSTR      =  0x36,
   MVHOSTREG_INSTR      =  0x37,
   ADDRNOTEQUAL_INSTR   =  0x38,
   ADDREQUAL_INSTR      =  0x39,
   INTMINUS_INSTR       =  0x3A,
   INTEQUAL_INSTR       =  0x3B,
   INTNOTEQUAL_INSTR    =  0x3C,
   INTPLUS_INSTR        =  0x3D,
   INTLESSER_INSTR      =  0x3E,
   INTGREATEREQUAL_INSTR=  0x3F,
   ALLOC_INSTR 	        =  0x40,
   BOOLOR_INSTR         =  0x41,
   INTLESSEREQUAL_INSTR =  0x42,
   INTGREATER_INSTR     =  0x43,
   INTMUL_INSTR         =  0x44,
   INTDIV_INSTR         =  0x45,
   FLOATPLUS_INSTR      =  0x46,
   FLOATMINUS_INSTR     =  0x47,
   FLOATMUL_INSTR       =  0x48,
   FLOATDIV_INSTR       =  0x49,
   FLOATEQUAL_INSTR     =  0x4A,
   FLOATNOTEQUAL_INSTR  =  0x4B,
   FLOATLESSER_INSTR    =  0x4C,
   FLOATLESSEREQUAL_INSTR= 0x4D,
   FLOATGREATER_INSTR   =  0x4E,
   FLOATGREATEREQUAL_INSTR=0x4F,
   MVREGREG_INSTR       =  0x50,
   BOOLEQUAL_INSTR      =  0x51,
   BOOLNOTEQUAL_INSTR   =  0x52,
   HEADRR_INSTR         =  0x53,
   HEADFR_INSTR         =  0x54,
   HEADFF_INSTR         =  0x55,
   HEADRF_INSTR         =  0x56,
   HEADFFR_INSTR        =  0x57,
   HEADRFR_INSTR        =  0x58,
   TAILRR_INSTR         =  0x59,
   TAILFR_INSTR         =  0x5A,
   TAILFF_INSTR         =  0x5B,
   TAILRF_INSTR         =  0x5C,
   MVWORLDREG_INSTR     =  0x5D,
   MVCONSTREG_INSTR     =  0x5E,
   CONSRRR_INSTR        =  0x5F,
   IF_INSTR 	        =  0x60,
   CONSRFF_INSTR        =  0x61,
   CONSFRF_INSTR        =  0x62,
   CONSFFR_INSTR        =  0x63,
   CONSRRF_INSTR        =  0x64,
   CONSRFR_INSTR        =  0x65,
   CONSFRR_INSTR        =  0x66,
   CONSFFF_INSTR        =  0x67,
   CALL0_INSTR          =  0x68,
   CALL1_INSTR          =  0x69,
   CALL2_INSTR          =  0x6A,
   CALL3_INSTR          =  0x6B,
   MVINTSTACK_INSTR     =  0x6C,
   PUSHN_INSTR          =  0x6D,
   MAKE_STRUCTF_INSTR   =  0x6E,
   STRUCT_VALRR_INSTR   =  0x6F,
   MVNILFIELD_INSTR	=  0x70,
   STRUCT_VALFR_INSTR   =  0x71,
   STRUCT_VALRF_INSTR   =  0x72,
   STRUCT_VALRFR_INSTR  =  0x73,
   STRUCT_VALFF_INSTR   =  0x74,
   STRUCT_VALFFR_INSTR  =  0x75,
   MVFLOATSTACK_INSTR   =  0x76,
   ADDLINEAR_INSTR      =  0x77,
   ADDPERS_INSTR        =  0x78,
   RUNACTION_INSTR      =  0x79,
   ENQUEUE_LINEAR_INSTR =  0x7A,
   UPDATE_INSTR         =  0x7B,
   MVARGREG_INSTR       =  0x7C,
   INTMOD_INSTR         =  0x7D,
   CPU_ID_INSTR         =  0x7E,
   NODE_PRIORITY_INSTR  =  0x7F,
   REMOVE_INSTR 	=  0x80,
   IF_ELSE_INSTR        =  0x81,
   JUMP_INSTR           =  0x82,
   ADD_PRIORITY_INSTR   =  0xA0,
   ADD_PRIORITYH_INSTR  =  0xA1,
   STOP_PROG_INSTR      =  0xA2,
   RETURN_LINEAR_INSTR  =  0xD0,
   RETURN_DERIVED_INSTR =  0xF0
};

/* Instruction sizes */
#define SEND_BASE            3
#define OP_BASE              4
#define BASE_ITER            21
#define ITER_BASE            23
#define PERS_ITER_BASE       21
#define OPERS_ITER_BASE      21
#define LINEAR_ITER_BASE     21
#define RLINEAR_ITER_BASE    21
#define OLINEAR_ITER_BASE    23
#define ORLINEAR_ITER_BASE   23
#define ALLOC_BASE           1 + 2
#define CALL_BASE            3 + 1
#define IF_BASE              1 + 1 + 4
#define TESTNIL_BASE         1 + 1 + 1
#define HEAD_BASE            1 + 3
#define NOT_BASE             1 + 2 * 1
#define RETURN_BASE          1
#define NEXT_BASE            1
#define FLOAT_BASE           1 + 2 * 1
#define SELECT_BASE          1 + 8
#define RETURN_SELECT_BASE   1 + 4
#define DELETE_BASE          1 + 2
#define REMOVE_BASE          1 + 1
#define RETURN_LINEAR_BASE   1
#define RETURN_DERIVED_BASE  1
#define RESET_LINEAR_BASE    1 + 4
#define END_LINEAR_BASE      1
#define RULE_BASE            1 + 4
#define RULE_DONE_BASE       1
#define NEW_NODE_BASE        1 + 1
#define NEW_AXIOMS_BASE      1 + 4
#define SEND_DELAY_BASE      1 + 2 + 4
#define PUSH_BASE            1
#define POP_BASE             1
#define PUSH_REGS_BASE       1
#define POP_REGS_BASE        1
#define CALLF_BASE           1 + 1
#define CALLE_BASE           3 + 1
#define MAKE_STRUCTR_BASE    1 + 1 + 1
#define MVINTFIELD_BASE      1 + 4 + 2
#define MVINTREG_BASE        1 + 4 + 1
#define MVFIELDFIELD_BASE    1 + 2 + 2
#define MVFIELDREG_BASE      1 + 2 + 1
#define MVPTRREG_BASE        1 + 8 + 1
#define MVNILFIELD_BASE      1 + 2
#define MVNILREG_BASE        1 + 1
#define MVREGFIELD_BASE      1 + 1 + 2
#define MVHOSTFIELD_BASE     1 + 2
#define MVREGCONST_BASE      1 + 1 + 4
#define MVCONSTFIELD_BASE    1 + 4 + 2
#define MVADDRFIELD_BASE     1 + 8 + 2
#define MVFLOATFIELD_BASE    1 + 8 + 2
#define MVFLOATREG_BASE      1 + 8 + 1
#define MVINTCONST_BASE      1 + 4 + 4
#define MVWORLDFIELD_BASE    1 + 2
#define MVSTACKPCOUNTER_BASE 1 + 1
#define MVPCOUNTERSTACK_BASE 1 + 1
#define MVSTACKREG_BASE      1 + 1 + 1
#define MVREGSTACK_BASE      1 + 1 + 1
#define MVADDRREG_BASE       1 + 8 + 1
#define MVHOSTREG_BASE       1 + 1
#define MVREGREG_BASE        1 + 2 * 1
#define MVARGREG_BASE        1 + 1 + 1
#define HEADRR_BASE          1 + 2 * 1
#define HEADFR_BASE          1 + 2 + 1
#define HEADFF_BASE          1 + 2 * 2
#define HEADRF_BASE          1 * 1 + 2
#define TAILRR_BASE          3
#define TAILFR_BASE          4
#define TAILFF_BASE          5
#define TAILRF_BASE          3
#define MVWORLDREG_BASE      1 + 1
#define MVCONSTREG_BASE      1 + 4 + 1
#define CONSRRR_BASE         1 + 1 + 3 * 1
#define CONSRFF_BASE         1 + 1 + 2 * 2
#define CONSFRF_BASE         1 + 2 + 1 + 2
#define CONSFFR_BASE         1 + 2 * 2 + 1
#define CONSRRF_BASE         1 + 2 * 1 + 2
#define CONSRFR_BASE         1 + 1 + 2 + 1
#define CONSFRR_BASE         1 + 1 + 2 + 2 * 1
#define CONSFFF_BASE         1 + 3 * 2
#define CALL0_BASE           3
#define CALL1_BASE           3 + 1
#define CALL2_BASE           3 + 2 * 1
#define CALL3_BASE           3 + 3 * 1
#define MVINTSTACK_BASE      1 + 4 + 1
#define PUSHN_BASE           1 + 1
#define MAKE_STRUCTF_BASE    1 + 2
#define STRUCT_VALRR_BASE    1 + 1 + 2 * 1
#define STRUCT_VALFR_BASE    1 + 1 + 2 + 1
#define STRUCT_VALRF_BASE    5
#define STRUCT_VALRFR_BASE   5
#define STRUCT_VALFF_BASE    1 + 1 + 2 * 2
#define STRUCT_VALFFR_BASE   6
#define MVFLOATSTACK_BASE    1 + 8 + 1
#define ADDLINEAR_BASE       1 + 1
#define ADDPERS_BASE         2
#define RUNACTION_BASE       2
#define ENQUEUE_LINEAR_BASE  2
#define UPDATE_BASE          1 + 1
#define SET_PRIORITY_BASE    1 + 2 * 1
#define SET_PRIORITYH_BASE   1 + 1
#define ADD_PRIORITY_BASE    1 + 2 * 1
#define ADD_PRIORITYH_BASE   1 + 1
#define STOP_PROG_BASE       1
#define CPU_ID_BASE          1 + 2 * 1
#define NODE_PRIORITY_BASE   1 + 2 * 1
#define IF_ELSE_BASE         1 + 1 + 2 * 4
#define JUMP_BASE            1 + 4

/* Instruction specific macros and functions */
#define IF_JUMP(x)    (*(uint32_t*)((const unsigned char*)(x)))

/* macros */

#define ITER_TYPE(x)  ((*(const unsigned char*)((x)+9))&0x7f)
#define ITER_INNER_JUMP(x)  (*(uint32_t*)((const unsigned char*)((x)+12)))
#define ITER_OUTER_JUMP(x)  (*(uint32_t*)((const unsigned char*)((x)+16)))
#define ITER_NUM_ARGS(x) (*(const unsigned char*)((x)+20))
#define ITER_MATCH_FIELD(x)   (*(const unsigned char*)(x))
#define ITER_MATCH_VAL(x)   ((*(const unsigned char*)((x)+1)))

#define SEND_MSG(x)   (*(const unsigned char*)(x))
#define SEND_RT(x)    (*(const unsigned char*)((x)+1))

#define SEND_ARG1(x)  ((*((const unsigned char*)(x)+2)) & 0x3f)
#define SEND_DELAY(x) (*(const unsigned char *)((x)+2))

#define REMOVE_REG(x) ((*(const unsigned char*)(x))&0x1f)

#define OP_ARG1(x)    (((*(const unsigned char*)(x)) & 0x3f))
#define OP_ARG2(x)    (((*(const unsigned char*)((x)+1)) & 0xfc) >> 2)
#define OP_OP(x)      ((*(const unsigned char*)((x)+2)) & 0x1f)
#define OP_DST(x)     ((((*(const unsigned char*)((x)+1)) & 0x03) << 3) | \
                      (((*(const unsigned char*)((x)+2)) & 0xe0) >> 5))

#define FETCH(x)   (*(const unsigned char*)(x)) 
#define MOVE_SRC(x)   (*(const unsigned char*)((x)+1))
#define MOVE_DST(x)   (((*(const unsigned char*)((x)+1))&0x3f))
#define ALLOC_TYPE(x) ((((*(const unsigned char *)(x))&0x1f) << 2) | \
					   (((*(const unsigned char *)(x+1))&0xc0) >> 6))
#define ALLOC_DST(x)  ((*(const unsigned char *)((x)+1))&0x3f)

#define CALL_VAL(x)   (*(const unsigned char *)(x))
#define CALL_DST(x)   ((*(const unsigned char *)((x)+1)) & 0x1f)
#define CALL_ID(x)    ((((*(const unsigned char *)((x))) & 0x0f) << 3) | \
						(((*(const unsigned char *)((x)+1)) & 0xe0) >> 5))

#define CALL_ARGS(x)  (extern_functs_args[CALL_ID(x)])
#define CALL_FUNC(x)  (extern_functs[CALL_ID(x)])

#define VALUE_TYPE_FLOAT 0x00
#define VALUE_TYPE_INT 0x01
#define VALUE_TYPE_FIELD 0x02
#define VALUE_TYPE_HOST 0x03
#define VALUE_TYPE_REVERSE 0x04
#define VALUE_TYPE_TUPLE 0x1f

#define VAL_IS_REG(x)   (((const unsigned char)(x)) & 0x20)
#define VAL_IS_TUPLE(x) (((const unsigned char)(x)) == VALUE_TYPE_TUPLE)
#define VAL_IS_FLOAT(x) (((const unsigned char)(x)) == VALUE_TYPE_FLOAT)
#define VAL_IS_INT(x)   (((const unsigned char)(x)) == VALUE_TYPE_INT)
#define VAL_IS_FIELD(x) (((const unsigned char)(x)) == VALUE_TYPE_FIELD)
#define VAL_IS_HOST(x)  (((const unsigned char)(x)) == VALUE_TYPE_HOST)
#define VAL_IS_REVERSE(x) (((const unsigned char)(x)) == VALUE_TYPE_REVERSE)

#define VAL_REG(x) (((const unsigned char)(x)) & 0x1f)
#define VAL_FIELD_NUM(x) ((*(const unsigned char *)(x)) & 0xff)
#define VAL_FIELD_REG(x) ((*(const unsigned char *)((x)+1)) & 0x1f)

#define TYPE_DESCRIPTOR_SIZE 7
#define DELTA_SIZE 2
#define TYPE_FIELD_SIZE 1
#define TYPE_FIELD_TYPE unsigned char

#define TUPLE_TYPE(x)   (*(TYPE_FIELD_TYPE *)(x))
#define TUPLE_FIELD(x,off)  ((void *)(((unsigned char*)(x)) + TYPE_FIELD_SIZE + (off)))

#define NUM_TYPES  (meld_prog[0])
#define NUM_RULES  (meld_prog[1])
#define TYPE_OFFSET(x)     (meld_prog[2 + (x)])

/* First 2 bytes contain offset to type's bytecode */
#define TYPE_DESCRIPTOR(x) ((unsigned char *)(meld_prog + TYPE_OFFSET(x)))
/* Contain tuple's type (linear/persistent...)*/
#define TYPE_PROPERTIES(x) (*(TYPE_DESCRIPTOR(x) + 2))
/* If tuple is aggregate, contains its type, 0 otherwise */
#define TYPE_AGGREGATE(x)  (*(TYPE_DESCRIPTOR(x) + 3))
/* Stratification round ..?*/
#define TYPE_STRATIFICATION_ROUND(x) (*(TYPE_DESCRIPTOR(x) + 4))
/* Number of arguments */
#define TYPE_NOARGS(x)     (*(TYPE_DESCRIPTOR(x) + 5))
/* Number of deltas ..? */
#define TYPE_NODELTAS(x)   (*(TYPE_DESCRIPTOR(x) + 6))
/* Argument descriptor */
#define TYPE_ARGS_DESC(x)  ((unsigned char*)(TYPE_DESCRIPTOR(x)+TYPE_DESCRIPTOR_SIZE))
/* Returns type of argument number f for type x */
#define TYPE_ARG_DESC(x, f) ((unsigned char *)(TYPE_ARGS_DESC(x)+1*(f)))
/* Returns type of deltas for type x */
#define TYPE_DELTAS(x)     (TYPE_ARGS_DESC(x) + 1*TYPE_NOARGS(x))

/* Returns address of bytecode for type x */
#define TYPE_START(x)							\
  ((unsigned char*)(meld_prog + *(unsigned short *)TYPE_DESCRIPTOR(x)))
#define TYPE_START_CHECK(x)			\
  (*(unsigned short *)TYPE_DESCRIPTOR(x))

/* Offset to rule byte code, pred 0 byte code start is reference */
#define RULE_START(x)							\
  ((unsigned char*)(meld_prog + *(unsigned short*)			\
		    ((TYPE_START(0) \
		      - NUM_RULES * sizeof(unsigned short)) + 2 * (x)) ) )
#define RULE_START_CHECK(x)						\
  (*(unsigned short*)							\
   ((TYPE_START(0) -							\
     (NUM_RULES * sizeof(unsigned short))) + 2 * (x) ) )

#define TYPE_IS_STRATIFIED(x) (TYPE_STRATIFICATION_ROUND(x) > 0)

#define TYPE_NAME(x)       (tuple_names[x])
#define TYPE_ARG_TYPE(x, f) ((unsigned char)(*TYPE_ARG_DESC(x, f)))

#define TYPE_SIZE(x)       (arguments[(x) * 2 + 1])
#define TYPE_ARGS(x)       (arguments + arguments[(x) * 2])

#define TYPE_ARG(x, f)     (TYPE_ARGS(x)+2*(f))
#define TYPE_ARG_SIZE(x, f) (*TYPE_ARG(x, f))
#define TYPE_ARG_OFFSET(x, f)   (*(TYPE_ARG(x, f) + 1))

#define SET_TUPLE_FIELD(tuple, field, data) \
		memcpy(TUPLE_FIELD(tuple, TYPE_ARG_OFFSET(TUPLE_TYPE(tuple), field)), \
				data, TYPE_ARG_SIZE(TUPLE_TYPE(tuple), field))
#define GET_TUPLE_FIELD(tuple, field) \
		TUPLE_FIELD(tuple, TYPE_ARG_OFFSET(TUPLE_TYPE(tuple), field))
#define GET_TUPLE_SIZE(tuple, field) \
		TYPE_ARG_SIZE(TUPLE_TYPE(tuple), field)
		
#define TYPE_IS_AGG(x)        (TYPE_PROPERTIES(x) & 0x01)
#define TYPE_IS_PERSISTENT(x) (TYPE_PROPERTIES(x) & 0x02)
#define TYPE_IS_LINEAR(x) 		(TYPE_PROPERTIES(x) & 0x04)
#define TYPE_IS_DELETE(x) 		(TYPE_PROPERTIES(x) & 0x08)
#define TYPE_IS_SCHEDULE(x) 	(TYPE_PROPERTIES(x) & 0x10)
#define TYPE_IS_ROUTING(x) 		(TYPE_PROPERTIES(x) & 0x20)
#define TYPE_IS_PROVED(x)     (TYPE_PROPERTIES(x) & 0x40)

#define AGG_AGG(x)    (((x) & (0xf0)) >> 4)
#define AGG_FIELD(x)  ((x) & 0x0f)

#define AGG_NONE 0
#define AGG_FIRST 1
#define AGG_MAX_INT 2
#define AGG_MIN_INT 3
#define AGG_SUM_INT 4
#define AGG_MAX_FLOAT 5
#define AGG_MIN_FLOAT 6
#define AGG_SUM_FLOAT 7
#define AGG_SET_UNION_INT 8
#define AGG_SET_UNION_FLOAT 9
#define AGG_SUM_LIST_INT 10
#define AGG_SUM_LIST_FLOAT 11

#define FIELD_INT 0x0
#define FIELD_FLOAT 0x1
#define FIELD_ADDR 0x2
#define FIELD_OTHER 0x2
#define FIELD_LIST_INT 0x3
#define FIELD_LIST_FLOAT 0x4
#define FIELD_LIST_ADDR 0x5
#define FIELD_SET_INT 0x6
#define FIELD_SET_FLOAT 0x7
#define FIELD_TYPE 0x8
#define FIELD_STRING 0x9

#define DELTA_TYPE(ori, id) (*(unsigned char*)(deltas[ori] + (id)*DELTA_SIZE))
#define DELTA_POSITION(ori, id) (*(unsigned char*)(deltas[ori] + (id)*DELTA_SIZE + 1))
#define DELTA_WITH(ori) (delta_sizes[ori])
#define DELTA_TOTAL(ori) (delta_sizes[ori])

#define RET_RET 0
#define RET_NEXT 1
#define RET_LINEAR 2
#define RET_DERIVED 3
#define RET_ERROR -1

#define TYPE_SETCOLOR 2
#define TYPE_SETCOLOR2 7

#define PROCESS_TUPLE 0
#define PROCESS_ITER 1
#define PROCESS_RULE 2

#define RULE_NUMBER(x) ((unsigned char)(((x) & 0xf0) >> 4))
#define PROCESS_TYPE(x) ((unsigned char)((x) & 0x0f))

extern const unsigned char meld_prog[];
typedef Register (*extern_funct_type)();
extern extern_funct_type extern_functs[];
extern int extern_functs_args[];
extern char *tuple_names[];
extern unsigned char *arguments;
extern int *delta_sizes;

static inline tuple_t
tuple_alloc(tuple_type type)
{
#ifdef TUPLE_ALLOC_CHECKS
  if(type >= NUM_TYPES || type < 0) {
    fprintf(stderr, "Unrecognized type: %d\n", type);
    exit(EXIT_FAILURE);
  }
#endif
  
  tuple_t tuple = ALLOC_TUPLE(TYPE_SIZE(type));
  
  TUPLE_TYPE(tuple) = type;
	
#ifdef TUPLE_ALLOC_DEBUG
  printf("New %s(%d) tuple -- size: %d\n", tuple_names[type], 
	 type, TYPE_SIZE(type));
#endif

	return tuple;
}

void tuple_handle(tuple_t tuple, int isNew, Register *reg);
void tuple_send(tuple_t tuple, void *rt, meld_int delay, int isNew);
void tuple_do_handle(tuple_type type,	void *tuple, int isNew, Register *reg);
void tuple_print(tuple_t tuple, FILE *fp);
char* arg2String(tuple_t tuple, byte index);

int process_bytecode(tuple_t tuple, const unsigned char *pc,
		  int isNew, Register *reg, byte state);
void derive_axioms(Register *reg);

static inline void
tuple_dump(void *tuple)
{
	tuple_print(tuple, stderr);
	fprintf(stderr, "\n");
}

void print_program_info(void);

void init_deltas(void);
void init_fields(void);
void facts_dump(void);
void init_consts(void);

tuple_entry* queue_enqueue(tuple_queue *queue, tuple_t tuple, record_type isNew);
bool queue_is_empty(tuple_queue *queue);
tuple_t queue_dequeue(tuple_queue *queue, int *isNew);
tuple_t queue_pop_tuple(tuple_queue *queue);
void queue_push_tuple(tuple_queue *queue, tuple_entry *entry);

static inline void
queue_init(tuple_queue *queue)
{
  queue->head = queue->tail = NULL;
}

static inline bool
p_empty(tuple_pqueue *q)
{
	return q->queue == NULL;
}

static inline tuple_pentry*
p_peek(tuple_pqueue *q)
{
	return q->queue;
}

tuple_pentry *p_dequeue(tuple_pqueue *q);
void p_enqueue(tuple_pqueue *q, meld_int priority, tuple_t tuple,
		void *rt, record_type isNew);
int queue_length (tuple_queue *queue);

extern tuple_type TYPE_INIT;
extern tuple_type TYPE_EDGE;
extern tuple_type TYPE_COLOCATED;
extern tuple_type TYPE_PROVED;
extern tuple_type TYPE_TERMINATE;
extern tuple_type TYPE_NEIGHBORCOUNT;
extern tuple_type TYPE_NEIGHBOR;
extern tuple_type TYPE_VACANT;

extern void setColorWrapper (byte color);
extern void setLEDWrapper (byte r, byte g, byte b, byte intensity);
extern NodeID getBlockId (void);
/* void print_process(const unsigned char *pc); */

#ifdef BBSIM
extern pthread_mutex_t printMutex;
#endif

#endif

