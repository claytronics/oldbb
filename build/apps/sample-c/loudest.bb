#include "block.bbh"

// idling mic data can fluctuate between +/- IDLE_THRESH of the baseline
#define IDLE_THRESH     75

// number of cycles a tap will last
#define TAP_DURATION    70 /*100*/

// Difference for a tap detection
#define TAP_DIFF        300

#define MYCHUNKS 24
extern Chunk* thisChunk;
Chunk myChunks[MYCHUNKS];

MicData micBase=0;
int      inTap=0;
uint16_t ID=0;
int32_t  m_diff=0;

// max/min values encountered
typedef struct
{
    MicData     m_min, m_max;
} range;

Chunk* getFree(void)
{
    Chunk* c;
	int i;
	for(i=0; i<MYCHUNKS; i++) {
		c = &(myChunks[i]);
	
		if( !chunkInUse(c) ) {
		    return c;
		}	
	}

	return NULL;
}

// update all values using new Range data
void updateTap(range* r, MicData newMic)
{
    if(newMic > r->m_max) r->m_max = newMic;
    if(newMic < r->m_min) r->m_min = newMic;
}

// handler to free chunk
byte freeThisChunk(void)
{
    if(thisChunk == NULL) { 
        return 0;
	}

    freeChunk(thisChunk);
    return 1;
}

// handler for tap message
byte tapHandler(void)
{
    if(thisChunk == NULL) { 
        return 0;
    }

    if(!inTap) {
        return 0;
    }

    int32_t diff;
    int x;

    diff  = (int32_t)(thisChunk->data[5]) & 0xFF;
    diff |= ((int32_t)(thisChunk->data[4]) << 8) & 0xFF00;
    diff |= ((int32_t)(thisChunk->data[3]) << 16) & 0xFF0000;
    diff |= ((int32_t)(thisChunk->data[2]) << 24)  & 0xFF0000;

    // already have max diff
    if(diff <= m_diff) {
        return 0;
    }

    setColor(RED);

    // update and send
    ID = charToGUID(&(thisChunk->data[0]));
    m_diff = diff;

    // send message to neighbors
    for( x=0; x<NUM_PORTS; x++ ) {
        Chunk* cChunk = getFree();

        if(cChunk != NULL) {
            if( sendMessageToPort(cChunk, x, thisChunk->data, 6, tapHandler, (GenericHandler)&freeThisChunk) == 0 ) {
                freeChunk(cChunk);
            }
        }
    }

    return 1;
}

// determine if this block was tapped
int isTap(range* r)
{
    int x;

    // calculate diffs
    int32_t diff    = (int32_t)(r->m_max) - (int32_t)(r->m_min);

    // reset to base Values
    r->m_max = micBase;
    r->m_min = micBase;

    // was a tap
    if( diff > TAP_DIFF ) {
        // check for loudest
        if(m_diff <= diff) {
            ID     = getGUID();
            m_diff = diff;
            setColor(GREEN);
        }
        // already one louder
        else {
            diff = m_diff;
            setColor(RED);
        }

        // send message to neighbors
        byte buf[6];
        GUIDIntoChar(ID, &(buf[0]));
        buf[2] = (diff >> 24) & 0xFF;
        buf[3] = (diff >> 16) & 0xFF;
        buf[4] = (diff >>  8) & 0xFF;
        buf[5] = diff & 0xFF;
        
        for( x=0; x<NUM_PORTS; x++ ) {
            Chunk* cChunk = getFree();

            if(cChunk != NULL) {
                if( sendMessageToPort(cChunk, x, buf, 6, tapHandler, (GenericHandler)&freeThisChunk) == 0 ) {
                    freeChunk(cChunk);
                }
            }
        }

        return 1;
    }

    // not tapped
    return 0;
}

// trigger start of tap detection
int startTap(range* r, MicData newMic)
{
    if(newMic > (micBase + IDLE_THRESH)) {
        updateTap(r, newMic);
        return 1;
    }

    if(newMic < (micBase - IDLE_THRESH)) {
        updateTap(r, newMic);
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

    // reset mic
    rejuvenateHWMic();
    t = getTime() + 250;
    while(getTime() < t);

    // get data
    BB_LOCK(ATOMIC_RESTORESTATE)
    mic = getMicData();
    BB_UNLOCK(NULL)

    micBase = mic;

    /************** get baseline readings *************/
    for(i=0; i<1000; i++)
    {
        // get data
        BB_LOCK(ATOMIC_RESTORESTATE)
        mic = getMicData();
        BB_UNLOCK(NULL)

        // average it out
        micBase = (micBase + mic) / 2;
    }
    /************** end baseline readings *************/
}

void myMain(void)
{
    int         count=0;
    range       r;

    MicData     mic;

    Time        t1=0, t2=0;

    // calibrate
    recalibrate();
    t2 = getTime() + 2000;

    // all tap processing
    while(1)
    {
        // recalibrate all
        if((getTime() > t2) && (!inTap)) {
            recalibrate();
            t2 = getTime() + 2000;
        }

        // get data
        BB_LOCK(ATOMIC_RESTORESTATE)
        mic = getMicData();
        BB_UNLOCK(NULL)

        // in the middle of tracking a tap
        if(inTap) {
            if(++count > TAP_DURATION) {
                isTap(&r);
                
                // wait, then clear color
                t1 = getTime() + 400;
                while(getTime() < t1);
                setColor(WHITE);
                inTap = 0;
                ID = 0;
                m_diff = 0;
                count = 0;
            }
            else {
                updateTap(&r, mic);
            }
        }
        // else determine if new Tap
        else {
            if(startTap(&r, mic)) {
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

