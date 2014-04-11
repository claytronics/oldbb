#ifndef _REPROGRAMMER_C_
#define _REPROGRAMMER_C_

#include <stdlib.h>
#include <stdio.h>
#include <unistd.h>
#include <ctype.h>
#include <sys/stat.h>
#include "string.h"
#include "hostserial.h"
#include "time.h"

// host defines
#define VERSION "1.0"
#define C_BOOT  'B'
#define C_PROG  'R'
#define C_SPAN  'S'
#define C_QUIT  'Q'

// command line read defines
#define MAX_LENGTH  10

// serial send defines
#define NUM_RETRIES 4
#define WAIT_DELAY  5
#define SEND_DELAY  30000

// reprogrammer defines
#define RC_VERSION  "1.2"
#define PROG_CMD    0x50
#define WRITEOUT1   0xFF
#define WRITEOUT2   0xFE
#define HEX_END     0xFF
#define ADDR_LENGTH 2
#define REV1B_PAGE_SIZE   256
#define REV1C_PAGE_SIZE   512
#define PDATA_SIZE  18

// system message defines
#define SYS_CMD     0x12
#define SYS_HANDLER 0x00
#define SYS_BOOT    0xFF
#define CRC_POLY    0xA6
#define UDATA_SIZE  17
#define UMSG_SIZE   (UDATA_SIZE + 4)

#define REV_QUERY   0x51

#define ACK         0x08
#define NACK        0x0A
#define ESCAPE      0x7D
#define ESCAPE_CHAR 0x20

// spanning tree defines
#define SPAN_TREE   0x54
#define SPAN_REQ    0x52
#define SPAN_YES    0x59
#define SPAN_NO     0x4E
#define SPAN_MSG    {SPAN_TREE, SPAN_REQ}
#define SMSG_SIZE   2

char* portname    = NULL;
char* hexFilename = NULL;
FILE* hexFile     = NULL;

int verbose = 0;		/* set to 1 to print out extra info */
int readverbose = 0;
char* prog = 0;			/* name of program as called from shell */

int baudrate      = 38400;

byte sentByte = 0;
uint16_t page_size;

pthread_mutex_t serialMutex;
pthread_mutex_t circbuffMutex;
pthread_mutex_t responseMutex;

// holds incoming bytes
CircBuf serialData;

// keyboard hit
static int kbhit(void){
    struct timeval timeout;
    fd_set read_handles;
    int status;

    // check stdin (fd 0) for activity
    FD_ZERO(&read_handles);
    FD_SET(0, &read_handles);
    timeout.tv_sec = timeout.tv_usec = 0;
    status = select(0 + 1, &read_handles, NULL, NULL, &timeout);

    // remove character
    if(status) getchar();

    return status;
}

// clears the screen
void clearScreen(void)
{
    static int needTitle = 1;
    if (needTitle) {
	printf("Ensemble Reprogrammer (v.%s)\n\n", VERSION);
	needTitle = 0;
    }
    fprintf(stdout, "\n"); fflush(stdout);
#if 0
    int i;

    #ifdef UNIX
        i = system("clear");
    #else
        i = system("cls");
    #endif
#endif
}

// usage instructions
void usage(void)
{
    printf("%s: [-p] [portname] [-f] [default filename] [-b] [baud] [-v]\n\n", prog);
    exit(1);
}

// print a menu screen
void menu(void)
{
    // clear the screen
    clearScreen();

    printf("Host Terminal (v.%s)\n\n", VERSION);
    printf("Choose a task:\n");
    printf("%c - Force ensemble into bootloader mode\n", C_BOOT);
    printf("%c - Reprogram cubes\n", C_PROG);
    printf("%c - Build a spanning tree\n", C_SPAN);
    printf("%c - Quit\n", C_QUIT);

    printf("\nSelection: ");
}

// getline which removes terminating character
int mygetline(char** n, size_t* l, FILE** b)
{
    // get the line
    int length = getline(n, l, *b);

    // check for data
    if( length == 0 )
    {
        return 0;
    }

    // parse and return
    length--;
    *(*n + length) = 0;

    return length;
}

