#include "block.bbh"

#include "block_config.bbh"
#include "memory.bbh"
//#include "hw-api/hwMemory.h"
#include "audio.bbh"

#define PERIOD (3000)

// enum{RED, ORANGE, YELLOW, GREEN, AQUA, BLUE, WHITE, PURPLE, PINK, NUM_COLORS};	

Color nextColor(void) {
  Color c = 0;
  c = (getClock()/PERIOD)%NUM_COLORS;
  if ((c == AQUA) || (c == WHITE))
    {
      c = ORANGE;
    }
  return c;
}

void myMain(void)
{
  Time changeT = PERIOD;

  initAudio();

  while(getNeighborCount() == 0)
    {
      setColor(WHITE);
    }

  while (!isSynchronized())
    {
      setColor(AQUA);
      delayMS(6);
    }

  setColor(nextColor());
  changeT = (getClock()/PERIOD)*PERIOD + PERIOD;

  while (1) {
    while(getClock() < changeT)
      {
	delayMS(1);
      }
    /*#ifdef LOG_DEBUG
      char s[150];
      snprintf(s, 150*sizeof(char), "color change: t: %lu, c: %lu", getTime(), getClock());
      s[149] = '\0';
      printDebug(s);
      #endif*/
    //printf("clock %u\n", getClock());
    chirp(40000,1);
    if (isTimeLeader()) {
      setColor(RED);
    } else {
      setColor(GREEN);
      //setColor(nextColor());
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
