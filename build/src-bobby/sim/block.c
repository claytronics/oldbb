#include <stdlib.h>

#include "block.h"

static unsigned int nextBlockId = 1;
static BlockList blockList;  // = Q_STATIC_INIT;

// initializes the access mutex
void initBlockList()
{
  Q_INIT_HEAD(&blockList);
}

Block *createBlock(int x, int y, int z)
{
	Block* newBlock;

	newBlock = calloc(sizeof(Block), 1);
	if (newBlock == NULL)
		return newBlock;

	newBlock->id = nextBlockId++;
	newBlock->x = x;
	newBlock->y = y;
	newBlock->z = z;
	newBlock->destroyed = 0;

	//
	pthread_mutex_init( &(newBlock->tapMutex), NULL);

	fprintf(stderr, "made block, inserting into Q");
	
	// this is redundant???  done in Q_INSERT_TAIL
	newBlock->blockLink.prev = (&blockList)->tail;
	Q_INSERT_TAIL(&blockList, newBlock, blockLink);

	return newBlock;
}

void destroyBlock(Block *block)
{
	Q_REMOVE(&blockList, block, blockLink);
	block->destroyed = 1;

	// uh...need to shut down other mutexes and memory in threads?!
	free(block);
}

BlockList *getBlockList(void)
{
	return &blockList;
}

// print out the right kind of NodeID

char*
nodeIDasString(NodeID x, char* sid)
{
    static char ssid[10];
    if (sid == 0) sid = ssid;
    sprintf(sid, "%u", x);
    return sid;
}
