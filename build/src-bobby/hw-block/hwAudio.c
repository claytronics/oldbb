#include "../hw-api/hwAudio.h"
#include <avr/io.h>
#include <util/delay.h>
#include <avr/interrupt.h>
#include "../hw-api/hwTime.h"

uint32_t _audio_duration;

unsigned char sinetable[256] = {
  128,131,134,137,140,143,146,149,152,156,159,162,165,168,171,174,
  176,179,182,185,188,191,193,196,199,201,204,206,209,211,213,216,
  218,220,222,224,226,228,230,232,234,236,237,239,240,242,243,245,
  246,247,248,249,250,251,252,252,253,254,254,255,255,255,255,255,
  255,255,255,255,255,255,254,254,253,252,252,251,250,249,248,247,
  246,245,243,242,240,239,237,236,234,232,230,228,226,224,222,220,
  218,216,213,211,209,206,204,201,199,196,193,191,188,185,182,179,
  176,174,171,168,165,162,159,156,152,149,146,143,140,137,134,131,
  128,124,121,118,115,112,109,106,103,99, 96, 93, 90, 87, 84, 81, 
  79, 76, 73, 70, 67, 64, 62, 59, 56, 54, 51, 49, 46, 44, 42, 39, 
  37, 35, 33, 31, 29, 27, 25, 23, 21, 19, 18, 16, 15, 13, 12, 10, 
  9,  8,  7,  6,  5,  4,  3,  3,  2,  1,  1,  0,  0,  0,  0,  0,  
  0,  0,  0,  0,  0,  0,  1,  1,  2,  3,  3,  4,  5,  6,  7,  8,  
  9,  10, 12, 13, 15, 16, 18, 19, 21, 23, 25, 27, 29, 31, 33, 35, 
  37, 39, 42, 44, 46, 49, 51, 54, 56, 59, 62, 64, 67, 70, 73, 76, 
  79, 81, 84, 87, 90, 93, 96, 99, 103,106,109,112,115,118,121,128
};

/*
  unsigned char quarterwave[90] = {
  0,4,9,13,18,22,27,31,35,40,44,49,53,57,62,66,70,75,79,83,87,91,
  96,100,104,108,112,116,120,124,128,131,135,139,143,146,150,153,
  157,160,164,167,171,174,177,180,183,186,190,192,195,198,201,204,
  206,209,211,214,216,219,221,223,225,227,229,231,233,235,236,238,
  240,241,243,244,245,246,247,248,249,250,251,252,253,253,254,254,
  254,255,255,255,255
  };
*/

unsigned int wave[SAMPLES];

void chirpHW(unsigned int freq, unsigned int duration)
{
  uint16_t ccaVal;
  ccaVal = ((uint32_t)16000000 / (uint32_t)freq) - 1;

  TCE0.CCA = ccaVal;

  _audio_duration = (uint32_t)freq * (uint32_t)duration;
  TCE0.CTRLA = TC_CLKSEL_DIV1_gc;

  PORTB.OUTSET = PIN5_bm;
}

void setDacHW(unsigned int ch0, unsigned int ch1)
{
  DACB.CH0DATA = ch0 << 4;
  DACB.CH1DATA = ch1 << 4;
}

