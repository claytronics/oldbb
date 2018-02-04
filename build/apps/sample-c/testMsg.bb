#include <stdlib.h> // min
#include "block.bbh"
#include "bbassert.bbh"
#include "message.bbh"

#define __MY_FILENAME__ "testMsg.bb"
			       
threadextern Chunk* thisChunk;

threadvar Timer testMsgTimer;

Color col;

// msg senders
static byte TestMsg_HANDLER(void) {
  col = (col + 1) % NUM_COLORS; 
  setColor(col);
  return 1;
}

// msg senders
static void sendTestMsg(PRef p) {
  byte data[1];
  byte s = 0;
  data[0] = 0;

  //PRef dest, byte *data, byte length, MsgHandler mh, GenericHandler cb
  s = sendUserMessage(p,data,1, (MsgHandler) &TestMsg_HANDLER, (GenericHandler)&defaultUserMessageCallback);
}

// callbacks
static void startTestMsg(void) {
  PRef p = 0;

  for (p = 0; p < NUM_PORTS; p++) {
    if (thisNeighborhood.n[p] == VACANT) {
      continue;
    }
    sendTestMsg(p);
  }
}

/** MAIN **/
void myMain(void) {

  setColor(WHITE);

  col = 0;

  testMsgTimer.t.callback = (GenericHandler)&startTestMsg;
  testMsgTimer.period = 500;

  registerTimer(&(testMsgTimer));
  enableTimer(testMsgTimer);
  
  while (1) {
    delayMS(50);
  }
}

void userRegistration(void) {
  registerHandler(SYSTEM_MAIN, (GenericHandler)&myMain);
  //registerHandler(EVENT_NEIGHBOR_CHANGE, (GenericHandler)&abc2HandleNeighborChange);
}

