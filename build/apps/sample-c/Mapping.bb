
#include "handler.bbh"
#include "block.bbh"
#include "led.bbh"
#include "ensemble.bbh"
#include "clock.bbh"

#include "block_config.bbh"
#include "memory.bbh"

#ifdef LOG_DEBUG
#include "log.bbh"
#endif

#define DIFFUSE_COORDINATE 3

threadvar int16_t px;
threadvar int16_t py;
threadvar int16_t pz;
threadvar int16_t distancet;

byte
CoordinateHandler(void)
{

	if(thisChunk == NULL)
	{
		return 0;
	}

	if(thisChunk->data[0] == DIFFUSE_COORDINATE)
	{

		px = (int16_t)(thisChunk->data[2]) & 0xFF;
		px |= ((int16_t)(thisChunk->data[1]) << 8) & 0xFF00;

		py = (int16_t)(thisChunk->data[4]) & 0xFF;
		py |= ((int16_t)(thisChunk->data[3]) << 8) & 0xFF00;

		pz = (int16_t)(thisChunk->data[6]) & 0xFF;
		pz |= ((int16_t)(thisChunk->data[5]) << 8) & 0xFF00;

		distancet = (int16_t)(thisChunk->data[8]) & 0xFF;
		distancet |= ((int16_t)(thisChunk->data[7]) << 8) & 0xFF00;

		printf("id:%d  x=%d y=%d z=%d dd=%d\n",getGUID(),px,py,pz,distancet);

	}

	return 1;
}


void
DiffusionCoordinate(PRef except, int16_t xx, int16_t yy, int16_t zz, int16_t dd)
{

	byte msg[17];
	msg[0] = DIFFUSE_COORDINATE;

	int16_t bx = xx;
	int16_t by = yy;
	int16_t bz = zz;

	int16_t bd = dd+1;

	Chunk* cChunk = getSystemTXChunk();
	
	for (int i = 0; i <NUM_PORTS; i++)
	{

		switch(i){

			case 0:
				bz = zz+1;
			break;

			case 5:
				bz = zz-1;
			break;

		}

		msg[1] = (byte) ((bx >> 8) & 0xFF);
		msg[2] = (byte) (bx & 0xFF);
		
		msg[3] = (byte) ((by >> 8) & 0xFF);
		msg[4] = (byte) (by & 0xFF);
		
		msg[5] = (byte) ((bz >> 8) & 0xFF);
		msg[6] = (byte) (bz & 0xFF);

		msg[7] = (byte) ((dd >> 8) & 0xFF);
		msg[8] = (byte) (dd & 0xFF);

		// printf("x=%d y=%d z=%d message transmitted to %d\n",bx,by,bz,i);


		if(sendMessageToPort(cChunk, i, msg, 9, CoordinateHandler, NULL) == 0)
		{
			freeChunk(cChunk);
		}

		delayMS(1);

		// printf("x=%d y=%d z=%d message sendd to %d\n",bx,by,bz,i);	    
		// bx = xx;
		// by = yy;
		// bz = zz;
		// printf("test reinitialisation x=%d y=%d z=%d\n",bx,by,bz);
		
	
	}	
}



void 
myMain(void)
{

	delayMS(200);

	if (getGUID() == 1) //im master
	{

		px=0;
		py=0;
		pz=0;
		distancet=0;

		printf("degin : id:%d x=%d y=%d z=%d distance=%d\n",getGUID(),px,py,pz,distancet);

		delayMS(400);
	
		//1st time diffusion

		DiffusionCoordinate(6, px, py, pz, distancet);

	}


	while(1);

}

void userRegistration(void)
{
	registerHandler(SYSTEM_MAIN, (GenericHandler)&myMain);  
}