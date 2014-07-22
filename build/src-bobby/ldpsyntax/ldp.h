#ifndef _LDP_H_
#define _LDP_H_

typedef enum { Const, Function, Node, Field, Unknown } Itype;

typedef struct symbol {
  char* text;
  Itype type;
  int watermark;
  struct symbol* next;
} *Symbol;

typedef struct hashtable {
  int size;
  int entries;
  int watermark;
  Symbol* data;
} *Table;

void initTable(void);
Symbol insert(Table t, char* value);
Symbol lookup(Table t, char* value);
void pushWatermark(Table t);
void popWatermark(Table t);
void printTable(Table t);
char* itype2str(Itype type);

#endif
