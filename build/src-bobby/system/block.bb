#include "block.bbh"

#ifdef CLOCK_SYNC
#include  "clock.bbh"
#endif
#ifdef LOG_DEBUG
#include "log.bbh"
#endif

#define RANDOMIZE_PORT_CHECK

#ifdef RANDOMIZE_PORT_CHECK
#define GET_PORT_INDEX(i) (portMap[(i+tmp) % NUM_PORTS])
threadvar PRef portMap[NUM_PORTS];
#else
#define GET_PORT_INDEX(i) (i)			    
#endif

threadvar int blockTickRunning = 0;

extern void vm_alloc(void);

int accelReady=0;

// BlockTick
//
// Polling-based hack to step through and update block state, as necessary.
//
// Much of this can probably be done via ISRs and other state change triggers and this function eliminated.
void blockTick()
{
  byte i;

#ifdef RANDOMIZE_PORT_CHECK
  byte j, tmp;
#endif
  
#ifdef BBSIM
  if (this()->destroyed == 1) {
    // let neighbors know we are gone
    //tellNeighborsDestroyed(this());
    // and never do anything again
    pauseForever();
  }
#endif
  //int input;
  blockTickRunning = 1;
  
  if(accelReady){
    if(newAccelData()){
      updateAccel();
    }
  }
  
  checkTimeout();
  
  checkTimer();

#ifdef RANDOMIZE_PORT_CHECK
  // randomly swap two port refs in the map
  i = rand() % NUM_PORTS;
  j = rand() % NUM_PORTS;
  tmp = portMap[i];
  portMap[i] = portMap[j];
  portMap[j] = tmp; 
#endif

#ifdef RANDOMIZE_PORT_CHECK
  tmp = rand() % (UINT8_MAX - NUM_PORTS); // used by macro GET_PORT_INDEX
#endif
  
  for(i = 0; i < NUM_PORTS; ++i) {
    // read from serial
    processBuffer(GET_PORT_INDEX(i));
  }

  for(i = 0; i < NUM_PORTS; ++i) {   
    // active messaging (handle at most 6 messages)
    handleOneMessage();
  }

#ifdef RANDOMIZE_PORT_CHECK
  tmp = rand() % (UINT8_MAX - NUM_PORTS); // used by macro GET_PORT_INDEX
#endif

  for(i = 0; i < NUM_PORTS; ++i) {
    //send packets/ACKS
    sendOnSerial(GET_PORT_INDEX(i));
  }
  
  // why is this called twice ?
  executeHandlers();	
  if(accelReady){
    if(newAccelData()){
      updateAccel();
    }
  }
  executeHandlers();	
  blockTickRunning = 0;
  
}


// Ties all the horrifying subfunctions together into one simple function
void initBlock()
{

#ifdef RANDOMIZE_PORT_CHECK
  // init local variables
  byte i = 0;
  for(i = 0; i < NUM_PORTS; ++i) {
    portMap[i] = i;
  }
#endif
  
  //software initialization
  initHandlers();

  //hardware related initialization
  initTime();

  initializeMemory();

  initPorts();

#ifdef DEBUG
  initDebug();
  //printf("System Debug Enabled\r\n");
#endif

  initDataLink();	

  initHWLED();
  //initAudio();

  initSystemMessage();
  initEnsemble();

#ifdef MELD
  //allocate MeldVM's data structures
  vm_alloc();
#endif

  initBlockTick();		// HW INITIALIZATION ROUTINE

  initHWAccel();
  accelReady=1;

#ifdef LOG_DEBUG
  initLogDebug();
#endif

#ifdef CLOCK_SYNC
  initClock();
#endif

#ifndef BBSIM
  initHWMic();
#endif
}

