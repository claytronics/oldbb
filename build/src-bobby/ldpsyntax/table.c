#include <stdio.h>
#include "ldp.h"

Table symbols;

static int 
hash(char* x, int mod)
{
  unsigned int v = *++x;
  while (*x != 0) {
    v *= *x;
    x++;
  }
  return v % mod;
}

void initTable(void)
{
  symbols = calloc(1, sizeof(struct hashtable));
  symbols->size = 1023;
  symbols->entries = 0;
  symbols->watermark = 0;
  symbols->data = calloc(symbols->size, sizeof(Symbol));
}

Symbol
lookup(Table t, char* val)
{
  int h = hash(val, t->size);
  Symbol list = t->data[h];
  while ((list != 0) && (strcmp(list->text, val) != 0)) list = list->next;
  return list;
}

Symbol
insert(Table t, char* val)
{
  Symbol list = lookup(t, val);
  if (list == 0) {
    Symbol s = calloc(1, sizeof(struct symbol));
    s->text = strdup(val);
    s->type = Unknown;
    s->watermark = t->watermark;
    int h = hash(val, t->size);
    s->next = t->data[h];
    t->data[h] = s;
    t->entries++;
    list = s;
  }
  return list;
}

void
pushWatermark(Table t)
{
  t->watermark++;
}

void
popWatermark(Table t)
{
  int w = t->watermark--;
  int i;
  for (i=0; i<t->size; i++) {
    Symbol s = t->data[i];
    while ((s != 0) && (s->watermark == w)) {
      Symbol d = s;
      s = s->next;
      free(d->text);
      free(d);
      t->data[i] = s;
      t->entries--;
    }
  }
}

void
printTable(Table t)
{
  fprintf(stderr, "Table with %d entries of %d size at watermark %d\n", t->entries, t->size, t->watermark);
  int i;
  for (i=0; i<t->size; i++) {
    Symbol s = t->data[i];
    while (s != 0) {
      fprintf(stderr, "%30s\t%s\t%d\n", s->text, itype2str(s->type), s->watermark);
      s = s->next;
    }
  }
}
