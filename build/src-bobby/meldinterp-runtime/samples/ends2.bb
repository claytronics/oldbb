const unsigned char meld_prog[] = {
/* NUMBER OF PREDICATES */
0xd, 
/* NUMBER OF RULES */
0x3, 
/* OFFSET TO PREDICATE DESCRIPTORS */
0xf, 
0x16, 
0x1e, 
0x28, 
0x31, 
0x39, 
0x41, 
0x48, 
0x50, 
0x57, 
0x5f, 
0x66, 
0x6f, 
/* PREDICATE DESCRIPTORS */
0x7d, 0, 0x4, 0, 0, 0, 0, 
0x7e, 0, 0x4, 0, 0x1, 0x1, 0, 0x1, 
0x7f, 0, 0x4, 0, 0x1, 0x3, 0, 0, 0, 0, 
0x80, 0, 0x4, 0, 0x1, 0x2, 0, 0x2, 0x9, 
0x81, 0, 0x4, 0, 0x1, 0x1, 0, 0x9, 
0x82, 0, 0x4, 0, 0x1, 0x1, 0, 0x1, 
0x83, 0, 0x4, 0, 0x1, 0, 0, 
0x84, 0, 0, 0, 0x1, 0x1, 0, 0, 
0x85, 0, 0x4, 0, 0x1, 0, 0, 
0x86, 0, 0, 0, 0x1, 0x1, 0, 0, 
0x87, 0, 0x4, 0, 0x1, 0, 0, 
0x88, 0, 0x20, 0, 0, 0x2, 0, 0x2, 0, 
0x89, 0, 0, 0, 0x1, 0x1, 0, 0, 
/* OFFSETS TO RULE BYTECODE */
0xd6, 0, 
0, 0x1, 
0x1, 0x1, 
/* PREDICATE BYTECODE */
/* Predicate 0: */0xd0, 
/* Predicate 1: */0, 
/* Predicate 2: */0, 
/* Predicate 3: */0, 
/* Predicate 4: */0, 
/* Predicate 5: */0, 
/* Predicate 6: */0, 
/* Predicate 7: */0, 
/* Predicate 8: */0, 
/* Predicate 9: */0, 
/* Predicate 10: */0, 
/* Predicate 11: */0, 
/* Predicate 12: */0x10, 0x1, 0, 0, 0, 0x22, 0, 0, 0x1, 0x1f, 0x1, 0, 0, 0, 0x2, 0x43, 0x1, 0x2, 0x1, 0x60, 0x1, 0x13, 0, 0, 0, 0x11, 0x40, 0x7, 0x1, 0x1e, 0x5, 0, 0, 0, 0, 0x1, 0x79, 0x1, 0x10, 0x2, 0, 0, 0, 0x22, 0, 0, 0x2, 0x1f, 0x1, 0, 0, 0, 0x3, 0x3b, 0x2, 0x3, 0x1, 0x60, 0x1, 0x13, 0, 0, 0, 0x11, 0x40, 0x7, 0x1, 0x1e, 0, 0, 0, 0, 0, 0x1, 0x79, 0x1, 0, 
/* RULE BYTECODE */
/* Rule 0: */0x10, 0, 0, 0, 0, 0x5, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0x1, 0x15, 0, 0, 0, 0x24, 0, 0, 0, 0, 0x11, 0x80, 0, 0x23, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0xf0, 0x1, 0, 
/* Rule 1: */0, 
/* Rule 2: */0, };

char *tuple_names[] = {"_init", "set-priority", "setcolor", "setedgelabel", "write-string", "add-priority", "schedule-next", "setcolor2", "stop-program", "vacant", "tap", "neighbor", "neighborCount", };

#include "extern_functions.bbh"
Register (*extern_functs[])() = {};

int extern_functs_args[] = {};