// edit slash directions
void editPath(char* p)
{
  return;

#if 0 // What is this trying to do? Currently it breaks things!
    while( *p )
    {
        #ifdef UNIX
            if( *p == '\\' ) {
                *p = '/';
            }
        #else
            if( *p == '/' ) {
                *p = '\\';
            }
        #endif

        p++;
    }
#endif
}

// open serial comms
int openSerial(char* serial) 
{
    int baud = (10000000/(16*baudrate)) - 1;

    if( !startupSerial(serial, 0, baud) )
    {
        printf("Invalid Port!\n\n");
        return 0;
    }
    return 1;
}

// open the hex file
int openFile(FILE** file, char* filename) 
{
    // dirty hack to get fopen to work properly
    int l = strlen(filename);
    char edited[l+1];
    memcpy(edited, filename, l);
    edited[l] = 0;

    // edit path (fix slashes)
    editPath(edited);

    // open hexfile
    *file = fopen(edited, "r");
    if(*file == NULL)
    {
        printf("Invalid file!\n\n");
        return 0;
    }
    return 1;
}

// convert char representation into hex
byte toByte(char c)
{
    switch(c) {
        case '0': return 0x0;
        case '1': return 0x1;
        case '2': return 0x2;
        case '3': return 0x3;
        case '4': return 0x4;
        case '5': return 0x5;
        case '6': return 0x6;
        case '7': return 0x7;
        case '8': return 0x8;
        case '9': return 0x9;
        case 'A':
        case 'a': return 0xA;
        case 'B':
        case 'b': return 0xB;
        case 'C':
        case 'c': return 0xC;
        case 'D':
        case 'd': return 0xD;
        case 'E':
        case 'e': return 0xE;
        case 'F':
        case 'f': return 0xF;
        default:  return 0xFF;
    }
}

// splits a line that falls on a page boundary into two lines, each on its own page
void splitLine(uint16_t orig_addr, byte *orig_data, int orig_len, int page_size, uint16_t *addr1,
               byte *data1, byte *len1, uint16_t *addr2, byte *data2, byte *len2)
{
    *len1 = ((((orig_addr-1) / page_size) + 1) * page_size) - orig_addr;
    *len2 = orig_len - *len1;

    *addr1 = orig_addr;
    *addr2 = orig_addr + *len1;

    data1[0] = (byte)((*addr1 & 0xFF00) >> 8);
    data1[1] = (byte)(*addr1);
    data2[0] = (byte)((*addr2 & 0xFF00) >> 8);
    data2[1] = (byte)(*addr2);

    int i;
    for(i=0; i<*len1; i++){
        data1[2+i] = orig_data[2+i];
    }
    for(i=0; i<*len2; i++){
        data2[2+i] = orig_data[2+*len1+i];
    }
    /*
    printf("orig_addr: %x addr1: %x addr2: %x\n", orig_addr, *addr1, *addr2);
    printf("orig_len: %x len1: %x len2 %x\n", orig_len, *len1, *len2);
    printf("orig_data:");
    for(i=0; i<orig_len+2; i++){
        printf(" %d", orig_data[i]);
    }
    printf("\ndata1:");
    for(i=0; i<*len1+2; i++){
        printf(" %d", data1[i]);
    }
    printf("\ndata2:");
    for(i=0; i<*len2+2; i++){
        printf(" %d", data2[i]);
    }
    printf("\n");*/
}

