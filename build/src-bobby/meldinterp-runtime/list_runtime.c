
#include <stdlib.h>
#include <stdio.h>
#include <string.h>
#include "../system/myassert.h"

#include "list_runtime.h"
#include "api.h"
#include "model.h"

static list_descriptor *int_descriptor = NULL;
static list_descriptor *float_descriptor = NULL;
static list_descriptor *node_descriptor = NULL;

static inline List*
list_create(list_descriptor *descriptor)
{
	List *ret = (List*)malloc(sizeof(List));

	ret->total = 0;
  ret->descriptor = descriptor;
	ret->head = ret->tail = NULL;

	return ret;
}

static inline void*
list_create_node(List *list, list_element data, void *next)
{
	void *node = (void*)malloc(sizeof(void*) + list->descriptor->size_elem);

	LIST_NEXT(node) = next;

	memcpy(LIST_DATA(node), data, list->descriptor->size_elem);

	return node;
}

static inline void
list_push_head(List *list, list_element data)
{
	void *node = list_create_node(list, data, list->head);

	list->head = node;

	if(list->tail == NULL)
		list->tail = node;

	list->total++;
}

static inline void
list_push_tail_node(List *list, void *node)
{
	if(list->tail != NULL)
		LIST_NEXT(list->tail) = node;
	list->tail = node;
	if(list->head == NULL)
		list->head = node;
	LIST_NEXT(node) = NULL;
	list->total++;
}

static inline void
list_push_tail(List *list, list_element data)
{
	list_push_tail_node(list, list_create_node(list, data, NULL));
}

static inline void*
list_pop_head(List *list)
{
	if(list->head != NULL) {
		void *ptr = list->head;

		list->head = LIST_NEXT(ptr);

		if(list->head == NULL)
			list->tail = NULL;

		--list->total;

		return ptr;
	} else
		return NULL;
}

List *list_int_create(void)
{
  assert(int_descriptor != NULL);
  
	return list_create(int_descriptor);
}

List *list_float_create(void)
{
  assert(float_descriptor != NULL);
  
	return list_create(float_descriptor);
}

List *list_node_create(void)
{
	assert(node_descriptor != NULL);

	return list_create(node_descriptor);
}

void list_int_push_head(List *list, meld_int data)
{
  list_push_head(list, (list_element)&data);
}

void list_int_push_tail(List *list, meld_int data)
{
	list_push_tail(list, (list_element)&data);
}

void list_node_push_head(List *list, void *data)
{
	list_push_head(list, (list_element)&data);
}

void list_node_push_tail(List *list, void *data)
{
	list_push_tail(list, (list_element)&data);
}

void list_float_push_head(List *list, meld_float data)
{
  list_push_head(list, (list_element)&data);
}

void list_float_push_tail(List *list, meld_float data)
{
	list_push_tail(list, (list_element)&data);
}

bool list_is_float(List *list)
{
	return list->descriptor == float_descriptor;
}

bool list_is_int(List *list)
{
	return list->descriptor == int_descriptor;
}

bool list_is_node(List *list)
{
	return list->descriptor == node_descriptor;
}

List* list_int_from_vector(meld_int *vec, int size)
{
	List *list = list_int_create();
	int i;

	for(i = 0; i < size; ++i)
		list_int_push_tail(list, vec[i]);

	return list;
}

List* list_float_from_vector(meld_float *vec, int size)
{
	List *list = list_float_create();
	int i;

	for(i = 0; i < size; ++i)
		list_float_push_tail(list, vec[i]);

	return list;
}

List* list_node_from_vector(void **vec, int size)
{
	List *list = list_node_create();
	int i;

	for(i = 0; i < size; ++i) {
		list_node_push_tail(list, vec[i]);
	}

	return list;
}

void list_delete(List *list)
{
	void *chain = list->head;
	void *next;
	
	while(chain) {
		next = LIST_NEXT(chain);
		free(chain);
		chain = next;
	}
	free(list);
}

int list_equal(List *list1, List *list2)
{
  if(list1->descriptor != list2->descriptor)
    return 0;
    
	if(list_total(list1) != list_total(list2))
		return 0;
	
	list_iterator it1 = list_get_iterator(list1);
	list_iterator it2 = list_get_iterator(list2);

	while(list_iterator_has_next(it1)) {
	  if(!list1->descriptor->equal_fn(list_iterator_data(it1), list_iterator_data(it2)))
			return 0;

		it1 = list_iterator_next(list1);
		it2 = list_iterator_next(list2);
	}

	return 1; /* they are equal! */
}

void list_print(List *list)
{
	list_iterator it = list_get_iterator(list);

	printf("LIST %p with %d nodes:\n", list, list->total);

	printf("\t");
	while (list_iterator_has_next(it)) {
    list->descriptor->print_fn(list_iterator_data(it));
		it = list_iterator_next(it);
		if (list_iterator_has_next(it))
			printf(" ");
	}
	printf("\n");
}

void list_reverse_first(List *list)
{
	void *node = list_pop_head(list);

	if (node) {
		list_push_tail_node(list, node);
	}
}

List* list_copy(List *list)
{
	List *clone = list_create(list->descriptor);
	list_iterator it = list_get_iterator(list);

	while(list_iterator_has_next(it)) {
		list_push_tail(clone, list_iterator_data(it));

		it = list_iterator_next(it);
	}

	return clone;
}

#define MAXLEN 20

char*
convert_meld_int_safe(meld_int data)
{
    static char buffer[MAXLEN];
    int i=MAXLEN-2;
    buffer[MAXLEN-1] = 0;
    while (data) {
	int d = data % 10;
	buffer[i--] = '0'+d;
	data = data / 10;
    }
    return buffer+i+1;
}

static void
print_int_list_elem(list_element data)
{
    if (sizeof(meld_int) <= sizeof(int))
	printf("%d", (int)MELD_INT(data));
    else 
	printf("%s", convert_meld_int_safe(MELD_INT(data)));
}

static bool
equal_int_list_elem(list_element el1, list_element el2)
{
  return MELD_INT(el1) == MELD_INT(el2);
}

static void
print_float_list_elem(list_element data)
{
    printf("%f", (double)(MELD_FLOAT(data)));
}

static bool
equal_float_list_elem(list_element el1, list_element el2)
{
  return MELD_FLOAT(el1) == MELD_FLOAT(el2);
}

static void
print_node_list_elem(list_element data)
{
#ifdef PARALLEL_MACHINE
	printf(NODE_FORMAT, MELD_NODE(data)->order);
  printf(" %p", MELD_NODE(data));
#else
#ifdef BBSIM
	printf(NODE_FORMAT, MELD_NODE(data)->id);
#endif
#endif
}

static bool
equal_node_list_elem(list_element el1, list_element el2)
{
	return MELD_PTR(el1) == MELD_PTR(el2);
}

void
list_init_descriptors(void)
{
  int_descriptor = malloc(sizeof(list_descriptor));
  int_descriptor->size_elem = sizeof(meld_int);
  int_descriptor->print_fn = print_int_list_elem;
  int_descriptor->equal_fn = equal_int_list_elem;
  
  float_descriptor = malloc(sizeof(list_descriptor));
  float_descriptor->size_elem = sizeof(meld_float);
  float_descriptor->print_fn = print_float_list_elem;
  float_descriptor->equal_fn = equal_float_list_elem;

	node_descriptor = malloc(sizeof(list_descriptor));
	node_descriptor->size_elem = sizeof(void*);
	node_descriptor->print_fn = print_node_list_elem;
	node_descriptor->equal_fn = equal_node_list_elem;
}
