# 1 "/seth/claycvs/svn/blinkyblocks/newcode/src/meldinterp/runtime/meldvm.bb"
#include "bb.h"
#include "led.h"
#include "ensemble.h"

#include <unistd.h>
#include <stdlib.h>
#include <string.h>


#include <sys/types.h>

#define BLOCK_ID  (read_fcn_id())

extern const unsigned char meld_prog[];
extern void* (*extern_functs[])();
extern int extern_functs_args[];


#define IF(x)     (((*(const unsigned char*)(x))&0xe0) == 0x60)
#define ELSE(x)   ((*(const unsigned char*)(x)) == 0x02)
#define ENDIF(x)  ((*(const unsigned char*)(x)) == 0x03)
#define ITER(x)   (((*(const unsigned char*)(x))&0xc0) == 0xc0)
#define NEXT(x)   ((*(const unsigned char*)(x)) == 0x01)
#define SEND(x)   (((*(const unsigned char*)(x))&0xfc) == 0x08)
#define OP(x)     (((*(const unsigned char*)(x))&0xe0) == 0x40)
#define MOVE(x)   (((*(const unsigned char*)(x))&0xf0) == 0x30)
#define ALLOC(x)  (((*(const unsigned char*)(x))&0xc0) == 0x80)
#define RETURN(x) ((*(const unsigned char*)(x)) == 0x00)
#define CALL(x)   (((*(const unsigned char*)(x)) & 0xf0) == 0x20)

#define IF_REG(x)     ((*(const unsigned char*)(x))&0x1f)
#define ITER_TYPE(x)  ((*(const unsigned char*)(x))&0x3f)
#define ITER_MATCH_END(x)   (((*(const unsigned char*)((x)+1))&0xc0) == 0x80)
#define ITER_MATCH_NONE(x)  (((*(const unsigned char*)((x)+1))&0xc0) == 0xc0)
#define ITER_MATCH_OFF(x)   (((*(const unsigned char*)(x))&0xf0) >> 4)
#define ITER_MATCH_LEN(x)   (((*(const unsigned char*)(x))&0x0f))
#define ITER_MATCH_VAL(x)   (((*(const unsigned char*)((x)+1))&0x3f))
#define SEND_MSG(x)   ((((*(const unsigned char*)(x))&0x3) << 3) | \
                       (((*(const unsigned char*)((x)+1))&0xe0) >> 5))
#define SEND_DST(x)   ((*(const unsigned char*)((x)+1))&0x1f)
#define OP_ARG1(x)    ((((*(const unsigned char*)((x)+1))&0xfc) >> 2))
#define OP_ARG2(x)    (((*(const unsigned char*)((x)+2))&0x3f))
#define OP_OP(x)      ((((*(const unsigned char*)((x)+1))&0x3) << 2) | \
                      (((*(const unsigned char*)((x)+2))&0xc0) >> 6))
#define OP_DST(x)     ((*(const unsigned char*)(x))&0x1f)
#define MOVE_SRC(x)   ((((*(const unsigned char*)(x))&0xf) << 2) | \
					   (((*(const unsigned char*)((x)+1))&0xc0) >> 6))
#define MOVE_DST(x)   (((*(const unsigned char*)((x)+1))&0x3f))
#define ALLOC_TYPE(x) ((*(const unsigned char *)(x))&0x3f)
#define ALLOC_DST(x)  ((*(const unsigned char *)((x)+1))&0x3f)

#define CALL_VAL(x)   (*(const unsigned char *)(x))
#define CALL_DST(x)   ((*(const unsigned char *)((x)+1)) & 0x1f)
#define CALL_ID(x)    ((((*(const unsigned char *)((x))) & 0x0f) << 3) | \
						(((*(const unsigned char *)((x)+1)) & 0xe0) >> 5))

#define CALL_ARGS(x)  (extern_functs_args[CALL_ID(x)])
#define CALL_FUNC(x)  (extern_functs[CALL_ID(x)])

