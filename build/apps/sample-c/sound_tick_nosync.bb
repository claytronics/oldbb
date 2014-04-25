#include "block.bbh"

#include "block_config.bbh"
#include "memory.bbh"
//#include "hw-api/hwMemory.h"
#include "audio.bbh"

#define PERIOD (3000)

void myMain(void)
{
  initAudio();

  while (1) {
    delayMS(1000);
    chirp(40000,1);
    setNextColor();
  }

  while(1);
}
void userRegistration(void)
{
  registerHandler(SYSTEM_MAIN, (GenericHandler)&myMain);	
}