void initHWAudio()
{
  int i;

  for(i = 0; i < (SAMPLES/2); i++)
    {
      wave[i] = (sinetable[((2*256)/SAMPLES)*i]) << 4;
      if(wave[i] > 2048)
	{
	  wave[i+(SAMPLES/2)] = 4096 - wave[i];
	}
      else
	{
	  wave[i+(SAMPLES/2)] = 2048 + wave[i];
	}

    }
  // disable audio chip for now
  PORTB.OUTCLR = PIN5_bm;
  PORTB.DIRSET = PIN5_bm;

  //PORTB.DIRSET = PIN2_bm;
  //PORTB.DIRSET = PIN3_bm;

  // enable dual channel operation, trigger on event
  //DACB.CTRLB = DAC_CHSEL_DUAL_gc | DAC_CH1TRIG_bm | DAC_CH0TRIG_bm;
  DACB.CTRLB = DAC_CHSEL_SINGLE_gc | DAC_CH0TRIG_bm;
  // left adjust to test using 8-bit values only; REFSEL = 0 to use internal 1V reference
  DACB.CTRLC = DAC_REFSEL_INT1V_gc; //DAC_LEFTADJ_bm;

  // set conversion interval to ~2us (64CLK @ 32MHz), refresh to ~16us (512CLK @32MHz)
  DACB.TIMCTRL = DAC_CONINTVAL_64CLK_gc | DAC_REFRESH_512CLK_gc;

  DACB.EVCTRL = DAC_EVSEL_1_gc;	// set trigger event to EV1

  //DACB.CTRLA = DAC_CH1EN_bm | DAC_CH0EN_bm | DAC_ENABLE_bm;
  DACB.CTRLA = DAC_CH0EN_bm | DAC_ENABLE_bm;

  DACB.CH0DATA = 0;
  DACB.CH1DATA = 0;

  DMA.CTRL = DMA_ENABLE_bm | DMA_PRIMODE_RR0123_gc;

  DMA.CH0.ADDRCTRL = DMA_CH_SRCRELOAD_BLOCK_gc | DMA_CH_SRCDIR_INC_gc | DMA_CH_DESTRELOAD_BURST_gc | DMA_CH_DESTDIR_INC_gc;
  DMA.CH0.TRIGSRC = DMA_CH_TRIGSRC_DACB_CH0_gc;
  DMA.CH0.TRFCNT = SAMPLES;
  DMA.CH0.REPCNT = 0;

  DMA.CH0.SRCADDR0  = (((uint32_t)(&(wave[0])))>>0*8) & 0xFF;
  DMA.CH0.SRCADDR1  = (((uint32_t)(&(wave[0])))>>1*8) & 0xFF;
  DMA.CH0.SRCADDR2  = (((uint32_t)(&(wave[0])))>>2*8) & 0xFF;
  DMA.CH0.DESTADDR0 = 0x38; //(((uint32_t)(&(DACB.CH0DATA)))>>0*8)&0xFF;
  DMA.CH0.DESTADDR1 = 0x03; //(((uint32_t)(&(DACB.CH0DATA)))>>1*8)&0xFF;
  DMA.CH0.DESTADDR2 = 0x00; //(((uint32_t)(&(DACB.CH0DATA)))>>2*8)&0xFF; 

  DMA.CH0.CTRLA = DMA_CH_ENABLE_bm | DMA_CH_REPEAT_bm | DMA_CH_BURSTLEN_2BYTE_gc | DMA_CH_SINGLE_bm;

  //DMA.CTRL = DMA_ENABLE_bm | DMA_PRIMODE_RR0123_gc;

  PORTB.DIRSET = PIN3_bm;
  PORTB.OUTCLR = PIN3_bm;
  /*
    DMA.CH1.ADDRCTRL = DMA_CH_SRCRELOAD_BLOCK_gc | DMA_CH_SRCDIR_INC_gc | DMA_CH_DESTRELOAD_BURST_gc | DMA_CH_DESTDIR_INC_gc;
    DMA.CH1.TRIGSRC = DMA_CH_TRIGSRC_DACB_CH1_gc;
    DMA.CH1.TRFCNT = SAMPLES;
    DMA.CH1.REPCNT = 0;
	
    DMA.CH1.SRCADDR0  = (((uint32_t)(&(wave[SAMPLES/2])))>>0*8) & 0xFF;
    DMA.CH1.SRCADDR1  = (((uint32_t)(&(wave[SAMPLES/2])))>>1*8) & 0xFF;
    DMA.CH1.SRCADDR2  = (((uint32_t)(&(wave[SAMPLES/2])))>>2*8) & 0xFF;
    DMA.CH1.DESTADDR0 = 0x3A; //(((uint32_t)(&(DACB.CH1DATA)))>>0*8)&0xFF;
    DMA.CH1.DESTADDR1 = 0x03; //(((uint32_t)(&(DACB.CH1DATA)))>>1*8)&0xFF;
    DMA.CH1.DESTADDR2 = 0x00; //(((uint32_t)(&(DACB.CH1DATA)))>>2*8)&0xFF; 
	
    DMA.CH1.CTRLA = DMA_CH_ENABLE_bm | DMA_CH_REPEAT_bm | DMA_CH_BURSTLEN_2BYTE_gc | DMA_CH_SINGLE_bm;
  */
  TCE0.CTRLA = TC_CLKSEL_OFF_gc;	//default to keeping the clock OFF
  TCE0.CTRLB = TC_WGMODE_FRQ_gc;
  TCE0.INTCTRLA = TC_OVFINTLVL_HI_gc;
  TCE0.CCA = 0x0000;

  EVSYS.CH1MUX = EVSYS_CHMUX_TCE0_OVF_gc;

  PORTB.OUTSET |= PIN5_bm;

  /*
    delay_ms(10);
	
    for(i = 0; i < 256; ++i)
    {
    set_dac(sinetable[i], sinetable[256-i]);
    delay_ms(10);
    }
	
    set_dac(0,0);
  */
  //EVSYS.CH1MUX = 0x83;

  //DMA.CH0.CTRLA |= 0x90;

  // need to set this to enable amplifier
  //PORTB.OUTSET |= PIN1_bm;

}


ISR(TCE0_OVF_vect)
{
  if(_audio_duration == 0)
    {
      TCE0.CTRLA = TC_CLKSEL_OFF_gc;
      PORTB.OUTCLR = PIN5_bm;
    }
  else
    {
      _audio_duration--;	
    }
}
