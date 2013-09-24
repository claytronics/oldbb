#include "block.bbh"

void myMain(void)
{
    setColor(YELLOW);
    rejuvenateHWMic();

    MicData mic;
    Time t=0, r=0;

    while(1)
    {
        // get microphone data
        BB_LOCK(ATOMIC_RESTORESTATE)
        mic = getMicData();
        BB_UNLOCK(NULL)

        // print it
        printf("%i\r\n", mic);

        if(mic >= 50) {
            setColor(GREEN);
            t = getTime() + 100;
        }

        if(getTime() > t) {
            setColor(YELLOW);
        }

        if(getTime() > r) {
            rejuvenateHWMic();
            r += 5000;
        }

    }
}

void userRegistration(void)
{
    registerHandler(SYSTEM_MAIN, (GenericHandler)&myMain);
}
