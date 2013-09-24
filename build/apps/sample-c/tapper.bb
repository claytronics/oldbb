#include "block.bbh"

#define TAP_DURATION    50      // time to energize solenoid
#define TAP_DELAY       1500    // delay between taps
#define DIR_DELAY       5000    // delay between direction switch
void myMain(void)
{
    int i;
    Time t;
    setColor(WHITE);

    (&USARTD1)->CTRLB = 0;	// turn off tx/rx
    (&USARTD1)->CTRLA = 0;	// turn off interrupts

    PORTD.DIRSET = PIN7_bm;     // port 7 is closer to center
    PORTD.DIRSET = PIN6_bm;     // port 6 is closer to edge

    while(1) 
    {
        setColor(BLUE);
        t = getTime() + DIR_DELAY;

        // do 5 taps
        for(i=0; i<5; i++)
        {
            while(getTime() < t);
            setColor(RED);
            PORTD.OUTSET = PIN7_bm;
            t = getTime() + TAP_DURATION;

            while(getTime() < t);
            PORTD.OUTCLR = PIN7_bm;
            t = getTime() + TAP_DELAY;
        }

	setColor(GREEN);
        t = getTime() + DIR_DELAY;

        // do 5 taps
        for(i=0; i<5; i++)
        {
            while(getTime() < t);
            setColor(ORANGE);
            PORTD.OUTSET = PIN6_bm;
            t = getTime() + TAP_DURATION;

            while(getTime() < t);
            PORTD.OUTCLR = PIN6_bm;
            t = getTime() + TAP_DELAY;
        }
    }
}

void userRegistration(void)
{
    registerHandler(SYSTEM_MAIN, (GenericHandler)&myMain);
}
