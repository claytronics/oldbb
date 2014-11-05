// memory.c
//
// Implement Memory Management protocols

#ifndef _MEMORY_C_
#define _MEMORY_C_

#include <string.h>
#include "memory.bbh"

#ifdef BBSIM
#include "../sim/sim.h"
#endif

#define FILENUM 1
#define checkMemoryConsistency(x) _checkMemoryConsistency(x, FILENUM, __LINE__)
#if 0
#ifdef TESTING
#define checkMemoryConsistency(x) _checkMemoryConsistency(x, FILENUM, __LINE__)
#include "message.bbh"
#include "log.bbh"
#else
#define checkMemoryConsistency(x)
#endif
#endif

#include "myassert.h"

threaddef #define NUM_RXCHUNKS 36
threaddef #define NUM_TXCHUNKS 36

// types of chunks to free
#define RXCHUNK 0
#define TXCHUNK 1

threadvar byte numFreeChunks;
threadvar char allocated;	/* track num chunks allocated, signed val  */
threadvar Chunk rxChunks[NUM_RXCHUNKS];
threadvar Chunk txChunks[NUM_TXCHUNKS];

threadvar blockConf EEMEM nv_conf;
threadvar blockConf conf;

void
initChunk(Chunk* cp)
{
    cp->status = CHUNK_FREE;
    cp->next = NULL;
    int j;
    for (j=0; j<DATA_SIZE; j++) cp->data[j] = 0;
    cp->callback = 0;
    *((MsgHandler*)cp->handler) = 0;
}

//////////////////// PUBLIC FUNCTIONS /////////////////////
// set-up memory
void initializeMemory(void)
{
    uint8_t i;

    // clear all status bits and next pointers
    // clear all status bits for receive chunks
    for( i=0; i<NUM_RXCHUNKS; i++ )
    {
        //blockprint(stderr, "RX:%d:%p\n", i, rxChunks+i);
        initChunk(rxChunks+i);
    }
    
    // clear all status bits for send chunks
    for( i=0; i<NUM_TXCHUNKS; i++ )
    {
        //blockprint(stderr, "TX:%d:%p\n", i, rxChunks+i);
        initChunk(txChunks+i);
    }

    // init allocation counters
    allocated = 0;


    // load config data
    //TODO: re-enable
    //restore(&conf, &nv_conf, sizeof(blockConf));
}

// this loops through and frees all connected Chunks in the list.
void freeChunk(Chunk * c)
{
  Chunk * tmp;	

  checkMemoryConsistency(0);
  while(c != NULL) {
      assert(chunkInUse(c));
    if(chunkInUse(c)) {
      c->status = CHUNK_FREE;
      allocated--;
      assert(allocated >= 0);
    }
    tmp = c->next;
    c->next = NULL;
    c = tmp;
  }
}

//static 
Chunk* getSystemChunk(byte which)
{
    checkMemoryConsistency(0);
    int8_t i;
    Chunk*  current;

    if(which == RXCHUNK) {
        current = rxChunks;
        
        i = NUM_RXCHUNKS-1;
    } else {
        current = txChunks;
        
        i = NUM_TXCHUNKS-1;
    }

    // look for unused Chunk
    for(; i>=0; i--) {
        // check top bit to indicate usage
        if( !chunkInUse((&(current[i]))) ) {
            // indicate in use
            Chunk* cp = &(current[i]);
            cp->status = CHUNK_USED;
            // clear old next ptr in case non-NULL
            assert(cp->next == NULL);
            cp->next = NULL;
            allocated++;
            checkMemoryConsistency(0);
            return cp;
        }
        // else, in use (supposedly)
    }
    // this assumes NUM_TXCHUNKS <= NUM_RXCHUNKS
    blockprint(stderr, "Out of chunks: %d\n", which);
    assert(allocated >= NUM_TXCHUNKS);
    checkMemoryConsistency(1);
    return NULL;  
}

