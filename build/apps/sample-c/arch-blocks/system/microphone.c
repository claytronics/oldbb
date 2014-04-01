# 1 "/home/pthalamy/CMU/oldbb/build/src-bobby/system/microphone.bb"
// SYSTEM INCLUDES
#include "microphone.h"

// HARDWARE INCLUDES
#include "../hw-api/hwMicrophone.h"

 MicData _mic;

MicData getMicData()
{
    return _mic;
}

/*
void updateMic()
{
	updateHWMic();
}
*/
/*
int newMicData()
{
	return newHWMicData();
}
*/

