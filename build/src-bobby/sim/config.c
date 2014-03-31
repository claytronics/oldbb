#include "../system/myassert.h"
#include "block.h"
#include "config.h"
#include "sim.h"
#include <time.h>
#include <stdlib.h>
#include <string.h>

void randomConfig(int maxcount)
{
	bool grid[11][11][11];
	Block* stack[11*11*11];
	int stack_idx = 0;
	Block *b, *curr;
	int x, y, z, i;
	int count = 1;
	int prob = 30;

	memset(grid, 0, sizeof(grid));

	if (maxcount == 0) maxcount = 25;
    b = createBlock(0, 0, 0);
    grid[5][5][5] = true;

	srand( time(NULL) );

    stack[stack_idx++] = b;

    while (stack_idx > 0 && count < maxcount)
    {
    	stack_idx--;
    	curr = stack[stack_idx];
    	for (i = 0; i < 6; i++)
    	{
    		x = curr->x;
    		y = curr->y;
    		z = curr->z;
    		if (rand() % 100 > prob)
    		{
    			switch (i)
    			{
    			case North:
    				z--;
    				break;
    			case East:
    				x++;
    				break;
    			case South:
    				z++;
    				break;
    			case West:
    				x--;
    				break;
    			case Top:
    				y++;
    				break;
    			case Down:
    				y--;
    				break;
    			default:
    				break;
    			}
    			if (x < -5 || y < -5 || z < -5 ||
    			    x > 5 || y > 5 || z > 5)
    				continue;
    			if (grid[5+x][5+y][5+z])
    				continue;

    		    grid[5+x][5+y][5+z] = true;
    		    b = createBlock(x, y, z);

    		    count++;
    	    	stack[stack_idx] = b;
    		    stack_idx++;
    		    if (prob > 16) prob--;
    		}
    	}
    }
}

void readConfig(char* name)
{
	FILE* f = fopen(name, "r");
	if (f == NULL)
		err("Can't open %s for reading", name, 0, 0);

	int line = 1;
	while (!feof(f))
	{
		char buffer[256];
		char* p = fgets(buffer, 256, f);
		if (p == NULL)
			break;

		int x, y, z, dir;
		int num = sscanf(buffer, "%d, %d, %d, %d", &x, &y, &z, &dir);
		assert(num == 4);

		createBlock(x, y, z);

		line++;
	}
	fclose(f);
}
