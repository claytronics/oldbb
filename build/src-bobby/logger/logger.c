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
#define NUM_COLORS	9
// Usage: ./logger -p /dev/ttyUSB0

using namespace std;

char* portname  = NULL;
string defaultportname = "/dev/ttyUSB0";
int baudrate = 38400;
char* prog = 0;
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
void sendCmd(int);

static int kbhit(void);

int main(int argc, char** argv) {
    
    char c = '0';
    //int i;
    readParameters(argc, argv);
    
    if( !Chunk::initSerial(portname, baudrate)  ) {
		exit(1);
    }
    
    sendIAmHost();
    while(c != 'q') {
      /*cout << "Press enter to send next color..." << endl;
      for(i = 0 ; i < NUM_COLORS ; i++){
	getchar();
	sendCmd(i);
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
      }*/
      cout << "Press enter to proceed to next test" << endl;
      getchar();
      cout << "Testing accelerometer: Tap Blinky Block" << endl;
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
	cout << "Press enter to proceed to next test" << endl;
	getchar();
	cout << "Testing accelerometer (2): Shake Blinky Block" << endl;
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
	cout << "Press enter to proceed to next test" << endl;
	getchar();
	cout << "Type 'q' to quit or any other key to start again..." << endl;
	c = getchar();
    }
	logs.printStats();
	// shutdown everything
	cout << "Closing serial comm...";
	cout.flush();
	
	Chunk::closeSerial();
	cout << " ok!" << endl;
    return 0;
}

void usage(void) {
    printf("%s: [-p portname]\n-l: log\n-s [period in ms]: time synchronization\ndefault port: /dev/ttyUSB0\nBaudrate: 38400\n", prog);
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
void sendCmd(int color) {
	
	byte data[4];
	
	data[0] = LOG_MSG;
	data[1] = LOG_CMD;
	data[2] = seq;
	data[3] = color;
	Chunk c(data, 4);

		printf("Sending setColor(%d)\n", color);
		c.send();
		seq++;
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
