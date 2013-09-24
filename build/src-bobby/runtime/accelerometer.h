# 1 "/seth/claycvs/svn/blinkyblocks/newcode/src/runtime/accelerometer.bbh"
#ifndef __ACCELEROMETER_H__
#define __ACCELEROMETER_H__

#include "bb.h"
#include <stdint.h>
#include <pthread.h>

// pthread_mutex_t tapMutex;
// unsigned tapBuffer;
// bool tapStatus;

void updateAccel();		// polls the accelerometer
bool getTap();  		// true if tapped since last updateAccel
void initAccel();		// initializes accelerometer

#endif
