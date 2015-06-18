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

threadvar int px;
threadvar int py;
threadvar int pz;
threadvar int distancet;
threadvar bool Dsend[6];

byte
CoordinateHandler(void){

	if(thisChunk == NULL){
		freeChunk(thisChunk);
	}

	printf("ID Message is : %d",thisChunk->data[0]);

}

void
DiffusionCoordinate(PRef except, int xx, int yy, int zz, int dd)
{
	byte msg[17];
	msg[0] = DIFFUSE_COORDINATE;

	msg[1] = (byte) ((xx >> 8) & 0xFF);
	msg[2] = (byte) (xx & 0xFF);

	msg[3] = (byte) ((yy >> 8) & 0xFF);
	msg[4] = (byte) (yy & 0xFF);

	msg[5] = (byte) ((zz >> 8) & 0xFF);
	msg[6] = (byte) (zz & 0xFF);

	msg[7] = (byte) ((dd >> 8) & 0xFF);
	msg[8] = (byte) (dd & 0xFF);

	Chunk* cChunk = getSystemTXChunk();
	
	for (int i = 0; i <NUM_PORTS; i++)
	{
		if(Dsend[i] == 1)
		{
			if(sendMessageToPort(cChunk, i, msg, 9, CoordinateHandler, NULL) == 0)
			{
				freeChunk(cChunk);
			}
		}	    
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

		printf("id:%d  x=%d y=%d z=%d distance=%d\n",getGUID(),px,py,pz,distancet);
	
		//1st time diffusion

		for (int j=0; j<6;j++)
		{

			if(thisNeighborhood.n[j] != VACANT)
	   		{
				Dsend[j] = 1;
			}
			else
			{
				Dsend[j] = 0;
			}

			delayMS(getGUID());

		}

		delayMS(00);

		DiffusionCoordinate(6, px, py, pz, distancet+1);

	}

	
while(1);

}

void userRegistration(void)
{
    registerHandler(SYSTEM_MAIN, (GenericHandler)&myMain);  
}