#define OP_NEQ       0x0
#define OP_EQ        0x1
#define OP_LESS      0x2
#define OP_LESSEQ    0x3
#define OP_GREATER   0x4
#define OP_GREATEREQ 0x5
#define OP_MODF      0x6
#define OP_MODI      0x7
#define OP_PLUSF     0x8
#define OP_PLUSI     0x9
#define OP_MINUSF    0xa
#define OP_MINUSI    0xb
#define OP_TIMESF    0xc
#define OP_TIMESI    0xd
#define OP_DIVF      0xe
#define OP_DIVI      0xf

#define VAL_IS_REG(x)   (((const unsigned char)(x)) & 0x20)
#define VAL_IS_TUPLE(x) (((const unsigned char)(x)) == 0x1f)
#define VAL_IS_FLOAT(x) (((const unsigned char)(x)) == 0x00)
#define VAL_IS_INT(x)   (((const unsigned char)(x)) == 0x01)
#define VAL_IS_FIELD(x) (((const unsigned char)(x)) == 0x02)

#define VAL_REG(x) (((const unsigned char)(x)) & 0x1f)
#define VAL_FIELD_OFF(x) (((*(const unsigned char *)(x)) & 0xf0) >> 4)
#define VAL_FIELD_LEN(x) ((*(const unsigned char *)(x)) & 0x0f)
#define VAL_FIELD_REG(x) ((*(const unsigned char *)((x)+1)) & 0x1f)


#define TYPE_DESCRIPTOR_SIZE 4

#define TYPE_SIZE(x)   (meld_prog[1+TYPE_DESCRIPTOR_SIZE*(x)+2]+1)
#define TYPE_AGGREGATE(x) (meld_prog[1+TYPE_DESCRIPTOR_SIZE*(x)+3])
#define TYPE_START(x)  (&meld_prog[*(unsigned short *)&meld_prog[1+TYPE_DESCRIPTOR_SIZE*(x)]])


#define AGG_AGG(x)    (((x) & (0xf0)) >> 4)
#define AGG_FIELD(x)  ((x) & 0x0f)

#define AGG_NONE 0
#define AGG_FIRST 1
#define AGG_MAX 2
#define AGG_MIN 3
#define AGG_SUM 4

#define TYPE_NEIGHBOR		0
#define TYPE_NEIGHBORCOUNT	1
#define TYPE_VACANT			2
#define TYPE_SETCOLOR		3
#define TYPE_SETCOLOR2		4

#define TUPLE_TYPE(x)   (*(unsigned char *)(x))
#define TUPLE_FIELD(x,off)  ((void *)(((unsigned char*)(x)) + 1 + (off)))


#define NUM_TYPES  (meld_prog[0])


// typedef union {int count; struct tuple_entry *aggList;} recordType;
// struct tuple_entry { struct tuple_entry *next; recordType records; void *tuple; };

// struct tuple_entry **tuples;
// struct tuple_entry *newTuples;

// unsigned int reg[32];

void process_tuple(void *tuple, const unsigned char *pc, int isNew);
void handle_tuple(void *tuple, int isNew);
void send_tuple(void *tuple, int isNew);


void enqueue(struct tuple_entry **queue, void *tuple, recordType isNew)
{
	struct tuple_entry *entry = malloc(sizeof(struct tuple_entry));

	entry->tuple = tuple;
	entry->records = isNew;

	struct tuple_entry **spot;
	for (spot = queue;
		 *spot != NULL;
		 spot = &((*spot)->next));
	*spot = entry;
	entry->next = NULL;
}

void *dequeue(struct tuple_entry **queue, int *isNew)
{
	if (*queue == NULL)
		return NULL;

	struct tuple_entry *entry = *queue;
	*queue = (*queue)->next;

	if (isNew != NULL)
		*isNew = entry->records.count;
	void *tuple = entry->tuple;
	free(entry);

	return tuple;
}

int getNeighborID(int face)
{

	if (face == Top)
		return read_fcn_top();
	else if (face == Down)
		return read_fcn_bottom();
	else if (face == West)
		return read_fcn_left();
	else if (face == East)
		return read_fcn_right();
	else if (face == North)
		return read_fcn_front();
	else if (face == South)
		return read_fcn_back();
	else {
		assert(0);
		return -1;
	}
}

