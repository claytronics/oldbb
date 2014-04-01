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
  
void getCmdData(void);
void blockTap(void);
void accelChange();
void orientationChange(AccelData acc);

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

void blockTap(void)
{
    setNextColor();
    
    #ifdef LOG_DEBUG
    char s[150];
    snprintf(s, 150*sizeof(char), "TAP #%d", tapCount);
    s[149] = '\0';
    printDebug(s);
    #endif
    tapCount++;
}

void accelChange(void)
{
	AccelData acc = getAccelData();
	if ((acc.status & ACC_TAP) == ACC_TAP){
		blockTap();
	}
	else {
		orientationChange(acc);
	}
}

void orientationChange(AccelData acc)
{
	/***** SHOULD WORK BUT ALWAYS SENDS ERROR. + INACCURATE.
	 * switch (acc.status & ACC_O_MASK) {
		case ACC_FRONT:
			snprintf(s, 150*sizeof(char), "ACC_FRONT");
			break;
		case ACC_BACK:
			snprintf(s, 150*sizeof(char), "ACC_BACK");
			break;
		case ACC_LEFT:
			snprintf(s, 150*sizeof(char), "ACC_LEFT");
			break;
		case ACC_RIGHT:
			snprintf(s, 150*sizeof(char), "ACC_RIGHT");
			break;
		case ACC_DOWN:
			snprintf(s, 150*sizeof(char), "ACC_DOWN");
			break;
		case ACC_UP:
			snprintf(s, 150*sizeof(char), "ACC_UP");
			break;
		default:
			snprintf(s, 150*sizeof(char), "ERROR");
			break;
	}*/
	#ifdef LOG_DEBUG
	char s[150];
	snprintf(s, 150*sizeof(char),"x:%i\ty:%i\tz:%i\n", acc.x, acc.y, acc.z);
	s[149] = '\0';
	printDebug(s);
	#endif
}

void userRegistration(void)
{
	registerHandler(SYSTEM_MAIN, (GenericHandler)&myMain);
	registerHandler(EVENT_COMMAND_RECEIVED, (GenericHandler)&getCmdData);
	registerHandler(EVENT_ACCEL_CHANGE, (GenericHandler)&accelChange);
}
