// chunk.h - provide serial comms for host

#ifndef _CHUNK_H_
#define _CHUNK_H_

#include "hostserial.h"

// Chunk format: 
#define DATA_SIZE 17
#define PTR_SIZE 2

// system message defines
#define SYS_CMD     0x12
#define SYS_HANDLER 0x00
#define SYS_BOOT    0xFF
#define CRC_POLY    0xA6
#define DATA_SIZE  17
#define PTR_SIZE 2

#define ACK         0x08
#define ACK_MASK    0xFE
#define NACK        0x0A
#define ESCAPE      0x7D
#define ESCAPE_CHAR 0x20

#define ESCAPE_NEXT     0x04
#define ACK_ODD         0x02
#define ACK_EVEN        0x01    
#define ACK_NONE        0x00    

// parity bits
#define EVEN            0x00
#define ODD             0x01

// frame delimeters
#define FD_MASK         0xFE
#define FD              0x12

// Log system
#define LOG_MSG					0x50

#define LOG_I_AM_HOST			0x01
#define LOG_PATH_TO_HOST		0x02
#define LOG_NEED_PATH_TO_HOST	0x03
#define LOG_DATA				0x04
#define LOG_CMD			        0x05
#define LOG_OUT_OF_MEMORY               0x06
#define LOG_ASSERT                      0x07

class Chunk {
    protected:
	    static bool isSpecial(byte b);
	    static byte crcCalc(byte currCrc, byte newVal);
    public:
	    byte data[DATA_SIZE];
	    Chunk(byte *d, unsigned int size);
	    Chunk(Chunk &c);

	    static Chunk* read();
	    void send();
	    static int initSerial(char* serial, int baudrate);
        static void closeSerial();
	
};

#endif

