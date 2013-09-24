#ifndef __HW_BOOT_C__
#define __HW_BOOT_C__

#include "../hw-api/hwBoot.h"
#include "../sim/block.h"
#include "../system/led.h"

void jumpToHWBootSection(void){
    printf("block %s switched to bootloader\r\n", nodeIDasString(this()->id, 0));
    setColor(PURPLE);
}

#endif
