#include <avr/io.h>
#include <avr/interrupt.h>
#include "../hw-api/hwAccelerometer.h"
#include "../system/defs.h"
#include "../system/accelerometer.h"
#include "../system/handler.h"
#include "twi_master_driver.h"

#define ACCEL_I2C_ADDR 0x4C    	// 0x98 >> 1;  << 1 and OR with 0x01 for i2c read address
#define CPU_SPEED   32000000
#define BAUDRATE	100000
#define TWI_BAUDSETTING TWI_BAUD(CPU_SPEED, BAUDRATE)

TWI_Master_t twiMaster;
extern AccelData _acc;

int newHWAccelData()
{
	return (twiMaster.result == TWIM_RESULT_OK && twiMaster.status == TWIM_STATUS_READY);
}

// this is currently non-blocking.
void updateHWAccel()
{
	if(twiMaster.result == TWIM_RESULT_OK && twiMaster.status == TWIM_STATUS_READY)
	{ 
		byte oldstatus = _acc.status & ACC_O_MASK;
		
		// I2C reads in as unsigned - we need to convert back to 2s complement (+- 32)
		_acc.x = (Angle)(twiMaster.readData[0] << 2) >> 2;
		_acc.y = (Angle)(twiMaster.readData[1] << 2) >> 2;
		_acc.z = (Angle)(twiMaster.readData[2] << 2) >> 2;
		_acc.status = twiMaster.readData[3];
		
		twiMaster.result = TWIM_RESULT_UNKNOWN;
		
		// if superceding event isn't registered, register sub-events, if necessary
		if(!triggerHandler(EVENT_ACCEL_CHANGE))
		{
			if(_acc.status & ACC_TAP)
			{
				triggerHandler(EVENT_ACCEL_TAP);
			}	
			if(_acc.status & ACC_SHAKE)
			{
				triggerHandler(EVENT_ACCEL_SHAKE);
			}	
			if(oldstatus != (_acc.status & ACC_O_MASK))
			{
				triggerHandler(EVENT_ACCEL_TAP);
			}			
		}
	}
}

// Doesn't *really* need to be blocking, just checked for success
// TODO: should block? yes no?
void initHWAccel()
{
	/* Initialize TWI master. */
	TWI_MasterInit(&twiMaster,
	               &TWIE,
	               TWI_MASTER_INTLVL_LO_gc,
	               TWI_BAUDSETTING);

	// prepare configuration data for the accelerometer
	byte buf[2];

	// set mode to STANDBY if it isn't already (can't update registers in ACTIVE mode!)
	buf[0] = 0x07;
	buf[1] = 0x18;
	TWI_MasterWriteRead(&twiMaster, ACCEL_I2C_ADDR, buf, 2, 0);
	while (twiMaster.status != TWIM_STATUS_READY);

	// set sleep mode
	buf[0] = 0x05;
	buf[1] = 0x00;	// no sleep
	TWI_MasterWriteRead(&twiMaster, ACCEL_I2C_ADDR, buf, 2, 0);
	while (twiMaster.status != TWIM_STATUS_READY);

	// set interrupts
	buf[0] = 0x06;
	buf[1] = 0x07;	// 0x06 Interrupt Setup Register -> PDINT | PLINT | FBINT
	TWI_MasterWriteRead(&twiMaster, ACCEL_I2C_ADDR, buf, 2, 0);
	while (twiMaster.status != TWIM_STATUS_READY);

	// set filter rate
	buf[0] = 0x08;
	buf[1] = 0xE0;	// 0x08 Sample Register -> Fil[0:2]
	TWI_MasterWriteRead(&twiMaster, ACCEL_I2C_ADDR, buf, 2, 0);
	while (twiMaster.status != TWIM_STATUS_READY);
	
	// set tap detection
	buf[0] = 0x09;
	buf[1] = 0x10;	// 0x09	Pulse detection -> +- 4 counts
	TWI_MasterWriteRead(&twiMaster, ACCEL_I2C_ADDR, buf, 2, 0);
	while (twiMaster.status != TWIM_STATUS_READY);

	// set tap debounce
	buf[0] = 0x0A;
	buf[1] = 0x10;	// 0x0A Tap debounce ->	4 detections
	TWI_MasterWriteRead(&twiMaster, ACCEL_I2C_ADDR, buf, 2, 0);
	while (twiMaster.status != TWIM_STATUS_READY);	
	
	// enable the accelerometer
	buf[0] = 0x07;
	buf[1] = 0x19;
	TWI_MasterWriteRead(&twiMaster, ACCEL_I2C_ADDR, buf, 2, 0);
	while (twiMaster.status != TWIM_STATUS_READY);
	
	// set up input/interrupt to detect acceleromter IRQs
	PORTB.DIRCLR = PIN0_bm;					// set Pin0 as input
	PORTB.PIN0CTRL = PORT_OPC_PULLUP_gc | PORT_ISC_FALLING_gc;	// set pullup, trigger interrupt on falling edge
	PORTB.INT0MASK = PIN0_bm;				// enable PIN0 to trigger INT0
	PORTB.INTCTRL = PORT_INT0LVL_LO_gc;		// enable INT0 interrupt on LOW priority
}

// Acceleromter status change detection interrupt
ISR(PORTB_INT0_vect)
{

	if(twiMaster.status == TWIM_STATUS_READY)
	{
		byte buf = 0x00;		// read 4 bytes, starting at 0x00 (x,y,z,status)
		TWI_MasterWriteRead(&twiMaster, ACCEL_I2C_ADDR, &buf, 1, 4);
	}

}

/*! TWIE Master Interrupt vector. */
ISR(TWIE_TWIM_vect)
{

	TWI_MasterInterruptHandler(&twiMaster);
}

// helper function to setup accelerometer registers
void setAccelRegister(byte one, byte two)
{
    byte buf[2];

    buf[0] = one;
    buf[1] = two;
    
    TWI_MasterWriteRead(&twiMaster, ACCEL_I2C_ADDR, buf, 2, 0);
    while(twiMaster.status != TWIM_STATUS_READY);
}
