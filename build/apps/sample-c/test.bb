#include "handler.bbh"
#include "data_link.bbh"
#include "led.bbh"
#include "log.bbh"
#include "accelerometer.bbh"
#include "handler.bbh"
#include "block.bbh"
#include "ensemble.bbh"
#include "clock.bbh"
#include "block_config.bbh"
#include "memory.bbh"
  
void getCmdData(void);
void blockTap(void);
void angleChange(void);

int tapCount = 0;

void myMain(void)
{
  setColor(WHITE);
  
  while(1);
}

void getCmdData(void)
{
  #ifdef LOG_DEBUG
  char s[150];
  
  switch (thisChunk->data[3]){
    case 0:
      setColor(RED);
      snprintf(s, 150*sizeof(char), "RED");
    break;
    case 1:
      setColor(ORANGE);
      snprintf(s, 150*sizeof(char), "ORANGE");
    break;
    case 2:
      setColor(YELLOW);
      snprintf(s, 150*sizeof(char), "YELLOW");
    break;
    case 3:
      setColor(GREEN);
      snprintf(s, 150*sizeof(char), "GREEN");
    break;
    case 4:
      setColor(AQUA);
      snprintf(s, 150*sizeof(char), "AQUA");
    break;
    case 5:
      setColor(BLUE);
      snprintf(s, 150*sizeof(char), "BLUE");
    break;
    case 6:
      setColor(WHITE);
      snprintf(s, 150*sizeof(char), "WHITE");
    break;
    case 7:
      setColor(PURPLE);
      snprintf(s, 150*sizeof(char), "PURPLE");
    break;
    case 8:
      setColor(PINK);
      snprintf(s, 150*sizeof(char), "PINK");
    break;
    default:
      setIntensity(0);
      snprintf(s, 150*sizeof(char), "UNKNOWN");
    break;
  }      
  s[149] = '\0';
  printDebug(s);
  #endif
}

void blockTap(void){
    setNextColor();
    
    #ifdef LOG_DEBUG
    char s[150];
    snprintf(s, 150*sizeof(char), "TAP #%d", tapCount);
    s[149] = '\0';
    printDebug(s);
    #endif
    tapCount++;
}

void angleChange(void){
	AccelData acc = getAccelData();
	
	#ifdef LOG_DEBUG
	char s[150];
	snprintf(s, 150*sizeof(char), "x:%i y:%i z:%i\n", acc.x, acc.y, acc.z);
	s[149] = '\0';
	printDebug(s);
	#endif
}

void userRegistration(void)
{
	registerHandler(SYSTEM_MAIN, (GenericHandler)&myMain);
	registerHandler(EVENT_COMMAND_RECEIVED, (GenericHandler)&getCmdData);
	//registerHandler(EVENT_ACCEL_TAP, (GenericHandler)&blockTap);
	registerHandler(EVENT_ACCEL_CHANGE, (GenericHandler)&angleChange);
}
