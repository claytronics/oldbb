
#ifndef API_H
#define API_H

#include <stdint.h>

#ifndef IGNORE_IN_PASS1_OFF_COMPILE_BB
#include "model.h"
#endif

typedef Register meld_value;

#define NODE_FORMAT "%u"

#define MELD_INT(x)   (*(meld_int *)(x))
#define MELD_FLOAT(x) (*(meld_float *)(x))
#define MELD_NODE(x)  (*(Node **)(x))
#define MELD_NODE_ID(x) (*(NodeID *)(x))
#define MELD_SET(x) (*(Set **)(x))
#define MELD_LIST(x)  (*(List **)(x))
#define MELD_PTR(x)	  (*(void **)(x))
#define MELD_BOOL(x)  (*(unsigned char*)(x))

#define MELD_CONVERT_INT(x)   (*(Register *)(meld_int *)&(x))
#define MELD_CONVERT_FLOAT(x) (*(Register *)(meld_float *)&(x))
#define MELD_CONVERT_LIST(x)  ((Register)(x))

#define MELD_CONVERT_REG_TO_PTR(x)   ((void *)(Register)(x))
#define MELD_CONVERT_PTR_TO_REG(x)   ((Register)(void *)(x))

#endif