void enqueueFace(int neighbor, int face, int isNew)
{
	void *tuple = NULL;

	if (neighbor <= 0) {
		tuple = malloc(TYPE_SIZE(TYPE_VACANT));
		TUPLE_TYPE(tuple) = TYPE_VACANT;
		*(int *)TUPLE_FIELD(tuple, 0) = BLOCK_ID;
		*(int *)TUPLE_FIELD(tuple, sizeof(int)) = face;
	}
	else {
		tuple = malloc(TYPE_SIZE(TYPE_NEIGHBOR));
		TUPLE_TYPE(tuple) = TYPE_NEIGHBOR;
		*(int *)TUPLE_FIELD(tuple, 0) = BLOCK_ID;
		*(int *)TUPLE_FIELD(tuple, sizeof(int)) = neighbor;
		*(int *)TUPLE_FIELD(tuple, 2* sizeof(int)) = face;
	}

	enqueue(& (this()->newTuples) , tuple, (recordType) isNew);
}

void enqueueCount(int count, int isNew)
{
	void *tuple = malloc(TYPE_SIZE(TYPE_NEIGHBORCOUNT));
	TUPLE_TYPE(tuple) = TYPE_NEIGHBORCOUNT;
	*(int *)TUPLE_FIELD(tuple, 0) = BLOCK_ID;
	*(int *)TUPLE_FIELD(tuple, sizeof(int)) = count;
	enqueue(& (this()->newTuples) , tuple, (recordType) isNew);
}

int blockProgram(void)
{
	// init stuff
	 (this()->tuples)  = calloc(NUM_TYPES, sizeof(struct tuple_entry *));
	 (this()->newTuples)  = NULL;
	//setColor(0);
	setLED(128,0,128,32);

	// introduce intial set of axioms
	int neighbors[6];
	int numNeighbors = getNeighborCount();

	enqueueCount(numNeighbors, 1);

	int i;
	for (i = 0; i < NumFaces; i++) {
		neighbors[i] = getNeighborID(i);

		enqueueFace(neighbors[i], i, 1);
	}

	// loop forever, processing new facts and updating axioms
	while(1) {

		// loop for new facts to process
		int isNew = 0;
		void *tuple = dequeue(& (this()->newTuples) , &isNew);

		if (tuple != NULL) {
			handle_tuple(tuple, isNew);
		}
		else {
			// if we've processed anything, sleep for the sake of letting other blocks run in the simulator
			sleep(1);
			// check for new messages
			pollFaces();
		}


		// update axioms based upon any changes
		int newNumNeighbors = getNeighborCount();
		if (newNumNeighbors != numNeighbors) {
			enqueueCount(numNeighbors, -1);
			numNeighbors = newNumNeighbors;
			enqueueCount(numNeighbors, 1);
		}

		for (i = 0; i < NumFaces; i++) {
			int neighbor = getNeighborID(i);

			if (neighbor == neighbors[i])
				continue;

			enqueueFace(neighbors[i], i, -1);
			neighbors[i] = neighbor;
			enqueueFace(neighbors[i], i, 1);
		}
	}
}

 void receive_tuple(Face, int tuple, int isNew);

 void
receive_tuple(Face face, int tuple, int isNew)
{
	/* TODO-REAL: needs to alloc on real blinky blocks??? */
	enqueue(& (this()->newTuples) , (void *)tuple, (recordType) isNew);
}

void send_tuple(void* tuple, int isNew)
{
	int target = *(int *)TUPLE_FIELD(tuple, 0);
	if (target == BLOCK_ID) {
		enqueue(& (this()->newTuples) , tuple, (recordType) isNew);
	}
	else {
		int face = -1;

		if (target == read_fcn_top())
			face = Top;
		else if (target == read_fcn_bottom())
			face = Down;
		else if (target == read_fcn_left())
			face = West;
		else if (target == read_fcn_right())
			face = East;
		else if (target == read_fcn_front())
			face = North;
		else if (target == read_fcn_back())
			face = South;

		if (face != -1)
			send2(face, MSGreceive_tuple, (int)tuple, isNew);
		/* TODO-REAL: needs to free on real blinky blocks??? */
	}
}

