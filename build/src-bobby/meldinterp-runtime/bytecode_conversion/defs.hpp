#ifndef DEFS_HPP
#define DEFS_HPP

#include <stdint.h>
#include <cstring>

typedef unsigned char byte;
typedef unsigned short field_num;
typedef uint32_t uint_val;
typedef int32_t int_val;
typedef double float_val;
typedef uint64_t ptr_val;
typedef uint64_t node_val;
typedef bool bool_val;
typedef unsigned char predicate_id;
typedef unsigned short process_id;
typedef short derivation_count;
typedef uint64_t ref_count;
typedef unsigned char byte_code_el;
typedef byte_code_el* byte_code;
typedef uint32_t code_size_t;
typedef code_size_t code_offset_t;
typedef byte_code pcounter;
typedef size_t strat_level;
typedef size_t argument_id;
typedef uint_val const_id;
typedef uint32_t rule_id;
typedef uint32_t depth_t;
typedef size_t deterministic_timestamp;
typedef uint32_t external_function_id;

static const ptr_val null_ptr_val = 0;

typedef union {
  bool_val bool_field;
  int_val int_field;
  float_val float_field;
  node_val node_field;
  ptr_val ptr_field;
} tuple_field;

const size_t node_obj_size = 16 * sizeof(byte);

#define FIELD_INT     0x0
#define FIELD_FLOAT   0x1
#define FIELD_NODE    0x2
#define FIELD_LIST    0x3
#define FIELD_STRUCT  0x4
#define FIELD_BOOL    0x5
#define FIELD_ANY     0x6
#define FIELD_STRING  0x9

#define PRED_AGG 0x01
#define PRED_ROUTE 0x02
#define PRED_REVERSE_ROUTE 0x04
#define PRED_LINEAR 0x08
#define PRED_ACTION 0x10
#define PRED_REUSED 0x20
#define PRED_CYCLE 0x40

#define PRED_AGG_LOCAL 0x01
#define PRED_AGG_REMOTE 0x02
#define PRED_AGG_REMOTE_AND_SELF 0x04
#define PRED_AGG_IMMEDIATE 0x08
#define PRED_AGG_UNSAFE 0x00

#define AGG_FIRST 1
#define AGG_MAX_INT 2
#define AGG_MIN_INT 3
#define AGG_SUM_INT 4
#define AGG_MAX_FLOAT 5
#define AGG_MIN_FLOAT 6
#define AGG_SUM_FLOAT 7
#define AGG_SUM_LIST_FLOAT 11

const size_t PRED_NAME_SIZE_MAX = 32;
const size_t PRED_AGG_INFO_MAX = 32;

#define PREDICATE_DESCRIPTOR_SIZE 7
#define DELTA_TYPE_FIELD_SIZE 1

enum return_type {
   RETURN_OK,
   RETURN_SELECT,
   RETURN_NEXT,
   RETURN_LINEAR,
   RETURN_DERIVED,
   RETURN_END_LINEAR,
   RETURN_NO_RETURN
};

#endif
