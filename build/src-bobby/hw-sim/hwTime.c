#ifndef __HW_TIME_C__
#define __HW_TIME_C__

#include "../hw-api/hwTime.h"
#include <sys/timeb.h>

Time getHWTime(void){

  struct timeb t;
  ftime(&t);


  return (Time)(t.millitm+1000*(t.time % (1<<20)));
}
void initHWTime(void){

}

#endif
