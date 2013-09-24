#include "block.bbh"

void myMain(void)
{
    setColor(BLUE);
    rejuvenateHWMic();

    MicData mic;
    Time t;

    while(1)
    {
        // get accelerometer data
        BB_LOCK(ATOMIC_RESTORESTATE)
        mic = getMicData();
        BB_UNLOCK(NULL)

        // print it
        printf("%i\r\n", mic);

        // take data every 0.5 seconds to check for drift
        t = getTime() + 500;
        while(getTime() < t);
    
        rejuvenateHWMic();
    }
}

void userRegistration(void)
{
    registerHandler(SYSTEM_MAIN, (GenericHandler)&myMain);
}