int accumulate(int aggType, int old, int new)
{
	switch (aggType) {
		case AGG_FIRST:
			return 0;

		case AGG_MAX:
			if (new > old)
				return 1;
			else
				return 0;

		case AGG_MIN:
			if (new < old)
				return 1;
			else
				return 0;

		case AGG_SUM:
			return new + old;
	}

	assert(0);
	while(1);
}

void recalcAggregate(struct tuple_entry *agg)
{
	unsigned char type = TUPLE_TYPE(agg->tuple);

	struct tuple_entry *cur;

	int accumulator = *(int *)TUPLE_FIELD(agg->records.aggList->tuple, sizeof(int) * AGG_FIELD(TYPE_AGGREGATE(type)));

	for (cur = agg->records.aggList->next; cur != NULL; cur = cur->next) {
		accumulator = accumulate(AGG_AGG(TYPE_AGGREGATE(type)),
				accumulator,
				*(int *)TUPLE_FIELD(cur->tuple, sizeof(int) * AGG_FIELD(TYPE_AGGREGATE(type))));
	}

	if (*(int *)TUPLE_FIELD(agg->tuple, sizeof(int) * AGG_FIELD(TYPE_AGGREGATE(type))) != accumulator) {
		process_tuple(agg->tuple, TYPE_START(type), -1);
		*(int *)TUPLE_FIELD(agg->tuple, sizeof(int) * AGG_FIELD(TYPE_AGGREGATE(type))) = accumulator;
		process_tuple(agg->tuple, TYPE_START(type), 1);
	}
}


void handle_tuple(void* tuple, int isNew)
{
	unsigned char type = TUPLE_TYPE(tuple);

	switch (type) {
		case TYPE_SETCOLOR:
			setLED(*(byte *)TUPLE_FIELD(tuple, 1),
				   *(byte *)TUPLE_FIELD(tuple, 2),
				   *(byte *)TUPLE_FIELD(tuple, 3),
				   *(byte *)TUPLE_FIELD(tuple, 4));
			free(tuple);
			break;


		case TYPE_SETCOLOR2:
			setColor((*(int *)TUPLE_FIELD(tuple, 4)) % NUM_COLORS);
			free(tuple);
			break;

		default:
			if (AGG_AGG(TYPE_AGGREGATE(type)) == AGG_NONE) {
				struct tuple_entry** current;
				for (current = & (this()->tuples) [type];
					 *current != NULL;
					 current = &(*current)->next) {

					if (memcmp((*current)->tuple,
							   tuple,
							   TYPE_SIZE(type)) == 0) {
						(*current)->records.count += isNew;

						if ((*current)->records.count <= 0) {
							process_tuple(tuple, TYPE_START(TUPLE_TYPE(tuple)), -1);

							// remove it
							struct tuple_entry *old;

							old = *current;
							*current = (*current)->next;

							// free some memory
							free(old->tuple);
							free(old);
						}
						free(tuple);
						return;
					}
				}

				// if deleting, return
				if (isNew <= 0) {
					free(tuple);
					return;
				}

				enqueue(&( (this()->tuples) [type]), tuple, (recordType) isNew);
				process_tuple(tuple, TYPE_START(TUPLE_TYPE(tuple)), isNew);
			}
			else {
				struct tuple_entry **current;
				for (current = & (this()->tuples) [type];
					 (*current) != NULL;
					 current = &(*current)->next) {
					if (memcmp((*current)->tuple,
							   tuple,
							   1+sizeof(int) * AGG_FIELD(TYPE_AGGREGATE(type))) == 0
						&&
					   (memcmp(((char*)(*current)->tuple) + 1 + sizeof(int) * (AGG_FIELD(TYPE_AGGREGATE(type))+1),
							   ((char*)tuple) + 1 + sizeof(int) * (AGG_FIELD(TYPE_AGGREGATE(type))+1),
							   TYPE_SIZE(type)-(1+sizeof(int) * (AGG_FIELD(TYPE_AGGREGATE(type))+1))) == 0)) {

						struct tuple_entry** current2;
						for (current2 = &(*current)->records.aggList;
							 *current2 != NULL;
							 current2 = &(*current2)->next) {
							if (memcmp((*current2)->tuple,
									   tuple,
									   TYPE_SIZE(type)) == 0) {
								(*current2)->records.count += isNew;

								if ((*current2)->records.count <= 0) {
									// remove it
									free(dequeue(current2, NULL));

									if ((*current)->records.aggList != NULL)
										recalcAggregate(*current);
									else {
										free(dequeue(current, NULL));
									}
								}
								free(tuple);
								return;
							}
						}


						// if deleting, return
						if (isNew <= 0) {
							free(tuple);
							return;
						}

						enqueue(&((*current)->records.aggList), tuple, (recordType) isNew);

						recalcAggregate(*current);
						return;
					}
				}

				// if deleting, return
				if (isNew <= 0) {
					free(tuple);
					return;
				}

				// So now we know we have a new tuple
				struct tuple_entry *aggList = NULL;
				void *tupleCpy = malloc(TYPE_SIZE(type));
				memcpy(tupleCpy, tuple, TYPE_SIZE(type));

				enqueue(&aggList, tuple, (recordType) isNew);
				enqueue(&( (this()->tuples) [type]), tupleCpy, (recordType)aggList);

				process_tuple(tuple, TYPE_START(TUPLE_TYPE(tuple)), isNew);
			}
			break;
	}
#if 0
	unsigned int type = tuple[0];
	unsigned int pc = *(unsigned short *)&meld_prog[1+3*type];

	bzero( (this()->reg) , sizeof(unsigned int) * 32);

	instr = &meld_prog[pc];
#endif
}

