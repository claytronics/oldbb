#include <pthread.h>
#include <malloc.h>
#include <string.h>

#include "../system/myassert.h"
#include "block.h"
#include "block_dispatch.h"
#include "unistd.h"
#include "sim.h"
#include "../hw-api/hwMemory.h"

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

static int maxThis = 0;
static Block** allThis = NULL;

static void* launchBlockProgram(Block *b)
{
  installThread(b);

  pthread_mutex_lock(&printmutex);
  fprintf(stderr, "Block id: %s\n", nodeIDasString(b->id, 0));
  while (b->id >= maxThis) {
    int old = maxThis;
    maxThis = (maxThis+1)*2;
    //fprintf(stderr, "Realloc: %d -> %d (for %s)\n", old, maxThis, nodeIDasString(b->id, 0));
    allThis = realloc(allThis, maxThis*sizeof(Block*));
    while (old < maxThis) allThis[old++] = 0;
  }
  allThis[b->id] = b;
  if (0)
  {
    int i;
    for (i=0; i<maxThis; i++) {
      fprintf(stderr, "%d: %p\n", i, allThis[i]);
    }
  }
  pthread_mutex_unlock(&printmutex);

  // call user program
  int x = blockProgram();
  int* xp = malloc(sizeof(int));
  *xp = x;
  pthread_exit((void*) xp);
  return 0;
}

static int* destroyedBlocks = NULL;
static unsigned int* tickCount = NULL;
static unsigned int* prevTickCount = NULL;
static int maxBlock = 0;
static int maxId = 0;
static byte checkMode = 0;

// this block seems to have stopped calling blocktick
void
blockLocked(int x)
{
  pthread_mutex_lock(&printmutex);
  fprintf(stderr, "blocktick not running properly! @ %d\nTICKS:", x);
  int j;
  for (j=0; j<maxId; j++) {
    fprintf(stderr, " %4d", tickCount[j]);
  }
  fprintf(stderr, "\n PREV:");
  for (j=0; j<maxId; j++) {
    fprintf(stderr, " %4d", prevTickCount[j]);
  }
  fprintf(stderr, "\nDESTR:");
  for (j=0; j<maxId; j++) {
    fprintf(stderr, " %4d", destroyedBlocks[j]);
  }
  fprintf(stderr, "\n");
  pthread_mutex_unlock(&printmutex);
}

// checkMode == 0 -> we haven't copied anything recently.  If copyflag == 1, then copy
// checkMode == 1 -> we have copied old values, don't do anything unless copyflag == 0
void
saveTickCount(int copyflag)
{
  if (copyflag == checkMode) return;
  pthread_mutex_lock(&printmutex);
  if (copyflag == checkMode) {
    pthread_mutex_unlock(&printmutex);
    return;
  }
  //fprintf(stderr, "STC: %d %d\n", copyflag, tickCount[1]);
  int j;
  for (j=1; j<maxId; j++) {
    if (copyflag) {
      prevTickCount[j] = tickCount[j];
    } else {
      prevTickCount[j] = tickCount[j] = 0;
    }
  }
  checkMode = copyflag;
  pthread_mutex_unlock(&printmutex);
}

// emulates the interrupt that triggers blockTick
static 
void* callBlockTick(Block *b)
{
  // set-up the block references correctly
  installThread(b);

  // just keep looping and call blockTick
  while(1) {
    usleep(1000);

    int id = getGUID();
    if (id >= maxId) {
      // make sure we have room to record the info about this block
      pthread_mutex_lock(&printmutex);
      maxId = id+1;
      if (id >= maxBlock) {
        int newmax = (id+1)*2;
        tickCount = realloc(tickCount, newmax*sizeof(int));
        prevTickCount = realloc(prevTickCount, newmax*sizeof(int));
        memset(tickCount, 0, sizeof(int)*newmax);
        memset(prevTickCount, 0, sizeof(int)*newmax);

        destroyedBlocks = realloc(destroyedBlocks, newmax*sizeof(int));
        memset(destroyedBlocks+maxBlock, 0, (newmax-maxBlock)*sizeof(int));
        maxBlock = newmax;
      }
      //fprintf(stderr, "REALLOC:%d, %d, %d\n", id, maxId, maxBlock);
      pthread_mutex_unlock(&printmutex);
    }
    tickCount[id]++;

    // now scan list and see if anyone is left behind
    unsigned int base = tickCount[id];
    //fprintf(stderr, "Assign base=%d @ %d\n", base, id);
    int i;
    for (i=1; i<maxId; i++) {
      if (destroyedBlocks[i] == 1) continue;
      unsigned int diff = (base > tickCount[i]) ? base-tickCount[i] : tickCount[i] - base;
      if (diff > 400) {
        // we must have been here before, compare old to new.  If all
        // have changed, reset and start over.
        int j;
        for (j=1; j<maxId; j++) {
          if (destroyedBlocks[j] == 1) continue;
          if (tickCount[j] == prevTickCount[j]) {
            // there was no change on this block
            blockLocked(j);
            break;
          }
        }
        saveTickCount(0);
      }

      if (diff > 200) {
        // save current counts
        saveTickCount(1);
        break;
      }
    }

    if (this()->destroyed == 1) {
      blockprint(stderr, "I AM DESTROYED!!\n");
      // let neighbors know we are gone
      //tellNeighborsDestroyed(this());
      // and never do anything again
      destroyedBlocks[id] = 1;
      pauseForever();
    }

    blockTick();
  }

  pthread_exit(0);
  return 0;
}


// Local Variables:
// mode: c
// tab-width: 8
// indent-tabs-mode: nil
// c-basic-offset: 2
// End:
