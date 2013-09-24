#ifndef __HW_BLOCK_TICK_C__
#define __HW_BLOCK_TICK_C__

#include "../hw-api/hwBlockTick.h"
#include "../sim/block.h"

void initBlockTick(void){
  this()->blockReady=1;

  // blockTick emulated using separate pthread
  //    see build/src/sim/blockDispatch.c
}

#endif