const unsigned char *advance(const unsigned char *pc) {
	if (SEND(pc)) {
		return pc+2;
	}
	else if(OP(pc)) {
		int count = 3;

		if (VAL_IS_FLOAT(OP_ARG1(pc))) {
			count += sizeof(float);
		}
		if (VAL_IS_FLOAT(OP_ARG2(pc))) {
			count += sizeof(float);
		}

		if (VAL_IS_INT(OP_ARG1(pc))) {
			count += sizeof(int);
		}
		if (VAL_IS_INT(OP_ARG2(pc))) {
			count += sizeof(int);
		}

		if (VAL_IS_FIELD(OP_ARG1(pc))) {
			count += 2;
		}
		if (VAL_IS_FIELD(OP_ARG2(pc))) {
			count += 2;
		}

		return pc+count;
	}
	else if(MOVE(pc)) {
		int count = 2;

		if (VAL_IS_FLOAT(MOVE_SRC(pc))) {
			count += sizeof(float);
		}
		if (VAL_IS_FLOAT(MOVE_DST(pc))) {
			count += sizeof(float);
		}

		if (VAL_IS_INT(MOVE_SRC(pc))) {
			count += sizeof(int);
		}
		if (VAL_IS_INT(MOVE_DST(pc))) {
			count += sizeof(int);
		}

		if (VAL_IS_FIELD(MOVE_SRC(pc))) {
			count += 2;
		}
		if (VAL_IS_FIELD(MOVE_DST(pc))) {
			count += 2;
		}

		return pc+count;
	}
	else if(ITER(pc)) {
		for (pc++; !ITER_MATCH_END(pc) && !ITER_MATCH_NONE(pc); pc+=2) {
			if (VAL_IS_FLOAT(ITER_MATCH_VAL(pc))) {
				pc += sizeof(float);
			}
			else if (VAL_IS_INT(ITER_MATCH_VAL(pc))) {
				pc += sizeof(int);
			}
			else if (VAL_IS_FIELD(ITER_MATCH_VAL(pc))) {
				pc += 2;
			}
		};

		return pc;
	}
	else if (ALLOC(pc)) {
		int count = 2;

		if (VAL_IS_INT(ALLOC_DST(pc))) {
			count += sizeof(int);
		}
		if	(VAL_IS_FLOAT(ALLOC_DST(pc))) {
			count += sizeof(float);
		}
		if	(VAL_IS_FIELD(ALLOC_DST(pc))) {
			count += 2;
		}

		return pc+count;
	}
	else if (CALL(pc)) {
		int numArgs = CALL_ARGS(pc);
		int i;
		for (i = 0, pc+=2; i < numArgs; i++, pc++) {
			if (VAL_IS_FLOAT(CALL_VAL(pc))) {
				pc += sizeof(float);
			}
			else if (VAL_IS_INT(CALL_VAL(pc))) {
				pc += sizeof(int);
			}
			else if (VAL_IS_FIELD(CALL_VAL(pc))) {
				pc += 2;
			}
		}
		return pc;
	}
	else {
		return pc+1;
	}
}


