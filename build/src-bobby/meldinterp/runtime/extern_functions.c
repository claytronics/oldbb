# 1 "/seth/claycvs/svn/blinkyblocks/newcode/src/meldinterp/runtime/extern_functions.bb"
#include "extern_functions.h"
#include <stdio.h>

int blockID(int x) {
    fprintf(stderr, "Returning %d\n", x);
	return x;
}

void printInt(int x, int y, int z) {
     fprintf(stderr, "Printing for catom %d at %d value %d\n", x, y, z);
}
