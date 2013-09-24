#ifndef _BLOCK_H_
#define _BLOCK_H_

#include "variable_queue.h"

typedef char bool;
#define false 0
#define true 1

typedef void* (*BlockProgram)(void *);

typedef struct _Block Block;

#include "world.h"

#include <pthread.h>
#include <stdint.h>

/* define a new BlockList type */
Q_NEW_HEAD(BlockList, Block);

/* user local types */

#include "../system/localdefs.h"

#include "../system/localtypes.h"

struct _Block
{
	/* unique block indentification */
	NodeID id;

	/* block physical properties */
	int x;
	int y;
	int z;
  
  int simLEDr, simLEDg, simLEDb, simLEDi;

  // communication stuff
  pthread_mutex_t neighborMutex;
  pthread_mutex_t sendQueueMutex;

  //flag to prevent access to this block until it has completed startup
  int blockReady;

  pthread_mutex_t tapMutex;
  int tapBuffer;

    // when we destroy a block we mark it as such
    int destroyed;

	/* linked list */
	Q_NEW_LINK(Block) blockLink;

	/* thread */
	pthread_t threadID;
	pthread_t blockTickID;

	/* local variables for user program */
	#include "../system/localvars.h"
};

#include "block_dispatch.h"

void initBlockList(void);
Block *createBlock(int x, int y, int z);
void destroyBlock(Block *block);
void startBlock(Block *block);
BlockList *getBlockList(void);

char* nodeIDasString(NodeID x, char* sid);
#endif
