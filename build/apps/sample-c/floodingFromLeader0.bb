#include "block.bbh"

/*********************/
/* Data for messages */
#define MYCHUNKS 12
extern Chunk* thisChunk;
Chunk myChunks[MYCHUNKS];
Chunk* getFreeUserChunk(void);

/*********************/
/** my functions    **/
uint8_t sendMessage(PRef p,uint8_t value);
uint8_t messageHandler(void);

void myMain(void) {
    // We are forced to use a small delay before program execution,
    // otherwise neighborhood may not be initialized yet
    setColor(WHITE);
    delayMS(300);

    if (getGUID()==100) {
        for (uint8_t port = 0; port < 6; port++) {
            if (thisNeighborhood.n[port] != VACANT) {
                sendMessage(port,1);
            }
        }
        setColor(RED);
    }
    while(1) {
      delayMS(1000);
    }
}

void userRegistration(void) {
  registerHandler(SYSTEM_MAIN, (GenericHandler)&myMain);
}

/*********************/
/* Send a message    */
/* to port p         */
/* carrying value    */
uint8_t sendMessage(PRef p,uint8_t value) {
  Chunk *c = getFreeUserChunk();

  if (c != NULL) {
    c->data[0] = value;
    if (sendMessageToPort(c, p, c->data, 1, messageHandler, NULL) == 0) {
      freeChunk(c);
      return 0;
    }
  }
  return 1;
}

uint8_t messageHandler(void) {
  if (thisChunk == NULL) return 0;
  uint8_t sender = faceNum(thisChunk); // port of the sender

  setColor(ORANGE);

  return 1;
}

/************************/
/* find a useable chunk */
Chunk* getFreeUserChunk(void) {
    Chunk* c;
    int i;

    for(i=0; i<MYCHUNKS; i++) {
        c = &(myChunks[i]);
        if( !chunkInUse(c) ) {
            return c;
        }
    }
    return NULL;
}
