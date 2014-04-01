#include "handler.bbh"
#include "data_link.bbh"
#include "led.bbh"
#include "log.bbh"
#include "handler.bbh"
#include "block.bbh"
#include "ensemble.bbh"
#include "clock.bbh"
#include "block_config.bbh"
#include "memory.bbh"
  
void getCmdData(void);

void myMain(void)
{

  setColor(WHITE);
  
  while(1);
}

void getCmdData(void)
{
  #ifdef LOG_DEBUG
  char s[150];
  switch (thisChunk->data[3]){ //thisChunk->data[3] always = 0, can't figure out WHY???
    case 0:
      setColor(RED);
      snprintf(s, 150*sizeof(char), "color change to RED");
    break;
    case 1:
      setColor(ORANGE);
      snprintf(s, 150*sizeof(char), "color change to ORANGE");
    break;
    case 2:
      setColor(YELLOW);
      snprintf(s, 150*sizeof(char), "color change to YELLOW");
    break;
    case 3:
      setColor(GREEN);
      snprintf(s, 150*sizeof(char), "color change to GREEN");
    break;
    case 4:
      setColor(AQUA);
      snprintf(s, 150*sizeof(char), "color change to AQUA");
    break;
    case 5:
      setColor(BLUE);
      snprintf(s, 150*sizeof(char), "color change to BLUE");
    break;
    case 6:
      setColor(WHITE);
      snprintf(s, 150*sizeof(char), "color change to WHITE");
    break;
    case 7:
      setColor(PURPLE);
      snprintf(s, 150*sizeof(char), "color change to PURPLE");
    break;
    case 8:
      setColor(PINK);
      snprintf(s, 150*sizeof(char), "color change to PINK");
    break;
    default:
      setIntensity(0);
      snprintf(s, 150*sizeof(char), "UNKNOWN COMMAND");
    break;
  }      
  s[149] = '\0';
  printDebug(s);
  #endif
}

void userRegistration(void)
{
	registerHandler(SYSTEM_MAIN, (GenericHandler)&myMain);
	registerHandler(EVENT_COMMAND_RECEIVED, (GenericHandler)&getCmdData);
}
