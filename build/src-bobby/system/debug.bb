#include "debug.bbh"
#include "../hw-api/hwDebug.h"

void initDebug() 
{
  initHWDebug();
}

#ifndef BBSIM
void 
xprintf(void* f, ...)
{
}
#endif
