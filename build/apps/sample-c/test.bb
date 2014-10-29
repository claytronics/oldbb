#include "block.bbh"

// idling mic data can fluctuate between +/- IDLE_THRESH of the baseline
#define IDLE_THRESH     75

// number of cycles a tap will last
#define TAP_DURATION    70 /*100*/

// Difference for a tap detection
#define TAP_DIFF        300

#define MYCHUNKS 24

void myMain(void)
{
    setColor(RED);
        //	fprintf(stderr,"my color is red");
    while(1)
    {
    }
}

void userRegistration(void)
{
    registerHandler(SYSTEM_MAIN, (GenericHandler)&myMain);
}

