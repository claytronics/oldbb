#ifndef _HW_SERIAL_C_
#define _HW_SERIAL_C_

#include "../hw-api/hwSerial.h"
#include "../system/circ_buffer.h"


void initHWPorts(void){

  /*  int i;
  for(i=0; i<NUM_PORTS; i++){
    this()->simCommBufs[i].start=0;
    this()->simCommBufs[i].end=0;
    pthread_mutex_init( &this()->simPortMutex[i], NULL);
    }*/
}


void pPutChar(char c, PRef p){
    if( /*SCG p>=0 &&*/ p < NUM_PORTS){


  }

}
int pGetChar(PRef p){

    if( /*SCG p>=0 &&*/ p < NUM_PORTS){
    
    /*  if(isEmpty(&this()->simCommBufs[p])){
      
      return -1;
    }
    
    return pop(&this()->simCommBufs[p]);*/
  }

  return -1;
}


#endif
