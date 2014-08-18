// hostserial.c - provide serial comms for host

#ifndef _HOSTSERIAL_C_
#define _HOSTSERIAL_C_

#include "hostserial.h"
#include <pthread.h>

// formula: (freq/(16*BAUD)) - 1
// freq = 10 MHz (f_CPU)
/*
#define B9600   64
#define B57600  10
#define B19200  31
#define B38400  15
*/

// baud rate
//#define BAUD_RATE B38400
#define BUF_SIZE  256

extern pthread_mutex_t serialMutex;
extern pthread_mutex_t responseMutex;

extern CircBuf serialData;

// custom serial handle
struct shandle {
    int fd;
    struct termios oldtio;
    char debug;

    pthread_t read_tid;
};

static serialHandle my_sh;
static int weAreWorking = 1;

// read serial data
static void *readSerial(void *h)
{
    struct shandle* sh = (struct shandle*) h;
    byte buf[BUF_SIZE];
   
    int i;

    while(weAreWorking)
    {
        int total = read(sh->fd, buf, BUF_SIZE);
        if (total < 0)
        {
          fprintf(stderr, "error in serial input\n");
            continue;
        } 
      
        for(i=0; i<total; i++)
        {
            // push bytes to circular buffer
            push(buf[i], &(serialData));
	    if (sh->debug > 1) {fprintf(stderr, "%02X %c\n", buf[i],buf[i]);fflush(stdout);}
        }

        //process();
    }
    return NULL;
}

// init serial port
static int initSerial(const char *path, struct termios *oldtio, char debug, int baud)
{
    int fd;
    if (debug>2) printf("%s \n", path);
    fd = open(path, O_RDWR|O_NOCTTY|O_NDELAY);
    if (fd < 0) 
    {
       if (debug>0) printf("Unable to open serial port\n");
       return -1;
    }
    if (debug>2) printf("Open succeeded\n");
    fcntl(fd, F_SETFL, 0);

    struct termios newtio;

    tcgetattr(fd, oldtio); /* save current port settings */
        
    bzero(&newtio, sizeof(newtio));
  
    /*set baudrate*/
    newtio.c_cflag = baud | CS8 | CLOCAL | CREAD;
    newtio.c_iflag = IGNPAR;
    newtio.c_oflag = 0;
        
    /* set input mode (non-canonical, no echo,...) */
    newtio.c_lflag = 0;
          
    newtio.c_cc[VTIME]    = 0;   /* inter-character timer unused */
    newtio.c_cc[VMIN]     = 1;   /* blocking read until 1 char received */
        
    tcflush(fd, TCIFLUSH);
    tcsetattr(fd,TCSANOW,&newtio);
  
    return fd;
}


// begin serial comms
int startupSerial(const char *port, char debug, int baud)
{
    struct shandle* sh = (struct shandle*) malloc (sizeof (struct shandle));
    sh->fd = initSerial(port, &sh->oldtio,debug, baud);
    if (sh->fd < 0) {
        free(sh);
        return 0;
    }

    sh->debug = debug;
    weAreWorking = 1;
    pthread_create(&sh->read_tid, NULL, readSerial, (void *)sh);

    pthread_mutex_init(&serialMutex, NULL);
    pthread_mutex_init(&responseMutex, NULL);

    my_sh = (serialHandle) sh;
    return 1;
}

// close all serial comms
void shutdownSerial()
{
    struct shandle* sh = (struct shandle*) my_sh;
    weAreWorking = 0;
    pthread_join(sh->read_tid,0);

    pthread_mutex_destroy(&serialMutex);
    pthread_mutex_destroy(&responseMutex);
   
    tcsetattr(sh->fd, TCSANOW, &sh->oldtio);
    close(sh->fd);
    free( sh );
}

// just grab bytes
void process()
{
  //SCG (unused) byte curr;

    while( !isEmpty(&(serialData)) )
    {
      //SCG (unused) curr = (byte)
      pop(&(serialData));

        // store last received byte
//        pthread_mutex_lock(&responseMutex);
//            gotResp  = 1;
//            recdByte = curr;
//        pthread_mutex_unlock(&responseMutex);
    }    
}

// send a fully formatted message on the serial
int sendMessage(byte* data, byte size)
{
    struct shandle* sh = (struct shandle*) my_sh;
    int i;

    // send on the serial
//    fprintf(stderr, "Sending: ");
//    for (i = 0; i<size; i++) fprintf(stderr, "%02x ", data[i]);
//    fprintf(stderr, "\n");
    pthread_mutex_lock(&serialMutex);
//    fprintf(stderr, "Writing ...\n");
        i = write(sh->fd, data, size);
    pthread_mutex_unlock(&serialMutex);
//    fprintf(stderr, "Wrote: %d\n", i);
    if (i != size) {
	fprintf(stderr, "Error: Wrote incorrect bytes, %d, when should have written %d\n", i, size);
    }
    return 1;
}

#endif

// Local Variables:
// mode: C
// indent-tabs-mode: nil
// c-basic-offset: 4
// End:
