#include "block.bbh"

// idling mic data can fluctuate between +/- IDLE_THRESH of the baseline
#define IDLE_THRESH     75

// number of cycles a tap will last
#define TAP_DURATION    70 /*100*/

// Difference for a tap detection
#define TAP_DIFF        300
#define ACCEL_THRESH    2

int     xBase=0, yBase=0, zBase=0;
MicData micBase=0;

// max/min values encountered
typedef struct
{
    MicData m_min, m_max;
    int     x_min, x_max, y_min, y_max, z_min, z_max;
} range;

// update all values using new Range data
void updateTap(range* r, MicData newMic, int newX, int newY, int newZ)
{
    if(newMic > r->m_max) r->m_max = newMic;
    if(newMic < r->m_min) r->m_min = newMic;

    if(newX > r->x_max) r->x_max = newX;
    if(newX < r->x_min) r->x_min = newX;

    if(newY > r->y_max) r->y_max = newY;
    if(newY < r->y_min) r->y_min = newY;

    if(newZ > r->z_max) r->z_max = newZ;
    if(newZ < r->z_min) r->z_min = newZ;
}

// determine if this block was tapped
int isTap(range* r)
{
    // calculate diffs
    int32_t diff    = (int32_t)(r->m_max) - (int32_t)(r->m_min);
    int     xRange  = r->x_max - r->x_min;
    int     yRange  = r->y_max - r->y_min;
    int     zRange  = r->z_max - r->z_min;

    int xDir = (abs(r->x_max - xBase) >= abs(r->x_min - xBase));
    int yDir = (abs(r->y_max - yBase) >= abs(r->y_min - yBase));
    int zDir = (abs(r->z_max - zBase) >= abs(r->z_min - zBase));

    // reset to base Values
    r->m_max = micBase;
    r->m_min = micBase;

    r->x_max = xBase;
    r->x_min = xBase;

    r->y_max = yBase;
    r->y_min = yBase;

    r->z_max = zBase;
    r->z_min = zBase;

    // was a tap - determine the direction
    if( diff > TAP_DIFF ) {
        // check accelerometers detect tap
        if( (xRange < ACCEL_THRESH) && (yRange < ACCEL_THRESH) && (zRange < ACCEL_THRESH) ) {
            setColor(RED);
        }
        // determine axis
        else {
            // x-axis
            if( (xRange >= yRange) && (xRange >= zRange) )
            {
                if(xDir)    setColor(ORANGE);   // tap on west face
                else        setColor(YELLOW);   // tap on east face
            }
            // y-axis
            if( (yRange >= xRange) && (yRange >= zRange) )
            {
                if(yDir)    setColor(BLUE);   // tap on north face
                else        setColor(AQUA);   // tap on south face
            }
            // z-axis
            if( (zRange >= xRange) && (zRange >= yRange) )
            {
                // zDir doesn't really work
                if(zDir)    setColor(PURPLE); // tap on top face
                else        setColor(PINK);   // tap on bottom face
            }
        }

        return 1;
    }

    // not tapped
    return 0;
}

// trigger start of tap detection
int startTap(range* r, MicData newMic, int newX, int newY, int newZ)
{
    if(newMic > (micBase + IDLE_THRESH)) {
        updateTap(r, newMic, newX, newY, newZ);
        return 1;
    }

    if(newMic < (micBase - IDLE_THRESH)) {
        updateTap(r, newMic, newX, newY, newZ);
        return 1;
    }

    return 0;
}

// recalibrate all sensors and reset base values
void recalibrate(void)
{
    Time        t;
    int         i;
    MicData     mic;
    AccelData   acc;

    // reset mic
    rejuvenateHWMic();
    t = getTime() + 250;
    while(getTime() < t);

    // get data
    BB_LOCK(ATOMIC_RESTORESTATE)
    mic = getMicData();
    acc = getAccelData();
    BB_UNLOCK(NULL)

    micBase = mic;
    xBase   = acc.x;
    yBase   = acc.y;
    zBase   = acc.z;

    /************** get baseline readings *************/
    for(i=0; i<1000; i++)
    {
        // get data
        BB_LOCK(ATOMIC_RESTORESTATE)
        mic = getMicData();
        acc = getAccelData();
        BB_UNLOCK(NULL)

        // average it out
        micBase = (micBase + mic) / 2;
        xBase = (xBase + acc.x) / 2;
        yBase = (yBase + acc.y) / 2;
        zBase = (zBase + acc.z) / 2;
    }
    /************** end baseline readings *************/
}

void myMain(void)
{
    int         i, inTap=0, count=0;
    range       r;

    MicData     mic;
    AccelData   acc;

    Time        t1=0, t2=0;


    /******* set-up custom accelerometer status *******/
    // put into standby mode to update registers
    setAccelRegister(0x07, 0x18);

    // every measurement triggers an interrupt
    setAccelRegister(0x06, 0x10);

    // set filter rate
    setAccelRegister(0x08, 0x00);

    // enable accelerometer
    setAccelRegister(0x07, 0x19);

    /***** end set-up custom accelerometer status *****/

    // calibrate
    recalibrate();
    t2 = getTime() + 2000;

    // all tap processing
    while(1)
    {
        // recalibrate all
        if((getTime() > t2) && (~inTap)) {
            recalibrate();
            t2 = getTime() + 2000;
        }

        // get data
        BB_LOCK(ATOMIC_RESTORESTATE)
        mic = getMicData();
        acc = getAccelData();
        BB_UNLOCK(NULL)

        // in the middle of tracking a tap
        if(inTap) {
            if(++count > TAP_DURATION) {
                isTap(&r);
                inTap = 0;
                count = 0;

                // wait, then clear color
                t1 = getTime() + 400;
                while(getTime() < t1);
                setColor(WHITE);
            }
            else {
                updateTap(&r, mic, acc.x, acc.y, acc.z);
            }
        }
        // else determine if new Tap
        else {
            if(startTap(&r, mic, acc.x, acc.y, acc.z)) {
                inTap = 1;
                count = 0;
            }
        }
    }
}

void userRegistration(void)
{
    registerHandler(SYSTEM_MAIN, (GenericHandler)&myMain);
}
