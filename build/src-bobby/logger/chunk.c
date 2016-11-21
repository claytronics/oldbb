#include <iostream>
#include <stdlib.h>
#include <stdio.h>
#include <unistd.h>
#include <ctype.h>

#include "chunk.h"
#include "hostserial.h"
#include "logtable.h"

//#define MESSAGE_DEBUG

// seconds
#define READ_TIMEOUT 2

// holds incoming bytes
extern CircBuf serialData;

using namespace std;

Chunk::Chunk(byte *d, unsigned int size) {
	if (size > DATA_SIZE) {
		size = DATA_SIZE;
		cerr << "data field too big to be hold in a Chunk..." << endl;
	}
	memcpy(data, d, size);
    if (size != DATA_SIZE) {
        for (int i = size; i < DATA_SIZE; i++) {
            data[i] = 0;
        }
    }
}

Chunk::Chunk(Chunk &c) {
    memcpy(data, c.data, DATA_SIZE);
}


Chunk* Chunk::read() {
  static uint64_t msgCnt = 0;
  static byte checksum = 0;
  static int index = 0;
  static byte parityNew = 0;
  static byte parityLast = 0;
  static byte data[100];
  static byte wasEscape = 0;

  byte currByte = 0;
  byte ack[1];
  uint64_t time = 0;
    
  while((time/1000000) < 2) {
    while( !isEmpty(&(serialData)) ) {
      currByte = (byte)pop(&(serialData));        
      // is ACK
      if( (currByte & ACK_MASK) == ACK ) {
	// we do not care :)
	//cout << "ACK" << endl;
	return NULL;
	//continue;
      }
      if( (currByte & FD_MASK) == FD ) {
	//cout << "FD" << endl;
	parityNew = currByte & 0x01;
	checksum = 0;
	index = 0;
	wasEscape = 0;
	continue;
      }
      // is an escape char        
      if( currByte == ESCAPE ) {
	//cout << "ESCAPE" << endl;
	wasEscape = 1;
	continue;
      }
      if( wasEscape ) {
	currByte ^= ESCAPE_CHAR;
	wasEscape = 0;
      }
      // is transmitted checksum
      if( index >= (DATA_SIZE + PTR_SIZE) ) {
	//cout << "MSG COMPLETE" << endl;
	// checksum matches!
	if( currByte == checksum ) {
	  //cout << "CHECKSUM OK" << endl;
	  // not a duplicate packet		
	  if((msgCnt == 0) || (parityNew != parityLast)) {  // add to global receive queue

#ifdef MESSAGE_DEBUG
	    switch(data[0]) {
	    case LOG_MSG:
	      cerr << "log " << endl;
	      //cout << (char*) data+7 << endl;
	      //cout << (char*) (data) << endl;
	      break;
	    case NEIGHBOR_MSG:
	      cerr << "neighbor" << endl;
	      break;
	    default:
	      cerr << "unknown" << endl;
	    }
#endif
	    // flip the parity
	    parityLast = parityNew;
	    msgCnt++;
	    // SEND ACK
	    //cout << "parity: " << (int) parityNew << endl;
	    if (data[0] != 0x01 ) {
	      ack[0] = ACK | parityNew;
	      sendMessage(ack, 1);
	    }
	    return new Chunk(data, index-PTR_SIZE);
	  }
	}	
	checksum = 0;
	index = 0;
      }
      // message handler
      if(index < PTR_SIZE ) {
	//cout << "PTR HANDLER" << endl;
      } else { // regular byte
	//cout << "DATA BYTE" << endl;
	data[index-PTR_SIZE] = currByte;
      }
      checksum = crcCalc(checksum, currByte);
      index++;	
    }        
    usleep(500);
    time += 500;
  }
  return NULL;
}

void Chunk::send() {
    int    i;
    int    index     = 0;
    byte   val       = 0;
    byte   checksum  = 0;
    byte   buf[100];
    
    // parity flipping
    static byte parity = 0;
    parity ^= 0x01;

    // create the packet
    buf[index++] = SYS_CMD | parity; // frame delimiter (FD = SYS_CMD)
    buf[index++] = SYS_HANDLER;
    buf[index++] = SYS_HANDLER;

    checksum = crcCalc(checksum, SYS_HANDLER);
    checksum = crcCalc(checksum, SYS_HANDLER);
	
    // load data bytes
    for(i=0; i<DATA_SIZE; i++) {
        val = data[i];
        checksum = crcCalc(checksum, val);
        // special char
        if( isSpecial(val) ) {
            buf[index++] = ESCAPE;
            buf[index++] = val ^ ESCAPE_CHAR;
        }
        // regular
        else {
            buf[index++]  = val;
        }
    }

    // add checksum
    if( isSpecial(checksum) ) {
        buf[index++] = ESCAPE;
        buf[index++] = checksum ^ ESCAPE_CHAR;
    }
    else {
        buf[index++]  = checksum;
    }

    // send the message - let user determine failure
    sendMessage(buf, index);
}

bool Chunk::isSpecial(byte b) {
	return  ((b == ACK)     || (b == (ACK+1))      || (b == NACK) || (b == (NACK+1)) ||
            (b == SYS_CMD) || (b == (SYS_CMD +1)) || (b == ESCAPE ) );
}


// open serial comms
int Chunk::initSerial(char* serial, int baudrate) {
    int baud = (10000000/(16*baudrate)) - 1;

    cerr << "Open serial comm... ";
    if( !startupSerial(serial, 0, baud) )
    {
	cerr << "Invalid Port!" << endl;
        return 0;
    }
    cerr << "ok!" << endl;
    return 1;
}

void Chunk::closeSerial() {
     shutdownSerial();
}


// adds to the crc calculation
byte Chunk::crcCalc(byte currCrc, byte newVal) {
    byte crc = currCrc;
    int i;

    crc ^= newVal;
    for(i=0; i<8; i++) {
        if(0x80 & crc) {
            crc = (crc << 1) ^ CRC_POLY;
        }
        else {
            crc = (crc << 1);
        }
    }

    return crc;
}