// return pointer to free memory Chunk
Chunk* getSystemRXChunk(void)
{
  return getSystemChunk(RXCHUNK);
}

Chunk* 
getSystemTXChunk(void)
{
  return getSystemChunk(TXCHUNK);
}

// check a pool for consistency, return number in use
static byte 
checkMemoryPool(Chunk* pool, byte num, byte show)
{
  byte used = 0;
  byte i = 0;
  for( i=0; i<num; i++ ) {
    Chunk* cp = &(pool[i]);
    if (chunkInUse(cp)) used++;
    else assert(cp->next == NULL);
    if (show) {
        char buffer[256];
        blockprint(stderr, "C%2d: %s\n", i, chunk2str(cp, buffer));
    }
  }
  return used;
}


#if 0
// Can be used for further debugging if blocks often get out of memory
static void
sendOOM(int cln)
{
    char buffer[64];
    buffer[0] = cln;
    int j=0;
    for( byte i=0; i<NUM_RXCHUNKS; i++ ) {
        if (chunkInUse((&(rxChunks[i])))) {
            buffer[j++] = rxChunks[i].fn;
            buffer[j++] = rxChunks[i].ln;
        }
    }
    for( byte i=0; i<NUM_TXCHUNKS; i++ ) {
        if (chunkInUse((&(txChunks[i])))) {
            buffer[j++] = txChunks[i].fn;
            buffer[j++] = txChunks[i].ln;
        }
    }
    buffer[j] = 0;
    printDebug(buffer);
    setColor(INDIGO);
    delayMS(500);
}
#endif

// Memory check function, in case check fails, sends log to host with file and line of failed check
void 
_checkMemoryConsistency(byte show, byte cfn, int cln)
{
    int localAllocated = allocated;
    int rused = checkMemoryPool(rxChunks, NUM_RXCHUNKS, show);
    int wused = checkMemoryPool(txChunks, NUM_TXCHUNKS, show);
    int used = rused+wused;
    if ((wused > (NUM_TXCHUNKS>>1))||(rused > (NUM_RXCHUNKS>>1))) {
        blockprint(stderr, "RX:%d, TX:%d\n", rused, wused);
    }
    if (allocated != localAllocated) {
        blockprint(stderr, "allocated changed from %d -> %d  R:%d T:%d\n", localAllocated, allocated, rused, wused);
        return;
    }
    if (used != localAllocated) {
      blockprint(stderr, "RX:%d TX:%d allocated:%d\n", rused, wused, localAllocated);
      checkMemoryPool(rxChunks, NUM_RXCHUNKS, 1);
      checkMemoryPool(txChunks, NUM_TXCHUNKS, 1);
    }
    assert(used == localAllocated);
#if 0
#ifdef TESTING
  if (used != allocated) {
      setColor(
BROWN);
      while (1) {
          reportAssert(cfn, cln);
          delayMS(1000);
      }
  }
  //if (used > 12) sendOOM(cln);
#endif 
#endif
}

#ifdef BBSIM
char*
chunk2str(Chunk* chk, char* bp)
{
  sprintf(bp, "%p %c%c %c%c %d -> %p [%p]:", 
          chk,
	  chunkInUse(chk) ? 'U' : 'F',
	  chunkFilling(chk) ? '_' : '-',
	  (chk->status >> 4) & 1 ? 'N' : ' ',
	  (chk->status >> 3) & 1 ? 'A' : ' ',
	  faceNum(chk),
	  *((MsgHandler*)(chk->handler)),
	  chk->callback);
  int i;
  for (i=0; i<DATA_SIZE; i++) {
    char buf[12];
    sprintf(buf, " %02x", chk->data[i]);
    strcat(bp, buf);
  }
  return bp;
}
#endif

////////////////// END PUBLIC FUNCTIONS ///////////////////

#endif

// Local Variables:
// mode: C
// indent-tabs-mode: nil
// c-basic-offset: 4
// End:

