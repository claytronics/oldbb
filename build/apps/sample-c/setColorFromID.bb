#include "block.bbh"

void myMain(void) {
  
  if (getGUID()==100) {
    setColor(RED);
  } else {
    setColor(BLUE);
  }

  while(1) {
    delayMS(100);
  }
}

void userRegistration(void) {
	registerHandler(SYSTEM_MAIN, (GenericHandler)&myMain);
}
