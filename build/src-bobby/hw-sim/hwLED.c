#ifndef __HW_LED_C__
#define __HW_LED_C__

#include "../sim/block.h"
#include "../hw-api/hwLED.h"



void setHWLED(byte r, byte g, byte b, Intensity i){
  this()->simLEDr=r;
  this()->simLEDg=g;
  this()->simLEDb=b;
  this()->simLEDi=i;
}

void initHWLED(void){
  setHWLED(128,128,128,255);
}

#endif
