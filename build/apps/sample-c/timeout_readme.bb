#include "block.bbh"

/*-------------------- SIMPLE EXAMPLE OF HOW TO USE TIMEOUTS INSIDE YOUR PROGRAM
 *
 * You have to use a table of at least 2 timeouts.
 * Otherwise you the block will not be able to register twice the same timeout and will crash. 
 * It seems that it takes a little time for a block to deregister a timeout.
 * It's probably what is causing this issue.
 */

// Time between color changes
#define COLOR_CHANGE_TIME 2000

// Number of timeouts to use, 2 should be enough
#define NUM_TIMEOUT 2

Timeout tout[NUM_TIMEOUT];
byte timeoutCount;

void setNextTimeout(void);

// Set next color on the block every COLOR_CHANGE_TIME ms
void 
nextColor(void)
{
  setNextColor();
  setNextTimeout();
}

// Register next timeout
void
setNextTimeout(void)
{
  tout[timeoutCount].callback = (GenericHandler)(&nextColor);
  tout[timeoutCount].calltime = getTime() + COLOR_CHANGE_TIME;
  registerTimeout( &(tout[timeoutCount]) );
  
  if (timeoutCount++ == NUM_TIMEOUT-1) timeoutCount = 0;
}

void 
myMain(void) 
{
  setColor(RED);

  // init timeout system
  timeoutCount = 0;
  setNextTimeout();

  while(1);
}

void 
userRegistration(void)
{
  registerHandler(SYSTEM_MAIN, (GenericHandler)&myMain);	
}

// Other ideas that did not work...

/***** Inside void nextColor();
if (timeoutNum++ == 0) resetTimeout(&tout[0]);
  else { 
  resetTimeout( &(tout[1]) );
  timeoutNum = 0;
  }********/

/*
  void
  resetTimeout(Timeout *t)
  { 
  (*t).callback = (GenericHandler)(&nextColor);
  (*t).calltime = getTime() + COLOR_CHANGE_TIME;
  registerTimeout(t);
  }*/
