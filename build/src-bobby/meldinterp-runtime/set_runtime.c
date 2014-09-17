
#include <stdlib.h>
#include <string.h>
#include <stdio.h>
#include "../system/myassert.h"

#include "set_runtime.h"

/******************************************************************************
@Description: Implementation file for the set data structures used by aggregates.
*******************************************************************************/

static set_descriptor *int_descriptor = NULL;
static set_descriptor *float_descriptor = NULL;

static inline Set*
set_create(set_descriptor *descriptor)
{
	Set* set = (Set*)malloc(sizeof(Set));

	set->start = NULL;
	set->nelems = 0;
  set->descriptor = descriptor;

	return set;
}

#define ALLOC_AND_SET(set, data, next, dest)                                        \
	dest = malloc(set->descriptor->size_elem + sizeof(void*));                        \
	memcpy(dest, data, set->descriptor->size_elem);                                   \
  *((void**)((unsigned char *)(dest) + set->descriptor->size_elem)) = next;         \
  set->nelems++

#define ADVANCE_NEXT(set, ptr) *(unsigned char**)(ptr + set->descriptor->size_elem)

void
set_insert(Set *set, set_data data)
{
	unsigned char *before = NULL;
	unsigned char *current = set->start;

	while (current) {
		set_data elem = current;

		if(set->descriptor->cmp_fn(data, elem)) {
			/* insert it here */

			/* is it repeated? */
			if(before && set->descriptor->equal_fn(data, before))
				return;

			if (before) {
				void **beforenext = (void**)(before + set->descriptor->size_elem);
				ALLOC_AND_SET(set, data, current, *beforenext);
			} else {
				ALLOC_AND_SET(set, data, current, set->start);
			}

			return;
		}

		/* go to next */
		before = current;
		current = ADVANCE_NEXT(set, current);
	}

	if (before) {
		/* repeated? */
		if (set->descriptor->equal_fn(data, before))
			return;

		void **beforenext = (void**)(before + set->descriptor->size_elem);
		ALLOC_AND_SET(set, data, NULL, *beforenext);
	} else {
		ALLOC_AND_SET(set, data, NULL, set->start);
	}
}

static int
compare_int_values(set_data a1, set_data a2)
{
	int i1 = *(int*)a1;
	int i2 = *(int*)a2;

	return i1 < i2;
}

static int
equal_int_values(set_data a1, set_data a2)
{
	int i1 = *(int*)a1;
	int i2 = *(int*)a2;

	return i1 == i2;
}

static void
print_int_value(set_data a)
{
	printf("%d", *(int*)a);
}

Set*
set_int_create(void)
{
  assert(int_descriptor != NULL);
	return set_create(int_descriptor);
}

static int
compare_float_values(set_data a1, set_data a2)
{
	float f1 = *(float*)a1;
	float f2 = *(float*)a2;

	return f1 < f2;
}

static int
equal_float_values(set_data a1, set_data a2)
{
	float f1 = *(float*)a1;
	float f2 = *(float*)a2;

	return f1 == f2;
}

static void
print_float_value(set_data a)
{
	printf("%f", *(double*)a);
}

Set*
set_float_create(void)
{
  assert(float_descriptor != NULL);
	return set_create(float_descriptor);
}

void
set_int_insert(Set* set, int data)
{
	set_insert(set, (set_data)&data);
}

void
set_float_insert(Set* set, float data)
{
	set_insert(set, (set_data)&data);
}

void
set_print(Set *set)
{
	printf("(Set-Union with %d elems, %Zu bytes each [", set->nelems, set->descriptor->size_elem);

	unsigned char* current = set->start;
	int isFirst = 1;

	while(current) {

		if(isFirst)
			isFirst = 0;
		else
			printf(", ");

		set->descriptor->print_fn((set_data)current);

		current = ADVANCE_NEXT(set, current);
	}

	printf("])\n");
}

int set_equal(Set *set1, Set *set2)
{
	if(set1->nelems != set2->nelems)
		return 0;

	if(set1->descriptor != set2->descriptor)
		return 0;

	unsigned char* current1 = set1->start;
	unsigned char* current2 = set2->start;

	while (current1) {
		set_data elem1 = (set_data)current1;
		set_data elem2 = (set_data)current2;

		if(!set1->descriptor->equal_fn(elem1, elem2))
			return 0;

		current1 = ADVANCE_NEXT(set1, current1);
		current2 = ADVANCE_NEXT(set1, current2);
	}

	return 1;
}

void set_delete(Set *set)
{
	unsigned char* current = set->start;
	unsigned char* next;

	while (current) {
		
		next = ADVANCE_NEXT(set, current);
		free(current);
		current = next;
	}

	free(set);
}

void
set_init_descriptors(void)
{
  int_descriptor = malloc(sizeof(set_descriptor));
  int_descriptor->size_elem = sizeof(int);
  int_descriptor->print_fn = print_int_value;
  int_descriptor->cmp_fn = compare_int_values;
  int_descriptor->equal_fn = equal_int_values;
  
  float_descriptor = malloc(sizeof(set_descriptor));
  float_descriptor->size_elem = sizeof(float);
  float_descriptor->print_fn = print_float_value;
  float_descriptor->cmp_fn = compare_float_values;
  float_descriptor->equal_fn = equal_float_values;
}
