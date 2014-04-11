// memory.c
//
// Implement Memory Management protocols

#ifndef _MEMORY_C_
#define _MEMORY_C_

#include "memory.bbh"

#ifdef TESTING
#define FILENUM 1
#include "message.bbh"
#endif

threaddef #define NUM_RXCHUNKS 12
threaddef #define NUM_TXCHUNKS 12

// types of chunks to free
#define RXCHUNK 0
#define TXCHUNK 1

threadvar byte numFreeChunks;
threadvar char allocated;	/* track num chunks allocated, signed val  */
threadvar Chunk rxChunks[NUM_RXCHUNKS];
threadvar Chunk txChunks[NUM_TXCHUNKS];

threadvar blockConf EEMEM nv_conf;
threadvar blockConf conf;

//////////////////// PUBLIC FUNCTIONS /////////////////////
// set-up memory
void initializeMemory(void)
{
    uint8_t i;

    // clear all status bits and next pointers

    // clear all status bits for receive chunks
    for( i=0; i<NUM_RXCHUNKS; i++ )
    {
        rxChunks[i].status = CHUNK_FREE;
	rxChunks[i].next = NULL;
    }
    
    // clear all status bits for receive chunks
    for( i=0; i<NUM_TXCHUNKS; i++ )
    {
        txChunks[i].status = CHUNK_FREE;
	txChunks[i].next = NULL;
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

  while(c != NULL) {
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
	  assert((current[i]).next == NULL);
	  (current[i]).next = NULL;
	  allocated++;
	  return &(current[i]);
        }
        // else, in use (supposedly)
    }
    // this assumes NUM_TXCHUNKS <= NUM_RXCHUNKS
    assert(allocated >= NUM_TXCHUNKS);
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

// check a pool for consistency, return number in use
byte checkMemoryPool(Chunk* pool, byte num)
{
  byte used = 0;
  for(byte i=0; i<num; i++ ) {
    Chunk* cp = &(pool[i]);
    if (chunkInUse(cp)) used++;
    else assert(cp->next == NULL);
  }
  return used;
}

// check function
void checkMemoryConsistency(void)
{
  int used = checkMemoryPool(rxChunks, NUM_RXCHUNKS);
  used += checkMemoryPool(txChunks, NUM_TXCHUNKS);
  assert(used == allocated);
}

////////////////// END PUBLIC FUNCTIONS ///////////////////

#endif
