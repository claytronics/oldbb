#ifndef __HW_ACCELEROMETER_C__
#define __HW_ACCELEROMETER_C__


#include "../hw-api/hwAccelerometer.h"
#include "../system/accelerometer.h"
#include "../system/handler.h"
#include <string.h>
#include "../sim/block.h"


int newHWAccelData(void){

  return 1; //updateHWAccel determines if work needs to be done or not
}

void updateHWAccel(void){
  if( this()->tapBuffer>0 && !(this()->_acc.status & ACC_TAP) ){
    if( pthread_mutex_trylock(&(this()->tapMutex))==0 ){
      this()->tapBuffer--;
      pthread_mutex_unlock(&(this()->tapMutex));
      this()->_acc.status|=ACC_TAP;
    }
  } else {
    this()->_acc.status&= (0xff ^ ACC_TAP);
  }

  byte oldstatus = this()->_acc.status & ACC_O_MASK;

	// if superceding event isn't registered, register sub-events, if necessary
	if(!triggerHandler(EVENT_ACCEL_CHANGE))
	{
		if(this()->_acc.status & ACC_TAP)
		{
			triggerHandler(EVENT_ACCEL_TAP);
		}	
		if(this()->_acc.status & ACC_SHAKE)
		{
			triggerHandler(EVENT_ACCEL_SHAKE);
		}	
		if(oldstatus != (this()->_acc.status & ACC_O_MASK))
		{
			triggerHandler(EVENT_ACCEL_TAP);
		}			
	}


}

void initHWAccel(){
  memset(&(this()->_acc), 0, sizeof(this()->_acc));
}

// does nothing in simulation
void setAccelRegister(byte one, byte two)
{
}

#endif
