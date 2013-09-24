# 1 "/seth/claycvs/svn/blinkyblocks/newcode/src/runtime/accelerometer.bb"
#include "accelerometer.h"

void updateAccel()
{
	pthread_mutex_lock(&tapMutex);
	if (tapBuffer > 0)
	{
		tapStatus = true;
		tapBuffer--;
	}
	else
	{
		tapStatus = false;
	}
	pthread_mutex_unlock(&tapMutex);
}

bool getTap()
{
	return tapStatus;
}

void initAccel()
{
	tapBuffer = 0;
	tapStatus = false;
	pthread_mutex_init(&tapMutex, NULL);
}
