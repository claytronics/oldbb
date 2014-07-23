#ifndef __PARSER_H__
#define __PARSER_H__

#include "stdint.h"
#include "string.h"

typedef unsigned char byte;

/* Used to check if opened file is Meld byte code file */
const uint32_t MAGIC1 = 0x646c656d;
const uint32_t MAGIC2 = 0x6c696620;

/* Macros from source MeldVM */
#define VERSION_AT_LEAST(MAJ, MIN) \
(majorVersion > (MAJ) || (majorVersion == (MAJ) && minorVersion >= (MIN)))
#define PRED_NAME_SIZE_MAX 32
#define PRED_AGG_INFO_MAX 32
#define PREDICATE_DESCRIPTOR_SIZE 7
#define DELTA_TYPE_FIELD_SIZE 1

/* Field types for target VM */
enum field_type {
   FIELD_INT = 0x0,
   FIELD_FLOAT = 0x1,
   FIELD_NODE = 0x2,
   FIELD_LIST = 0x3,
   FIELD_STRUCT = 0x4,
   FIELD_BOOL = 0x5,
   FIELD_ANY = 0x6, 
   FIELD_STRING = 0x9,
   FIELD_INTLIST = 0xa,
   FIELD_FLOATLIST = 0xb,
   FIELD_NODELIST = 0xc
};

typedef struct _Predicate {
  uint32_t codeSize; 		/* Size of byte code */
  byte argOffset;		/* Offset for global arg array */
  char *pName;			/* Name string */
  byte *pBytecode;		/* Pointer to byte code */

  uint32_t bytecodeOffset;	/* Offset to byte code in header */
  byte properties;		/* Linear, persistent, agg ... */
  byte agg;			/* Type of agg if applicable */
  byte level;			/* Stratification round */
  byte nFields;			/* Number of arguments */

  byte desc_size;		/* Size of predicate descriptor */
} Predicate;

typedef struct _Rule {
  uint32_t codeSize; 		/* Size of byte code */
  byte *pBytecode;		/* Pointer to bytecode */
  char *pName;			/* Rule string */

  uint32_t bytecodeOffset;	/* Offset to byte code */
  uint32_t numInclPreds;	/* Number of included predicates */
  byte inclPredIDs[32];		/* ID of each included predicate */
  byte persistence;		/* Is the rule persistent? */

  byte desc_size;		/* Size of rule descriptor */
} Rule;


/* Source properties */
#define PRED_AGG 0x01
#define PRED_ROUTE 0x02
#define PRED_REVERSE_ROUTE 0x04
#define PRED_LINEAR 0x08
#define PRED_ACTION 0x10
#define PRED_REUSED 0x20
#define PRED_CYCLE 0x40

/* Target properties */
#define TYPE_AGG 0x01
#define TYPE_PERSISTENT 0x02
#define TYPE_LINEAR 0x03
#define TYPE_DELETE 0x08
#define TYPE_SCHEDULE 0x10
#define TYPE_ROUTING 0x20

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
/* done */

#endif	/* ifdef __PARSER_H__ */
