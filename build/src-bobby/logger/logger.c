#include <stdlib.h>
#include <stdio.h>
#include <unistd.h>
#include <ctype.h>
#include <sys/stat.h>
#include <iostream>
#include <pthread.h>

#include "string.h"
#include "hostserial.h"
#include "time.h"
#include "chunk.h"
#include "logtable.h"

#define LOG_CMD		0x05	
#define LOG_MSG 	0x50

//CMD TYPES
#define COLOR_SET	0x11
#define SET_STLEADER	0x22
#define ENSEMBLE_RESET	0x33

#define NUM_COLORS	9

// Usage: ./logger -p /dev/ttyUSB0

using namespace std;

char* portname  = NULL;
string defaultportname = "/dev/ttyUSB0";
int baudrate = 38400;
char* prog = 0;

byte testMode = 0;
int seq = 1;

pthread_mutex_t serialMutex;
pthread_mutex_t circbuffMutex;
pthread_mutex_t responseMutex;

// holds incoming bytes
CircBuf serialData;
LogTable logs;

void usage(void);
void readParameters(int argc, char** argv);
void sendIAmHost(void);
void insertLogChunk(Chunk *c);
void receiveLogs(void);

void sendColorCmd(int);
void giveLeaderStatus(void);
void sendResetCmd(void);

static int kbhit(void);

int 
main(int argc, char** argv) 
{
    
  int i;
  char c = '0';
  readParameters(argc, argv);
    
  if( !Chunk::initSerial(portname, baudrate)  ) {
    exit(1);
  }
    
  sendIAmHost();
    
  /************************* TEST *****************************/
    
  if(testMode) {
      // Test Menu
      cout << "Choose a test:" << endl;
      cout << "1: Color and accelerometer test" << endl;
      cout << "2: Network test:" << endl;
      
      switch (c = getchar())
	{
	case '1':
	  while(c != 'q') {
	    cout << "Press enter to send next color..." << endl;
	    for(i = 0 ; i < NUM_COLORS ; i++)
	      {
		getchar();
		sendColorCmd(i);
		receiveLogs();
	      }
	    //cout << "Press enter to proceed to next test" << endl;
	    //getchar();
	    cout << "Testing accelerometer: Tap Blinky Block and change its orientation" << endl;
	    getchar();
	    receiveLogs();
	    cout << "Type 'q' to quit or any other key to start again..." << endl;
	    c = getchar();
	  }
	  break;  
	case '2':
	  giveLeaderStatus();
	  while(c != 'q') 
	    { 
	      receiveLogs();
	      cout << "Press any key to reset the ensemble or q to quit" << endl;
	      c = getchar();
	      sendResetCmd();
	    }
	  break;
	default:
	  cout << "INVALID CHOICE" << endl;
	  break;
	}
    }
    
  receiveLogs();
  logs.printStats();
  // shutdown everything
  cout << "Closing serial comm...";
  cout.flush();
	
  Chunk::closeSerial();
  cout << " ok!" << endl;
	
  return 0;
}

/**********************************************************************
 ************************ TEST Functions ******************************
 **********************************************************************/

void sendColorCmd(int color) 
{
	
	byte data[5];
	
	data[0] = LOG_MSG;
	data[1] = LOG_CMD;
	data[2] = COLOR_SET;
	data[3] = seq;
	data[4] = color;
	Chunk c(data, 5);

		printf("Sending setColor(%d)\n", color);
		c.send();
		seq++;
}

void giveLeaderStatus(void) 
{
	
	byte data[3];
	
	data[0] = LOG_MSG;
	data[1] = LOG_CMD;
	data[2] = SET_STLEADER;
	
	Chunk c(data, 3);

		printf("Giving leader status to connected block and starting Spanning Tree setup...\n");
		c.send();
}

