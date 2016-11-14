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

//#define RBG_COLORS
#define TREE_LEVEL_COLOR_SHIFT

// enum{RED, ORANGE, YELLOW, GREEN, AQUA, BLUE, WHITE, PURPLE, PINK, NUM_COLORS};
#ifdef RGB_COLORS
Color myColors[3];
#endif

Color nextColor(void) {
  unsigned int c = 0;
  byte nbColors = 6;

#ifdef RBG_COLORS
  nbColors = 3;
#endif
  
#ifdef TREE_LEVEL_COLOR_SHIFT
  c = (getClock()/PERIOD - getDistanceToTimeLeader())%nbColors;
#else
  c = (getClock()/PERIOD)%nbColors;
#endif

#ifdef RBG_COLORS
  return myColors[c];
#else
  return (Color) c;
#endif
}

void myMain(void)
{
#ifdef RGB_COLORS
  myColors[0] = GREEN;
  myColors[1] = BLUE;
  myColors[2] = RED;  
#endif
  
  while(getNeighborCount() == 0) {
    setColor(WHITE);
  }
  
#ifdef CLOCK_SYNC
  setColor(AQUA);
  while (!isSynchronized()) {
    delayMS(6);
  }
#endif	

  while (1) {
    if (isTimeLeader()) {
      setColor(RED);
    } else {
      setColor(nextColor());
    }
    delayMS(5);
  }
  
}
void userRegistration(void)
{
  registerHandler(SYSTEM_MAIN, (GenericHandler)&myMain);	
}
