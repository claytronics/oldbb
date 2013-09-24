#include <avr/io.h>
#include "../hw-api/hwBoot.h"

void jumpToHWBootSection(){
	uint8_t temp = RST.CTRL | RST_SWRST_bm;
	CCP = CCP_IOREG_gc; // grab permission to modify the reset reg
	RST.CTRL = temp; // set the reset reg to trigger a SW reset
}
