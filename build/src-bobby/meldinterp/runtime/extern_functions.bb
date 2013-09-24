#include "extern_functions.h"
#include <stdio.h>

int blockID(int x) {
//    fprintf(stderr, "Returning %d\n", x);
	return x;
}

void printInt(int x, int y, int z) {
     fprintf(stderr, "%2d: %2d -> %d\n", x, y, z);
}

int gen_fresh_file() {
	static int f = 0;
	return f++;
}

int gen_fresh_tag() {
	static int t = 0;
	return t++;
}

int gen_fresh_nonce() {
	static int n = 0;
	return n++;
}