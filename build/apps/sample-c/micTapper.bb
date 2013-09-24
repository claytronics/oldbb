#include "block.bbh"

// idling mic data can fluctuate between +/- IDLE_THRESH of the baseline
#define IDLE_THRESH     75

// number of cycles a tap will last
#define TAP_DURATION    70

// Difference for a tap detection
#define TAP_DIFF        600

MicData micBase=0;

// mic max/min values encountered
typedef struct
{
    MicData min;
    MicData max;
} micRange;


void updateTap(micRange* m, MicData newData)
{
    if(newData > m->max) m->max = newData;
    if(newData < m->min) m->min = newData;
}

int isTap(micRange *m)
{
    int32_t diff = (int32_t)(m->max) - (int32_t)(m->min);

    m->max = micBase;
    m->min = micBase;

    if( diff > TAP_DIFF ) {
        setColor(BLUE);
        return 1;
    }

    return 0;
}

int determineTap(micRange* m, MicData newData)
{
    if(newData > (micBase + IDLE_THRESH)) {
        m->max = newData;
        return 1;
    }

    if(newData < (micBase - IDLE_THRESH)) {
        m->min = newData;
        return 1;
    }

    return 0;
}

void myMain(void)
{
    int         i, inTap=0, count=0;
    micRange    range;
    MicData     mic;
    Time        t=0, r=0;

    /************** get baseline readings *************/
    rejuvenateHWMic();
    r = getTime() + 500;
    while(getTime() < r);

    for(i=0; i<1000; i++)
    {

        // get accelerometer data
        BB_LOCK(ATOMIC_RESTORESTATE)
        mic = getMicData();
        BB_UNLOCK(NULL)

        micBase += mic;
        micBase /= 2;
    }

    setColor(WHITE);
    /************** end baseline readings *************/


    while(1)
    {
        // get accelerometer data
        BB_LOCK(ATOMIC_RESTORESTATE)
        mic = getMicData();
        BB_UNLOCK(NULL)

        // in the middle of tracking a tap
        if(inTap) {
            if(++count > TAP_DURATION) {
                isTap(&range);
                inTap = 0;
                count = 0;

                // wait for half a second
                t = getTime() + 400;
                while(getTime() < t);
                setColor(WHITE);
            }
            else {
                updateTap(&range, mic);
            }
        }
        // else determine if new Tap
        else {
            if(determineTap(&range, mic)) {
                inTap = 1;
                count = 0;
            }
        }

        // rejuvenate mic
        if((getTime() > r) && (~inTap)) {
            rejuvenateHWMic();
            r = getTime() + 2000;
        }
    }
}

void userRegistration(void)
{
    registerHandler(SYSTEM_MAIN, (GenericHandler)&myMain);
}