void *eval(const unsigned char value, void *tuple, const unsigned char **pc) {
	void *ret = NULL;

	if (VAL_IS_REG(value)) {
		ret = & (this()->reg) [VAL_REG(value)];
	} else if (VAL_IS_TUPLE(value)) {
		ret = tuple;
	} else if (VAL_IS_FIELD(value)) {
		//int len = VAL_FIELD_LEN(pc+1); // assume len == 4 for now...

		ret = (void *)( (this()->reg) [VAL_FIELD_REG(*pc)] + 1 + VAL_FIELD_OFF(*pc));
		(*pc) += 2;
	} else if (VAL_IS_INT(value)) {
		ret = (void *)(*pc);
		(*pc) += sizeof(int);
	} else if (VAL_IS_FLOAT(value)) {
		ret = (void *)(*pc);
		(*pc) += sizeof(float);
	} else {
		assert(0 /* invalid value */ );
	}

	return ret;
}

void process_tuple(void *tuple, const unsigned char *pc, int isNew)
{
	unsigned int goElse = 0;
	unsigned int goEndif = 0;
	unsigned int goNext = 0;

	for (; !(RETURN(pc)); pc = advance(pc)) {
		/* Ignore instructions until we get to the right place. Should implement jump... */
		if (goEndif) {
			if (IF(pc)) {
				goEndif++;
			}
			else if (ENDIF(pc)) {
				goEndif--;
			}
			continue;
		}
		else if (goElse) {
			if (IF(pc)) {
				goEndif++;
			} else if (ELSE(pc)) {
				goElse--;
			} else if (ENDIF(pc)) {
				goElse--;
			}
			continue;
		}
		else if (goNext) {
			if (IF(pc)) {
				goEndif++;
			}
			else if (NEXT(pc)) {
				goNext--;
			}
			else if (ITER(pc)) {
				goNext++;
			}
			continue;
		}

		/* perform an instruction */
		if (IF(pc)) {
			if (! (this()->reg) [IF_REG(pc)]) {
				goElse++;
			}
		} else if (ELSE(pc)) {
			goEndif++;
		} else if (ENDIF(pc)) {
			/* goElse and goEndif are zero, so do nothing */
		} else if (ITER(pc)) {

			struct tuple_entry *next_tuple =  (this()->tuples) [ITER_TYPE(pc)];

			/* iterate over all tuples of the appropriate type */
			for (next_tuple =  (this()->tuples) [ITER_TYPE(pc)];
				 next_tuple != NULL;
				 next_tuple = next_tuple->next) {

				unsigned char matched = 1;

				/* check to see if it matches */
				const unsigned char *tmppc;
				for (tmppc = pc+1; !ITER_MATCH_NONE(tmppc); tmppc+=2) {

					const unsigned char *old_pc = tmppc + 2;

					matched = matched &&
						(memcmp(TUPLE_FIELD(next_tuple->tuple, ITER_MATCH_OFF(tmppc)),
								   eval(ITER_MATCH_VAL(tmppc), &tuple, &old_pc),
								   ITER_MATCH_LEN(tmppc)) == 0);

					if (ITER_MATCH_END(tmppc)) break;
					tmppc = old_pc - 2;
				}

				if (matched)
					process_tuple(next_tuple->tuple, advance(pc), isNew);

			}

			/* advance the pc to the end of the loop */
			goNext++;

		} else if (NEXT(pc)) {
			return;
		} else if (SEND(pc)) {
			send_tuple((void *) (this()->reg) [SEND_MSG(pc)], isNew);
		} else if (OP(pc)) {
			const unsigned char *old_pc = pc+3;

			int arg1 = *(int *)eval(OP_ARG1(pc), &tuple, &old_pc);
			int arg2 = *(int *)eval(OP_ARG2(pc), &tuple, &old_pc);

			switch (OP_OP(pc)) {
			case OP_NEQ:
				 (this()->reg) [OP_DST(pc)] = (arg1 != arg2);
				break;

			case OP_EQ:
				 (this()->reg) [OP_DST(pc)] = (arg1 == arg2);
				break;

			case OP_LESS:
				 (this()->reg) [OP_DST(pc)] = (arg1 < arg2);
				break;

			case OP_LESSEQ:
				 (this()->reg) [OP_DST(pc)] = (arg1 <= arg2);
				break;

			case OP_GREATER:
				 (this()->reg) [OP_DST(pc)] = (arg1 > arg2);
				break;

			case OP_GREATEREQ:
				 (this()->reg) [OP_DST(pc)] = (arg1 >= arg2);
				break;

			case OP_MODF:
#if 0
				 (this()->reg) [OP_DST(pc)] = (((float)arg1) % ((float)arg2));
#endif
				break;

			case OP_MODI:
				 (this()->reg) [OP_DST(pc)] = (arg1 % arg2);
				break;

			case OP_PLUSF:
				 (this()->reg) [OP_DST(pc)] = (((float)arg1) + ((float)arg2));
				break;

			case OP_PLUSI:
				 (this()->reg) [OP_DST(pc)] = (arg1 + arg2);
				break;

			case OP_MINUSF:
				 (this()->reg) [OP_DST(pc)] = (((float)arg1) - ((float)arg2));
				break;

			case OP_MINUSI:
				 (this()->reg) [OP_DST(pc)] = (arg1 - arg2);
				break;

			case OP_TIMESF:
				 (this()->reg) [OP_DST(pc)] = (((float)arg1) * ((float)arg2));
				break;

			case OP_TIMESI:
				 (this()->reg) [OP_DST(pc)] = (arg1 * arg2);
				break;

			case OP_DIVF:
				 (this()->reg) [OP_DST(pc)] = (((float)arg1) / ((float)arg2));
				break;

			case OP_DIVI:
				 (this()->reg) [OP_DST(pc)] = (arg1 / arg2);
				break;
			}

		} else if (MOVE(pc)) {
			const unsigned char *old_pc = pc+2;

			int *src = (int *)eval(MOVE_SRC(pc), &tuple, &old_pc);
			int *dst = (int *)eval(MOVE_DST(pc), &tuple, &old_pc);

			*dst = *src;
		} else if (ALLOC(pc)) {
			const unsigned char *old_pc = pc+2;

			void **dst = (void **)eval(ALLOC_DST(pc), &tuple, &old_pc);
			*dst = malloc(TYPE_SIZE(ALLOC_TYPE(pc)));
			bzero(*dst, TYPE_SIZE(ALLOC_TYPE(pc)));
			TUPLE_TYPE(*dst) = ALLOC_TYPE(pc);
		} else if (CALL(pc)) {
		 	unsigned int *dst = & (this()->reg) [CALL_DST(pc)];
			unsigned int args[CALL_ARGS(pc)];

			assert(CALL_ARGS(pc) <= 5);

			int i;
			const unsigned char *old_pc = pc+2;
			for (i = 0; i < CALL_ARGS(pc); i++) {
				unsigned char value = CALL_VAL(old_pc);
				old_pc++;
				args[i] = *(int *)eval(value, &tuple, &old_pc);
			}

			// TODO: These casts on the return values of the functions should be unnecessary
			switch (CALL_ARGS(pc)) {
				default:
					break;

				case 0:
					*dst = (unsigned int)CALL_FUNC(pc)();
					break;

				case 1:
					*dst = (unsigned int)CALL_FUNC(pc)(args[0]);
					break;

				case 2:
					*dst = (unsigned int)CALL_FUNC(pc)(args[0], args[1]);
					break;

				case 3:
					*dst = (unsigned int)CALL_FUNC(pc)(args[0], args[1], args[2]);
					break;

				case 4:
					*dst = (unsigned int)CALL_FUNC(pc)(args[0], args[1], args[2], args[3]);
					break;

				case 5:
					*dst = (unsigned int)CALL_FUNC(pc)(args[0], args[1], args[2], args[3], args[4]);
					break;
			}
		}
	}

	return;
}
