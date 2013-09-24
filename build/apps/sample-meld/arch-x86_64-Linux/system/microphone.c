# 1 "/home/dcampbel/Research/blinkyBocksHardware/build/src-bobby/system/microphone.bb"
#include "../sim/block.h"
// SYSTEM INCLUDES
#include "microphone.h"

// HARDWARE INCLUDES
#include "../hw-api/hwMicrophone.h"

// MicData _mic;

MicData getMicData()
{
    return  (this()->_mic) ;
}

/*void updateMic(){	updateHWMic();}*/
/*int newMicData(){	return newHWMicData();}*/

