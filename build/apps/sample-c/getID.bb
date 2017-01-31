#include <stdio.h>
#include "block.bbh"

void myMain(void) {

  setColor(WHITE);
  #ifdef LOG_DEBUG
    char s[12];
    snprintf(s, 12*sizeof(char), "Uid: %hu", getGUID() );
    printDebug(s);
  #endif

  while(1) {
    delayMS(50);
  }
}

void userRegistration(void) {
	registerHandler(SYSTEM_MAIN, (GenericHandler)&myMain);
}
