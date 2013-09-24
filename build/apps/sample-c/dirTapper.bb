#include "block.bbh"

// idling accel data can fluctuate between +/- IDLE_THRESH of the baseline
#define IDLE_THRESH     3

// number of cycles a tap will last
#define TAP_DURATION    100

int baseX=0, baseY=0, baseZ=0;

// tap max/min values encountered
typedef struct
{
    int min;
    int max;
} tapRange;


void updateTap(tapRange* x, tapRange* y, tapRange* z, int newX, int newY, int newZ)
{
    if(newX > x->max) x->max = newX;
    if(newX < x->min) x->min = newX;

    if(newY > y->max) y->max = newY;
    if(newY < y->min) y->min = newY;

    if(newZ > z->max) z->max = newZ;
    if(newZ < z->min) z->min = newZ;
}

void tapDir(tapRange* x, tapRange* y, tapRange* z)
{
    int xRange = x->max - x->min;
    int yRange = y->max - y->min;
    int zRange = z->max - z->min;

    x->max = baseX;
    x->min = baseX;
    
    y->max = baseY;
    y->min = baseY;

    z->max = baseZ;
    z->min = baseZ;

    if( (xRange >= yRange) && (xRange >= zRange) )
        setColor(RED);
    if( (yRange >= xRange) && (yRange >= zRange) )
        setColor(BLUE);
    if( (zRange >= xRange) && (zRange >= yRange) )
        setColor(GREEN);
}

int determineTap(tapRange* x, tapRange* y, tapRange* z, int newX, int newY, int newZ)
{
    if(newX > (baseX + IDLE_THRESH)) {
        x->max = newX;
        return 1;
    }
    if(newX < (baseX - IDLE_THRESH)) {
        x->min = newX;
        return 1;
    }

    if(newY > (baseY + IDLE_THRESH)) {
        y->max = newY;
        return 1;
    }
    if(newY < (baseY - IDLE_THRESH)) {
        y->min = newY;
        return 1;
    }

    if(newZ > (baseZ + IDLE_THRESH)) {
        z->max = newZ;
        return 1;
    }
    if(newZ < (baseZ - IDLE_THRESH)) {
        z->min = newZ;
        return 1;
    }

    return 0;
}

void myMain(void)
{
    int         i, inTap=0, count=0;
    tapRange    rX, rY, rZ;
    Time        t;

    /***************** indicate set-up *****************/
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

    // indicate reset
    /******************* end set-up *******************/


    /************** get baseline readings *************/
    for(i=0; i<1000; i++)
    {
        AccelData acc = getAccelData();
        baseX += acc.x;
        baseY += acc.y;
        baseZ += acc.z;

        baseX /= 2;
        baseY /= 2;
        baseZ /= 2;
    }

    // probably a better way to do this other than averaging
//    baseX /= 1000;
//    baseY /= 1000;
//    baseZ /= 1000;

    setColor(WHITE);
    /************** end baseline readings *************/


    while(1)
    {
        // get accelerometer data
        AccelData acc = getAccelData();

        // in the middle of tracking a tap
        if(inTap) {
            if(++count > TAP_DURATION) {
                tapDir(&rX, &rY, &rZ);
                inTap = 0;
                count = 0;

                // wait for half a second
                t = getTime() + 500;
                while(getTime() < t);
                setColor(WHITE);
            }
            else {
                updateTap(&rX, &rY, &rZ, acc.x, acc.y, acc.z);
            }
        }
        // else determine if new Tap
        else {
            if(determineTap(&rX, &rY, &rZ, acc.x, acc.y, acc.z)) {
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