// read one hex file line
int 
getOneLine(uint16_t* addr, byte* data, FILE** f)
{
    byte length   = 0;
    byte checksum = 0;
    byte type     = 0;
    byte val;
    char c;
    int  i;
    int offset = 0;

    // wait for a newline (":", ignore comment lines ";")
    do {
        c = (char)fgetc(*f);
	offset++;

        if( feof(*f) ) {
            return 0;
        }
    }
    while (c != ':');

    // extract line length
    length    = (toByte((char)fgetc(*f))) << 4;
    length   += (toByte((char)fgetc(*f)));
    checksum += length;

    // check line length
    if( length > PDATA_SIZE ) {
      fprintf(stderr, "%d > %d @ byte 0x%p+%d\n", length, PDATA_SIZE, addr, offset);
        return -length;
    }

    // extract address
    *addr = 0x0000;
    for(i=0; i<ADDR_LENGTH; i++)
    {
        val  = (toByte((char)fgetc(*f))) << 4;
        val += (toByte((char)fgetc(*f)));
        checksum += val;

        data[i]  = val;
        *addr   |= val << ((ADDR_LENGTH-i-1)*8);
    }

    // data type
    type  = (toByte((char)fgetc(*f))) << 4;
    type += (toByte((char)fgetc(*f)));
    checksum += type;

    // end of data
    if( type )
    {
        // check checksum matches
        val  = (toByte((char)fgetc(*f))) << 4;
        val += (toByte((char)fgetc(*f)));
        checksum += val;

        // checksum error
        if( checksum ) {
	  fprintf(stderr, "Checksum error @ 0x%p+%d\n", addr, offset);
            return -1;
        }
        // checksum ok
        else {
            return 0;
        }
    }

    // is data
    for(i=0; i<length; i++)
    {
        val  = (toByte((char)fgetc(*f))) << 4;
        val += (toByte((char)fgetc(*f)));
        checksum += val;

        data[i + ADDR_LENGTH] = val;
    }

    // confirm checksum
    val  = (toByte((char)fgetc(*f))) << 4;
    val += (toByte((char)fgetc(*f)));
    checksum += val;

    // checksum error
    if( checksum ) {
	  fprintf(stderr, "second Checksum error @ 0x%p+%d\n", addr, offset);
        return -1;
    }
    // checksum ok
    else {
        return length;
    }
}

// print a chopped line of the hex file
void printDataLine(byte* data, int length)
{
    int i;

    printf("addr: %02X%02X\ndata: ", data[0], data[1]);
    for(i=0; i<length; i++) {
        printf("%02X ",data[i+ADDR_LENGTH]);
    }
}

// send bootloader message
int sendBootloaderMessage(byte* data, byte size)
{
    // too large
    if(size > PDATA_SIZE) { return 0; }

    byte buf[size*2];
   
    int  index    = 0;
    int  i;
    byte val;
    byte checksum = 0;

    // send message type
    val = PROG_CMD;
    buf[index++] = val;
    checksum    += val;

    // send number of valid bytes
    val = size - ADDR_LENGTH;
    buf[index++] = val;
    checksum    += val;

    // send data
    for(i=0; i<size; i++)
    {
        val = *(data+i);
        checksum += val;

        // send regular
        buf[index++] = val;
    }

    // send padding bytes
    for(i=0; i<(PDATA_SIZE - size); i++)
    {
        buf[index++] = 0x00;
    }

    // fix checksum
    checksum = (checksum ^ 0xFF) + 1;

    // send checksum
    buf[index++] = checksum;

    // send on the serial
    sentByte = checksum;
    return sendMessage(buf, index);
}

int bytesperdot = 1;

// print a dot every 2% of the file
void
maybeprintdot(int len)
{
    static int beforedot = 0;
    beforedot -= len;
    while (beforedot <= 0) {
	printf(".");
	fflush(stdout);
	beforedot += bytesperdot;
    } 
}

// send with retries
int bootloaderSend(byte* data, byte length)
{
    int    i, count;
    byte   recdByte = 0;

    // send a couple of times
    for(i=0; i<NUM_RETRIES; i++)
    {
        // try sending it once
        sendBootloaderMessage(data, length + ADDR_LENGTH);
        //usleep(SEND_DELAY);
        
        // wait for a confirmation or 1 second
        count = 0;
        time_t endTime = time(NULL) + WAIT_DELAY;

        while( isEmpty(&(serialData)) && (time(NULL) < endTime) );
        
        // got a byte
        if( !isEmpty(&(serialData)) )
        {
            // consume all but last received byte
            while( !isEmpty(&(serialData)) ) {
                recdByte = (byte)pop(&(serialData));
            }

            if (verbose) {
		printf("a: %02X%02X    s: %02X    r: %02X\r", data[0], data[1], sentByte, recdByte);
		fflush(stdout);
	    } else {
		maybeprintdot(length);
	    }

            // got a matching checksum
            if( sentByte == recdByte ) {
                return 1;
            }
        }
        // no response
        else {
            printf("a: %02X%02X    s: %02X    r: none\n", data[0], data[1], sentByte);
        }
    }

    // ran out of retries
    if( i >= NUM_RETRIES ) {
        printDataLine(data, length);
        printf("\nRetry Failed - Reprogramming aborted!\n");
        return 0; 
    }

    return 1;
}

