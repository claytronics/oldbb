# 1 "/home/dcampbel/Research/blinkyBocksHardware/build/src-bobby/meldinterp-runtime/extern_functions.bb"
#include "../sim/block.h"
#include "extern_functions.h"
#include "list_runtime.h"
#include <stdio.h>

meld_value blockID(meld_value x) {
  meld_int ret = MELD_INT(&x);
  //printf("Returning %d\n", ret);
  return MELD_CONVERT_INT(ret);
}

/*meld_value printInt(meld_value x, meld_value y, meld_value z) {    if (sizeof(int) >= sizeof(meld_int)) {	fprintf(stderr, "%2d: %2d -> %d\n",		(int)MELD_INT(x),(int)MELD_INT(y),(int)MELD_INT(z));    } else {	fprintf(stderr, "%s: ", convert_meld_int_safe(MELD_INT(x)));	fprintf(stderr, "%s -> ", convert_meld_int_safe(MELD_INT(y)));	fprintf(stderr, "%s\n", convert_meld_int_safe(MELD_INT(z)));    }   return 0;}*/

int gen_fresh_file(void) {
	static int f = 0;
	return f++;
}

int gen_fresh_tag(void) {
	static int t = 0;
	return t++;
}

int gen_fresh_nonce(void) {
	static int n = 0;
	return n++;
}
