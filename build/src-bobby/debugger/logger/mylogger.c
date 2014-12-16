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
#include "mylogger.h"

#define LOG_CMD		0x05	
#define LOG_MSG 	0x50


//CMD TYPES
#define COLOR_SET	0x10
#define ENSEMBLE_RESET	0x11
#define SET_ID	        0x12
#define ENSEMBLE_COUNT  0x14



#define NUM_COLORS	9

// Usage: ./logger -p /dev/ttyUSB0 [-t]

using namespace std;

char* portname  = NULL;
string defaultportname = "/dev/ttyUSB0";
int baudrate = 38400;
char* prog = 0;

byte testMode = 0;
int seq = 1;
volatile uint8_t tree_count = 0;
volatile uint8_t resp_rxed;
//std::string log_message;

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
void stringifyLogs(void);

void sendColorCmd(int);
void sendIDToSet(uint16_t idToSet);
void sendResetCmd(void);
void send_tree_count(void);


//void send_read_register(void);

static int kbhit(void);

	int 
mylogger(int argc, char** argv) 
{

	int i;
	char c = '0';
	//sendColorCmd(6);
	//receiveLogs();
	printf("!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!%s:%d\n",__FUNCTION__,__LINE__);
	//readParameters(argc, argv);

	/*portname = (char*) defaultportname.c_str();
	cout<<"portname"<<portname;

	if( !Chunk::initSerial(portname, baudrate)  ) {
		exit(1);
	}

	printf("%s:%d\n",__FUNCTION__,__LINE__);
	sendIAmHost();
	printf("%s:%d\n",__FUNCTION__,__LINE__);

	//receiveLogs();

	sendColorCmd(4);
	receiveLogs();
	while(1);

	printf("%s:%d\n",__FUNCTION__,__LINE__);
	logs.printStats();
	printf("%s:%d\n",__FUNCTION__,__LINE__);
	// shutdown everything
	cout << "Closing serial comm...";
	cout.flush();
	printf("%s:%d\n",__FUNCTION__,__LINE__);

	Chunk::closeSerial();*/
	printf("%s:%d\n",__FUNCTION__,__LINE__);
	cout << " ok!" << endl;
	printf("%s:%d\n",__FUNCTION__,__LINE__);

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

void sendIDToSet(uint16_t idToSet) 
{

	byte data[5];

	data[0] = LOG_MSG;
	data[1] = LOG_CMD;
	data[2] = SET_ID;
	data[3] = (idToSet >> 8) & 0x00FF;
	data[4] = (idToSet & 0x00FF); 

	Chunk c(data, 5);

	printf("Setting ID %u on block...\n", idToSet);
	c.send();
}

void sendResetCmd(void) 
{	
	byte data[4];

	data[0] = LOG_MSG;
	data[1] = LOG_CMD;
	data[2] = ENSEMBLE_RESET;
	data[3] = 4;

	Chunk c(data, 4);

	printf("Resetting the system\n");
	c.send();
}

void send_tree_count(void)
{
  byte data[3];
	
  data[0] = LOG_MSG;
  data[1] = LOG_CMD;
  data[2] = ENSEMBLE_COUNT;
	
  Chunk c(data, 3);

  printf("getting tree count\n");
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


void stringifyLogs(void)
{
	log_message = "";
	printf("%s:%d\n",__FUNCTION__,__LINE__);
	while(1) {
		Chunk *ch = Chunk::read();
		if ( ch != NULL) {
			if (ch->data[0] == LOG_MSG) {			
				insertLogChunk(ch);
			}
			delete ch;
		}
		//logs.printAll();
		logs.stringifyCompleted();
		logs.removeCompleted();
	} 
}

void usage(void) {
	printf("%s: [-p portname]\n-l: log\n -t : test mode \n-s [period in ms]: time synchronization\ndefault port: /dev/ttyUSB0\nBaudrate: 38400\n", prog);
	exit(1);
}

void readParameters(int argc, char** argv) {

	//prog = argv[0];
	portname = (char*) defaultportname.c_str();
	cout<<"portname"<<portname;

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
		}
		// Message is an emergency chunk to report an out of memory error (No free chunk available)
		else if (c->data[1] == LOG_OUT_OF_MEMORY) {
			blockId = (c->data[3] << 8) | c->data[4];
			uint8_t pid = c->data[2];
			fprintf(stderr, "Block  %d got out of memory while sending to port %d\n", (int)blockId, (int)pid);
			return;
		}
		// Message is an assert report, allows us to identify what the location of the triggered assert is
		else if (c->data[1] == LOG_ASSERT) {
			blockId = (c->data[2] << 8) | c->data[3];
			uint8_t fileNumber = c->data[4];
			uint8_t lineNumber = (c->data[5] << 8) | c->data[6];
			fprintf(stderr, "Assert triggered on block %d. File: %d Line: %d\n", (int)blockId, (int)fileNumber, (int)lineNumber);
			return;
		}
		else if (c->data[1] == ENSEMBLE_COUNT) {
			blockId = (c->data[2] << 8) | c->data[3];
			tree_count = c->data[4];
			fprintf(stderr, "treecount = %d\n",tree_count);
			resp_rxed = 1;
			return;
		}
		logs.insert(blockId, messageId, fragmentId, size, string(s));
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
/*
void send_read_register(uint16_t address) {

	byte data[5];

	data[0] = LOG_MSG;
	data[1] = LOG_CMD;
	data[2] = REGISTER_READ;
	data[3] = LSB(address);
	data[4] = MSB(address);
	Chunk c(data, 5);

	printf("Sending read_register(0x%x)\n", address);
	c.send();
}
*/
/*
void send_read_memory(uint16_t address,uint16_t num_bytes) {
	byte data[5];

	data[0] = LOG_MSG;
	data[1] = LOG_CMD;
	data[2] = MEM_READ;
	data[3] = LSB(address);
	data[4] = MSB(address);
	data[5] = LSB(num_bytes);
	data[6] = MSB(num_bytes);
	Chunk c(data, 7);

	printf("Sending read_memory(address %x)(num bytes %d)\n", address,num_bytes);
	c.send();

}
*/

//ask for the number of blocks present in the ensemble
/*void ask_attendence() {
	byte data[4];
	data[0] = log_MSG;
	data[1] = LOG_CMD;
	data[2] = SEND_ATTENDANCE;
	data[3] = 4;
	Chunk c(data, 4);
	c.send();
}*/