// function to reprogram a cube
void reprogram()
{
    byte data[PDATA_SIZE];
    byte sysData[2] = {WRITEOUT1, WRITEOUT2};
    
    uint16_t addr;
    uint16_t currPage = page_size;

    int    length;
    int    success = 1;
    
    // clear the screen
    clearScreen();
    printf("Reprogramming started ...\n");
    printf("|------------------------------------------------|\n");

    // read file and send over serial
    while( success && !feof(hexFile) )
    {
        length = getOneLine(&addr, data, &hexFile);

        // read error
        if( length < 0 ) {
	  printf("read line error: %d\n", length);
            success = 0;
            continue;
        }
        // should be end of file
        else if( length == 0 ) {
            continue;
        }
        // data line
        else {
            // do nothing - fall through
        }

        // data is for new page
        //while( addr >= currPage )
        if(addr + length >= currPage) {
            uint16_t addr1, addr2;
            byte len1, len2;
            byte data1[18], data2[18];
            splitLine(addr, data, length, page_size, &addr1, data1, &len1, &addr2, data2, &len2);

            if(len1 > 0){
                success = bootloaderSend(data1, len1);
            }

            while(addr + length >= currPage)
            {
                currPage += page_size;

                // write out previous page
                success = bootloaderSend(sysData, 0);

                // failed to write-out
                if( !success ) {
                    continue;
                }

                // clear screen
//                clearScreen();
//                printf("Reprogram Cubes (v.%s)\n\n", RC_VERSION);
            }

            if(len2 > 0){
                success = bootloaderSend(data2, len2);
            }

            continue;
        }

        // within page range - send data packet
        if( (addr < currPage) && (addr >= currPage-page_size) ) {
            success = bootloaderSend(data, length);
        }
        // out-of-bounds
        else {
            printDataLine(data, length);
            printf("\nBounds Error!  Range: %04X - %04X\n", currPage-page_size, currPage);
            success = 0;
        } 
    }

    // okay so far - send the end of data message
    if( success ) {
        memset(data, HEX_END, 2);
        success = bootloaderSend(data, 0);
    }

    // successfully completed
    if( success ) {
        printf("\nEnsemble successfully reprogrammed!\n");
    }

    // close file and free memory
    fclose(hexFile);
}

