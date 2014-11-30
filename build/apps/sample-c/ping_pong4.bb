#include "block.bbh"

#define PING 1
#define PONG 2
#define QUERY 'Q'
#define READY 'R'
#define ACK_TIMEOUT	500

//typedef byte(*MsgHandler)(void );
typedef void handler_t(int);


threadvar byte activePort;
threadvar byte who_sent_me_ack;
threadvar byte ack_recvd;
threadvar byte ping_recvd;
//threadvar byte pong_recvd;
//threadvar byte he_is_ready;
volatile byte alarm_out;
byte kolor;


//void sendPing(byte);
//void sendPong(byte);
//void got_pong(void);
//void got_ping(void);
byte sendMyChunk(PRef port_t, byte *data, byte size, MsgHandler mh); 
void freeMyChunk(void);
void alarm_handler(int sig);

//void check_is_he_ready(byte where);
//void tell_me_you_are_ready(void);
//void got_ready(void);
//handler_t *Signal(int signum, handler_t *handler);

void packet_timed_out(void);

threadvar Timeout packet_deadline;
threadvar int county;

Time sendTime;

char message[100];

void myMain(void)
{ 
	//watchdog_reset();
        //he_is_ready = 0;
        county = 0;
	setColor(RED);

                while (1) {
			//printDebug("Top");
                        alarm_out = 0;
                        packet_deadline.callback = (GenericHandler)(&packet_timed_out);
                        packet_deadline.arg = 0;
                        packet_deadline.calltime = getTime() + 2000;
                        registerTimeout(&packet_deadline);
			
        		//fprintf(stderr,"[id = %d] Sending Inquiry \n",getGUID());
                        //pong_recvd = 0;
			while(!alarm_out);
			kolor++;
			setColor((getGUID()+kolor)%8);
                        deregisterTimeout(&packet_deadline);
			//printDebug("bottom");
                }
		printDebug("Out");
        while(1);

}

void userRegistration(void)
{
        registerHandler(SYSTEM_MAIN, (GenericHandler)&myMain);
}

/*
void check_is_he_ready(byte where) {
        fprintf(stderr,"[id = %d] Sending Inquiry \n",getGUID());
        byte data[2];
        data[0] = QUERY;
        data[1] = getGUID();
        //sendMyChunk(who_sent, data, 2, (MsgHandler)sendAck_and_pong);
        sendMyChunk(where, data, 2, (MsgHandler)tell_me_you_are_ready);
}
*/

/*
void tell_me_you_are_ready(void) {
        byte who_sent;
        who_sent = thisChunk->data[0];
        fprintf(stderr,"[id = %d] Sending I am ready \n",getGUID());
        byte data[2];
        data[0] = READY;
        data[1] = getGUID();
        sendMyChunk(DOWN, data, 2, (MsgHandler)got_ready);
}
*/
/*
void got_ready(void) {
        fprintf(stderr,"[id = %d] He is ready \n",getGUID());
        he_is_ready = 1;
}
*/
/*
void sendPing(byte who_sent) {
        //fprintf(stderr,"[id = %d] Sending Ping \n",getGUID());
        ack_recvd = 0;
        byte data[2];
        data[0] = PING;
        data[1] = getGUID();
        //sendMyChunk(who_sent, data, 2, (MsgHandler)sendAck_and_pong);
        sendMyChunk(who_sent, data, 2, (MsgHandler)got_ping);
}
*/
/*
void sendPong(byte who_sent) {
        //fprintf(stderr,"[id = %d] Sending Pong \n",getGUID());
        ack_recvd = 0;
        byte data[2];
        data[0] = PONG;
        data[1] = getGUID();
        //sendMyChunk(who_sent, data, 2, (MsgHandler)sendAck_and_ping);
        sendMyChunk(who_sent, data, 2, (MsgHandler)got_pong);
}
*/
/*
void got_pong(void){
        fprintf(stderr,"[id = %d] Got Pong \n",getGUID());
	printDebug("Got pong\n");
        if(thisChunk->data[0] != PONG) 
                return;
        pong_recvd = 1;
}
*/

/*

void got_ping(void){
        fprintf(stderr,"[id = %d] Got Ping \n",getGUID());
	printDebug("Got ping\n");
        if(thisChunk->data[0] != PING) 
                return;
        ping_recvd = 1;
}
*/

byte sendMyChunk(PRef port_t, byte *data, byte size, MsgHandler mh) 
{ 
        Chunk *c = getSystemTXChunk();
        if (c == NULL){fprintf(stderr,"no memory\n"); return 0;}
        if ((sendMessageToPort(c, port_t, data, size, mh, 0)))
        {
                //fprintf(stderr,"%d,%s,%s\n",__LINE__,__FUNCTION__,__FILE__);
                freeChunk(c);
                return 0;
        }
        return 1;
}

        void 
freeMyChunk(void)
{
        fprintf(stderr,"%d,%s,%s\n",__LINE__,__FUNCTION__,__FILE__);
        free(thisChunk);
}


//timeout for checking if all the blocks get into a barrier
void packet_timed_out(void)
{
	static byte count = 0;
        //notify the loop that the alarm was set of due to
        //lack of acknowledgement in time
        alarm_out = 1;
        //packet_deadline.calltime = getTime() + 1000;
        //registerTimeout(&packet_deadline);
	sprintf(message,"message = %d\n",count);
	printDebug(message);
	count++;
	return;
}


