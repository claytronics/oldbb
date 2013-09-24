#include <avr/io.h>
#include <avr/interrupt.h>
#include "../system/defs.h"
#include "../system/microphone.h"
#include "../hw-api/hwMicrophone.h"


extern MicData _mic;


void updateHWMic()
{
    BB_LOCK(ATOMIC_RESTORESTATE)
    // read low, then high byte
    _mic = ADCA.CH0.RES;

    // sign extend to full 16 bits
    _mic = _mic >> 4;
    BB_UNLOCK(NULL)

    // register event
    //triggerHandler(EVENT_MIC_DATA);
}

void initHWMic(void)
{
    // set-up basic function
    ADCA.CTRLA = ADC_ENABLE_bm;                                 // disable combined DMA but enable ADC
    ADCA.CTRLB = ADC_CONMODE_bm | ADC_RESOLUTION_LEFT12BIT_gc;  // use signed 12 bit left adjusted format
    ADCA.REFCTRL = ADC_REFSEL_VCC_gc | ADC_BANDGAP_bm;          // disable combined DMA but enable ADC
    ADCA.PRESCALER = ADC_PRESCALER_DIV512_gc;                   // DIV512 prescaler

    // set-up inputs (NOTE: define for ADC_CH_MUXNEG_PIN5_gv is incorrect)
    ADCA.CH0.MUXCTRL = ADC_CH_MUXPOS_PIN0_gc | ADC_CH_MUXNEG_PIN1_gc;   // take inputs from microphone (A0, A5)
    ADCA.CH0.CTRL = ADC_CH_GAIN_1X_gc | ADC_CH_INPUTMODE_DIFFWGAIN_gc;  // use differential gain (x1)

    // set-up interrupts
    ADCA.CH0.INTCTRL = ADC_CH_INTLVL1_bm;    // interrupts on conversion completion with MED priority

    // start conversion/clear interrupts
    ADCA.CH0.INTFLAGS = ADC_CH_CHIF_bm;
    ADCA.CH0.CTRL |= ADC_CH_START_bm;
}

void rejuvenateHWMic(void)
{
    // set as outputs
    PORTA.DIRSET = PIN0_bm;
    PORTA.DIRSET = PIN5_bm;

    // drive both back to zero
    PORTA.OUTCLR = PIN0_bm;
    PORTA.OUTCLR = PIN5_bm;

    // reset as inputs
    PORTA.DIRCLR = PIN0_bm;
    PORTA.DIRCLR = PIN5_bm;

}

ISR(ADCA_CH0_vect)
{
    ADCA.CH0.INTFLAGS |= ADC_CH_CHIF_bm;
    
    // update the data
    updateHWMic();

    // restart the conversion
    ADCA.CH0.CTRL |= ADC_CH_START_bm;
}


