# 1 "/home/anaz/Desktop/oldbb-ssh/build/src-bobby/system/block.bbh"
#ifndef __BLOCK_H__
#define __BLOCK_H__
//---------- BLOCK.H------------------
//
// This takes the place of the old cubic-system managing all of the hardware
//
//

// SYSTEM API INCLUDE SECTION
#include "defs.h"

#include "debug.h"

//#include <avr/io.h>
//#include <util/delay.h>

#include "hardwaretime.h"
#include "led.h"
#include "accelerometer.h"
#include "microphone.h"
#include "serial.h"
//#include "audio.h"
#include "data_link.h"
#include "message.h"

// HARDWARE API INCLUDE SECTION
#include "../hw-api/hwBlockTick.h"
#include "../hw-api/hwLED.h"
#include "../hw-api/hwDataLink.h"
#include "../hw-api/hwAccelerometer.h"
#include "../hw-api/hwMicrophone.h"


// PROTOTYPES
void blockTick(void);    // polls the system for status changes

void initBlock(void);   // calls initial register magic, other setup

// SIM PROTOs

#ifdef BBSIM
void pauseForever(void);
void tellNeighborsDestroyed(Block *b);
#endif

#endif
