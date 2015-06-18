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
CoordinateHandler(void)
{
	if(thisChunk == NULL)
	{
		return 0;
	}
	if(thisChunk->data[0] == DIFFUSE_COORDINATE)
	{
	px = (int)(thisChunk->data[2]) & 0xFF;
	px |= ((int)(thisChunk->data[1]) << 8) & 0xFF00;
	py = (int)(thisChunk->data[4]) & 0xFF;
	py |= ((int)(thisChunk->data[2]) << 8) & 0xFF00;
	pz = (int)(thisChunk->data[6]) & 0xFF;
	pz |= ((int)(thisChunk->data[5]) << 8) & 0xFF00;
	distancet = (int)(thisChunk->data[8]) & 0xFF;
	distancet |= ((int)(thisChunk->data[7]) << 8) & 0xFF00;
	printf("id:%d  x=%d y=%d z=%d distance=%d\n",getGUID(),px,py,pz,distancet);
	}
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
		if (i==0)
		{
		zz--;
		}
		if (i==1)
		{
		yy++;
		}
		if (i==2)
		{
		xx++;
		}
		if (i==3)
		{
		xx--;
		}
		if (i==4)
		{
		yy--;
		}
		if (i==5)
		{
		zz++;
		}

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