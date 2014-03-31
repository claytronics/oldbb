# 1 "/home/anaz/blinkyblocks/build/src-bobby/meldinterp-runtime/extern_functions.bbh"
#ifndef _EXTERN_FUNCTIONS_H_
#define _EXTERN_FUNCTIONS_H_

#include <stdlib.h>
#include "api.h"
#include "../hw-api/hwMemory.h"

meld_value blockID(meld_value);
meld_value printInt(meld_value, meld_value, meld_value);

int gen_fresh_file(void);
int gen_fresh_tag(void);
int gen_fresh_nonce(void);

#endif
