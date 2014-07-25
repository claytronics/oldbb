const unsigned char meld_prog[] = {
/* NUMBER OF PREDICATES */
0xf, 
/* NUMBER OF RULES */
0x2, 
/* OFFSETS TO PREDICATE DESCRIPTORS */
0x24, 0, 
0x2a, 0, 
0x31, 0, 
0x3a, 0, 
0x42, 0, 
0x49, 0, 
0x50, 0, 
0x56, 0, 
0x5d, 0, 
0x63, 0, 
0x6a, 0, 
0x70, 0, 
0x78, 0, 
0x7f, 0, 
0x86, 0, 
/* OFFSETS TO RULE DESCRIPTORS */
0x8d, 0, 
0x92, 0, 
/* PREDICATE DESCRIPTORS */
0x98, 0, 0x4, 0, 0, 0, 
0x99, 0, 0x4, 0, 0x1, 0x1, 0x1, 
0x9a, 0, 0x4, 0, 0x1, 0x3, 0, 0, 0, 
0x9b, 0, 0x4, 0, 0x1, 0x2, 0x2, 0x9, 
0x9c, 0, 0x4, 0, 0x1, 0x1, 0x9, 
0x9d, 0, 0x4, 0, 0x1, 0x1, 0x1, 
0x9e, 0, 0x4, 0, 0x1, 0, 
0x9f, 0, 0, 0, 0x1, 0x1, 0, 
0xa0, 0, 0x4, 0, 0x1, 0, 
0xa1, 0, 0, 0, 0x1, 0x1, 0, 
0xa2, 0, 0, 0, 0x1, 0, 
0xa3, 0, 0, 0, 0x1, 0x2, 0x2, 0, 
0xa4, 0, 0, 0, 0x1, 0x1, 0, 
0xa5, 0, 0, 0, 0x1, 0x1, 0, 
0xd7, 0, 0, 0, 0x1, 0x1, 0, 
/* RULE DESCRIPTORS */
0x9, 0x1, 0, 0x1, 0, 
0x4b, 0x1, 0x1, 0x2, 0xe, 0xd, 
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
/* Predicate 12: */0, 
/* Predicate 13: */0x10, 0x1, 0, 0, 0, 0x2, 0, 0, 0, 0, 0, 0, 0, 0, 0xe, 0x1, 0x1, 0x15, 0, 0, 0, 0x2c, 0, 0, 0, 0, 0x11, 0x40, 0x7, 0x2, 0x22, 0, 0, 0x3, 0x22, 0, 0x1, 0x4, 0x3d, 0x3, 0x4, 0x3, 0x26, 0x3, 0, 0x2, 0x79, 0x2, 0x1, 0, 
/* Predicate 14: */0x10, 0x1, 0, 0, 0, 0x2, 0, 0, 0, 0, 0, 0, 0, 0, 0xd, 0x1, 0x1, 0x15, 0, 0, 0, 0x2c, 0, 0, 0, 0, 0x11, 0x40, 0x7, 0x2, 0x22, 0, 0x1, 0x3, 0x22, 0, 0, 0x4, 0x3d, 0x3, 0x4, 0x3, 0x26, 0x3, 0, 0x2, 0x79, 0x2, 0x1, 0, 
/* RULE BYTECODE */
/* Rule 0: */0x10, 0, 0, 0, 0, 0x5, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0x1, 0x15, 0, 0, 0, 0x3c, 0, 0, 0, 0, 0x11, 0x80, 0, 0x40, 0xd, 0, 0x1e, 0x3, 0, 0, 0, 0, 0, 0x78, 0, 0x40, 0xe, 0, 0x1e, 0x2, 0, 0, 0, 0, 0, 0x78, 0, 0x23, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0xf0, 0x1, 0, 
/* Rule 1: */0, };

char *tuple_names[] = {"_init", "set-priority", "setcolor", "setedgelabel", "write-string", "add-priority", "schedule-next", "setcolor2", "stop-program", "vacant", "tap", "neighbor", "neighborCount", "greenSocks", "yellowSocks", };

char *rule_names[] = {"init -o axioms", "!greenSocks(A), !yellowSocks(B) -o !setColor2(A + B).", };

#include "extern_functions.bbh"
Register (*extern_functs[])() = {};

int extern_functs_args[] = {};