// adds to the crc calculation
byte crcCalc(byte currCrc, byte newVal)
{
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

// send a system message
void systemSend(byte type)
{
    byte data[UMSG_SIZE*2];

    int    i;
    int    index     = 0;
    byte   val       = 0;
    byte   checksum  = 0;

    // parity flipping
    static byte parity = 0;
    parity ^= 0x01;

    // create the packet
    data[index++] = SYS_CMD | parity;
    data[index++] = SYS_HANDLER;
    data[index++] = SYS_HANDLER;

    checksum = crcCalc(checksum, SYS_HANDLER);
    checksum = crcCalc(checksum, SYS_HANDLER);

    // load data bytes
    for(i=0; i<UDATA_SIZE; i++) {
        val = type ^ i;
        checksum = crcCalc(checksum, val);
        
        // special char
        if( (val == ACK)     || (val == (ACK+1))      || (val == NACK) || (val == (NACK+1)) ||
            (val == SYS_CMD) || (val == (SYS_CMD +1)) || (val == ESCAPE ) ) {
            data[index++] = ESCAPE;
            data[index++] = val ^ ESCAPE_CHAR;
        }
        // regular
        else {
            data[index++]  = type ^ i;
        }
    }

    // add checksum
    if( (checksum == ACK)     || (checksum == (ACK+1))      || (checksum == NACK) || (checksum == (NACK+1)) ||
        (checksum == SYS_CMD) || (checksum == (SYS_CMD +1)) || (checksum == ESCAPE ) ) {
        data[index++] = ESCAPE;
        data[index++] = checksum ^ ESCAPE_CHAR;
    }
    else {
        data[index++]  = checksum;
    }

    // send the message - let user determine failure
    sendMessage(data, index);
    //usleep(SEND_DELAY);

    if (verbose) printf("Sent type %02X system message.\n", type);
        
    // flush serial buffer (extraneous system messages)
    while( !isEmpty(&(serialData)) )
    {
        pop(&(serialData));
    }

}

char getVersion(void)
{
    byte query = REV_QUERY;
    sendMessage(&query, 1);
    byte i;


    for(i=0; i<NUM_RETRIES; i++)
    {
        time_t endTime = time(NULL) + WAIT_DELAY;

        while( isEmpty(&(serialData)) && (time(NULL) < endTime) );
    
        // got response byte 1
        if( !isEmpty(&(serialData)) ) {
            return pop(&(serialData));
        }
        // timed out, resend
        else {
            printf("Failed to read block version.\n");
            continue;
        }
    }

    return (char)0;
}

// create a spanning tree
void createSpan(void)
{
    int    i, count;
    int    success = 0;
    byte   recdByte;
    byte   data[2] = SPAN_MSG;
    time_t endTime;

    // try sending a couple of times
    for(i=0; i<NUM_RETRIES; i++)
    {
        // flush buffer
        while( !isEmpty(&(serialData)) ) {
            pop(&(serialData));
        };

        // try sending it once
        sendMessage(data, SMSG_SIZE);
        if (verbose) {
	    printf("Sent: %c%c    ", (char)data[0], (char)data[1]);fflush(stdout);
	}

        // wait for data
        count = 0;       
        endTime = time(NULL) + WAIT_DELAY;

        while( isEmpty(&(serialData)) && (time(NULL) < endTime) );


        // got response byte 1 (try until it is "T" or run out of bytes)
        while( !isEmpty(&(serialData)) ) {
            recdByte = (byte)pop(&(serialData));
            success = (recdByte == SPAN_TREE);

            if( success ) {
                if (verbose) {
		    printf("Recd: %c", (char)recdByte);fflush(stdout);
		}
                break;
            }
        }

        //timed out, resend
        if( !success ) {
	    if (verbose) {
		printf("Recd: (none)(none)\n");fflush(stdout);
	    }
            success = 0;

            continue;
        }

        // wait for data
        count = 0;       
        endTime = time(NULL) + WAIT_DELAY;

        while( isEmpty(&(serialData)) && (time(NULL) < endTime) );


        // got response byte 2
        if( !isEmpty(&(serialData)) ) {
            recdByte = (byte)pop(&(serialData));
            if (verbose) printf("%c\n", (char)recdByte);

            success = success && (recdByte == SPAN_YES);
        }
        // timed out, resend
        else {
            success = 0;
            if (verbose) printf("(none)\n");
        }

        // good response
        if( success ) {
            break;
        }
    }

    // flush buffer
    while( !isEmpty(&(serialData)) ) {
        pop(&(serialData));
    };

    // ran out of retries
    if( i >= NUM_RETRIES ) {
        printf("Unable to create spanning tree!\n");
    }
}

// main screen
int main(int argc, char** argv)
{    
    int    i;
    size_t maxLength = MAX_LENGTH;
    char   version;
    
    // clear the screen
    clearScreen();
    
    prog = argv[0];

    // find switches
    for(i=1; i<argc; i++)
    {
        // defined port
        if( strcmp(argv[i], "-p") == 0 )
        {
            // no port defined
            if( argc <= (i+1) ) {
                usage();
            }
            // store port and try to open
            else {
                portname = argv[++i];
                
                if( !openSerial(portname) ) {
                    exit(1);
                }
            }
        }
        // defined default hex file
        else if( strcmp(argv[i], "-f") == 0 )
        {
            // no file defined
            if( argc <= (i+1) ) {
                usage();
            }
            // store file and try opening
            else {
                hexFilename = argv[++i];
                
                // confirm file validity
                if( !openFile(&hexFile, hexFilename) ) {
                    exit(1);
                }
            }
        }
        // defined baud rate
        else if( strcmp(argv[i], "-b") == 0 )
        {
            // no baud defined
            if( argc <= (i+1) ) {
                usage();
            }
            // store baud
            else {
                baudrate = atoi(argv[++i]);

                // serial already open
                if(portname != NULL) {
                    // close and reopen serial to ensure correct baud rate
                    shutdownSerial();
                    openSerial(portname);
                }
            }
        }
	else if (strcmp(argv[1], "-v") == 0 )
	{
	    verbose = 1;
	}
	else if (strcmp(argv[1], "-x") == 0 )
	{
	    readverbose = 1;
	}
        // invalid switch
        else
        {
            usage();
        }
    }

    // designate and prompt
    clearScreen();

    // no port entered yet - read from cmd line
    if( portname == NULL )
    {
        // init
        portname = malloc(MAX_LENGTH + 1);
            
        do {
            printf("Enter serial port: ");
    
            // get portname of serial port
            mygetline(&portname, &maxLength, &stdin);
        }
        while( !openSerial(portname) );
        free(portname);
    }

    // designate and prompt
    clearScreen();

    // no file entered yet - read from cmd line
    if( hexFilename == NULL )
    {
        // init
        hexFilename = malloc(MAX_LENGTH + 1);

        do {
            printf("Enter path of hex file: ");
    
            // get filename of hex file and open
            mygetline(&hexFilename, &maxLength, &stdin);
        }
        while( !openFile(&hexFile, hexFilename) );
    }

    // get size of hexfile
    {
	struct stat sbuf;
	if (stat(hexFilename, &sbuf)) {
	    fprintf(stderr, "Can't stat '%s'\n", hexFilename);
	    exit(-1);
	}
	bytesperdot = ((16*(sbuf.st_size/42))/50)+1;
    }

/*
    // continually build spanning tree
    do {
        // designate and prompt
        clearScreen();
        printf("Ensemble Reprogrammer (v.%s)\n\n", VERSION);
        printf("Press Enter when all blocks are blue/green...\n\n");

//printf("sysSend\n");fflush(stdout);
        systemSend(SYS_BOOT);
        usleep(250000);
//printf("createSpan\n");fflush(stdout);
        createSpan();
        usleep(50000);
//printf("done\n");fflush(stdout);
    }
    // while not quit
    while( !kbhit() );
*/
//////// hack to get it to work
    clearScreen();
    printf("Press Enter when all blocks are in bootloader mode (blue/red)...\n\n");

    do {
        // designate and prompt
        systemSend(SYS_BOOT);
        usleep(250000);
	printf("."); fflush(stdout);
    }
    // while not quit
    while( !kbhit() );

    clearScreen();
    printf("Press Enter when all blocks are in spanning tree (blue/green)...\n\n");

    do {
        // designate and prompt

        createSpan();
        usleep(50000);
	printf("."); fflush(stdout);
    }
    // while not quit
    while( !kbhit() );

//////////////////////////////

    clearScreen();
        
    // send one last time for good measure
    //systemSend(SYS_BOOT);
    //usleep(250000);
    createSpan();

    // get version as well
    version = getVersion();
    if(version == (char) 0){
        printf("Failed to read block version.  Exiting...\n");
        return 1;
    }
    else if(version == 'b'){
        page_size = REV1B_PAGE_SIZE;
    }
    else if(version == 'c'){
        page_size = REV1C_PAGE_SIZE;
    }

    // reprogram
    reprogram();
  
    // shutdown everything
    shutdownSerial();

    return 0;
}

#endif