void sendResetCmd(void) 
{
	
	byte data[3];
	
	data[0] = LOG_MSG;
	data[1] = LOG_CMD;
	data[2] = ENSEMBLE_RESET;
	
	Chunk c(data, 3);

		printf("Resetting the system\n");
		c.send();
}

/*************************************************************************/

void receiveLogs(void)
{
  while(!kbhit()) {
    Chunk *ch = Chunk::read();
    if ( ch != NULL) {
      if (ch->data[0] == LOG_MSG) {			
	insertLogChunk(ch);
      }
      delete ch;
    }
    //logs.printAll();
    logs.printCompleted();
    logs.removeCompleted();
  } 
}

void usage(void) {
    printf("%s: [-p portname]\n-l: log\n -t : test mode \n-s [period in ms]: time synchronization\ndefault port: /dev/ttyUSB0\nBaudrate: 38400\n", prog);
    exit(1);
}

void readParameters(int argc, char** argv) {
	
	prog = argv[0];
	portname = (char*) defaultportname.c_str();
	
    // find switches
    for(int i = 1; i < argc; i++) {
        // defined port
        if( strcmp(argv[i], "-p") == 0 ) {
            // no port defined
            if( argc <= (i+1) ) {
                usage();
            } else { // store port and try to open
                portname = argv[++i];
            }
        }
	else { 
	  if( strcmp(argv[i], "-t") == 0 ) {
	    testMode = 1;
	  }
	}   
   }
}


void sendIAmHost(void) {
	
	byte data[2];
	
	data[0] = LOG_MSG;
    data[1] = LOG_I_AM_HOST;
    Chunk c(data, 2);
    Chunk *ack = NULL; 

    // Try 2 times (parity)
    do {
		c.send();
		ack = Chunk::read();
		if (ack == NULL) {// ack
			//cout << "ACK!" << endl;
			return;
		} else if (ack->data[0] == LOG_MSG) {
			insertLogChunk(ack);
			delete ack;
			logs.printCompleted();
			logs.removeCompleted();
		}
	} while (true);
}

void insertLogChunk(Chunk *c) {
	// Log data msg format: <LOG_MSG> <LOG_DATA> <block id (2 bytes) > <message # (1 byte)> < fragment # (1 byte)> < if fragment # = 1, number of fragments. otherwise data> < log data: 17 - 7 = 10>.
	uint16_t blockId = 0;
	uint8_t messageId = 0;
	uint8_t fragmentId = 0;
	uint8_t size = 0;
	uint8_t offset = 6;
	char s[DATA_SIZE+1];
	
	if (c->data[0] == LOG_MSG) {
		if (c->data[1] == LOG_DATA) {
			blockId = (c->data[2] << 8) | c->data[3];
			messageId = c->data[4];
			fragmentId = c->data[5];
			if (fragmentId == 0) {
				size = c->data[6];
				offset = 7;
			}
			memcpy(s, c->data + offset, DATA_SIZE - offset);
			s[DATA_SIZE-offset] = '\0';
			//cout << (int) fragmentId << " " << s << endl;
			// insert(uint16_t bId, uint8_t i, uint8_t f, uint8_t s, std::string str);
			if (c->data[9] == 'M') {
			  // magic offsets here correspond to code in log.bb in function reportLoggerOutOfMemory
			  uint16_t bid = *(uint16_t*)((char*)c->data+2);
			  uint8_t pid = c->data[12];

			  fprintf(stderr, "Maybe out of memory message from %d sending to port %d (%s)\n", (int)bid, (int)pid, c->data+7);
			}
			logs.insert(blockId, messageId, fragmentId, size, string(s));
		}
	}
}

static int kbhit(void){
    struct timeval timeout;
    fd_set read_handles;
    int status;

    // check stdin (fd 0) for activity
    FD_ZERO(&read_handles);
    FD_SET(0, &read_handles);
    timeout.tv_sec = timeout.tv_usec = 0;
    status = select(0 + 1, &read_handles, NULL, NULL, &timeout);

    return status;
}
