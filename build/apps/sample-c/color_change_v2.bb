#include "handler.bbh"
#include "block.bbh"
#include "led.bbh"
#include "ensemble.bbh"
#include "clock.bbh"

#include "block_config.bbh"
#include "memory.bbh"
//#include "hw-api/hwMemory.h"

#ifdef LOG_DEBUG
#include "log.bbh"
#endif

#define PERIOD (3000)

// enum{RED, ORANGE, YELLOW, GREEN, AQUA, BLUE, WHITE, PURPLE, PINK, NUM_COLORS};	
Color myColors[3];

Color nextColor(void) {
  unsigned int c = 0;
  c = (getClock()/PERIOD)%3;
  return myColors[c];
}

void myMain(void)
{
  Time changeT = PERIOD;
	
  //myColors[0] = YELLOW;
  myColors[0] = GREEN;
  myColors[1] = BLUE;
  myColors[2] = RED;
	
  while(getNeighborCount() == 0)
    {
      setColor(WHITE);
    }
#ifdef CLOCK_SYNC
  setColor(AQUA);
  while (!isSynchronized())
    {
      //setColor(AQUA);
      delayMS(6);
    }
#endif	

  if (isTimeLeader()) {
    setColor(RED);
  } else {
    setColor(nextColor());
  }
  changeT = (getClock()/PERIOD)*PERIOD + PERIOD;

  while (1) {
    /*#ifdef LOG_DEBUG
      char s[150];
      snprintf(s, 150*sizeof(char), "color change: t: %lu, c: %lu", getTime(), getClock());
      s[149] = '\0';
      printDebug(s);
      #endif*/
    //printf("clock %u\n", getClock());
    if (isTimeLeader()) {
      setColor(RED);
    } else {
      setColor(nextColor());
    }
    delayMS(1);
    changeT = (getClock()/PERIOD)*PERIOD + PERIOD;
    //changeT += PERIOD;
  }
  //printf("Color Change Clock %u\n", getClock());
	
  while(1);
}
void userRegistration(void)
{
  registerHandler(SYSTEM_MAIN, (GenericHandler)&myMain);	
}
