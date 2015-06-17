#include <pthread.h>

#include "../system/myassert.h"
#include "block.h"
#include "block_dispatch.h"
#include "unistd.h"

extern int blockProgram(void);
extern void blockTick(void);
extern pthread_mutex_t printmutex;

static pthread_key_t key;
static pthread_once_t key_once = PTHREAD_ONCE_INIT;

/* private methods */
static void makeKey(void);
static void *launchBlockProgram(Block *b);
static void *callBlockTick(Block *b);
static void installThread(Block *b);

Block *this(void)
{
	Block* ptr = (Block *) pthread_getspecific(key);
	assert(ptr != NULL);
       
	return ptr;
}

void startBlock(Block *block)
{
	pthread_attr_t attr;
	pthread_attr_init(&attr);
	pthread_attr_setdetachstate(&attr, PTHREAD_CREATE_JOINABLE);

	int status = pthread_create(&(block->threadID), &attr,
			(BlockProgram) launchBlockProgram, block);
	assert(status == 0);

    // thread that asserts blockTick (emulates the interrupt)
	status = pthread_create(&(block->blockTickID), &attr,
            (BlockProgram)callBlockTick, block);
	assert(status == 0);
}

static void makeKey(void)
{
	assert(pthread_key_create(&key, NULL)==0);
}

static void installThread(Block *b)
{
	Block** ptr;
	pthread_once(&key_once, makeKey);
	ptr = pthread_getspecific(key);

	assert(ptr == NULL && pthread_setspecific(key, b) == 0);
}

static void* launchBlockProgram(Block *b)
{
	installThread(b);

    pthread_mutex_lock(&printmutex);
	fprintf(stderr, "Block id: %s\n", nodeIDasString(b->id, 0));
    pthread_mutex_unlock(&printmutex);

	// call user program
	int x = blockProgram();

	pthread_exit((void*) x);
	return 0;
}

// emulates the interrupt that triggers blockTick
static void* callBlockTick(Block *b)
{
    // set-up the block references correctly
	installThread(b);

    // just keep looping and call blockTick
    while(1)
    {
        usleep(1000);
	//blockprint(stderr, "call tick\n");
        blockTick();
    }

	pthread_exit(0);
	return 0;
}


