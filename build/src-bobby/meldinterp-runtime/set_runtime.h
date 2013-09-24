
#ifndef SET_RUNTIME_H
#define SET_RUNTIME_H

#include <stddef.h>

typedef void* set_data;

typedef int (*compare_set_fn)(set_data, set_data);
typedef void (*print_set_fn)(set_data);

typedef struct _set_descriptor {
  compare_set_fn cmp_fn;
  compare_set_fn equal_fn;
  print_set_fn print_fn;
  size_t size_elem;
} set_descriptor;

typedef struct _Set {
	unsigned char* start;
	int nelems;
  set_descriptor *descriptor;
} Set;

void set_init_descriptors(void);

Set *set_int_create(void);
Set *set_float_create(void);

void set_insert(Set *set, set_data data);
void set_int_insert(Set *set, int data);
void set_float_insert(Set *set, float data);

int set_equal(Set *set1, Set *set2);

void set_delete(Set *set);

void set_print(Set *set);

static inline
int set_total(Set *set)
{
	return set->nelems;
}

#endif /* SET_RUNTIME_H */
