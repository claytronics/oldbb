# 1 "/home/pthalamy/CMU/build-modif/src-bobby/system/memory.bb"
// memory.c
//
// Implement Memory Management protocols

#ifndef _MEMORY_C_
#define _MEMORY_C_

#include "memory.h"

 #define NUM_RXCHUNKS 12
 #define NUM_TXCHUNKS 12

// types of chunks to free
#define RXCHUNK 0
#define TXCHUNK 1

 byte numFreeChunks;
 Chunk rxChunks[NUM_RXCHUNKS];
 Chunk txChunks[NUM_TXCHUNKS];

 blockConf EEMEM nv_conf;
 blockConf conf;

//////////////////// PUBLIC FUNCTIONS /////////////////////
// set-up memory
void initializeMemory(void)
{
    uint8_t i;

    // clear all status bits for receive chunks
    for( i=0; i<NUM_RXCHUNKS; i++ )
    {
        rxChunks[i].status = CHUNK_FREE;
    }

    // clear all status bits for receive chunks
    for( i=0; i<NUM_TXCHUNKS; i++ )
    {
        txChunks[i].status = CHUNK_FREE;
    }

    // load config data
    //TODO: re-enable
    //restore(&conf, &nv_conf, sizeof(blockConf));
}

// this loops through and frees all connected Chunks in the list.
void freeChunk(Chunk * c)
{
  Chunk * tmp;

  while(c != NULL)
    {
      if(chunkInUse(c))
        {
	  c->status = CHUNK_FREE;
        }
      tmp = c->next;
      c->next = NULL;
      c = tmp;
    }
}

Chunk* getSystemChunk(byte which)
{
    int8_t i;
    Chunk*  current;

    if(which == RXCHUNK)
      {
	current = rxChunks;

	i = NUM_RXCHUNKS-1;
      }
    else
      {
	current = txChunks;

	i = NUM_TXCHUNKS-1;
      }

    // look for unused Chunk
    for(; i>=0; i--)
    {
        // check top bit to indicate usage
      if( !chunkInUse((&(current[i]))) )
        {
            // indicate in use
	  (current[i]).status = CHUNK_USED;

	  // clear old next ptr in case non-NULL
	  (current[i]).next = NULL;
	  return &(current[i]);
        }
        // else, in use (supposedly)
    }
    // none free!
    return NULL;
}

// return pointer to free memory Chunk
Chunk* getSystemRXChunk()
{
  return getSystemChunk(RXCHUNK);
}

Chunk* getSystemTXChunk()
{
  return getSystemChunk(TXCHUNK);
}

////////////////// END PUBLIC FUNCTIONS ///////////////////

#endif
