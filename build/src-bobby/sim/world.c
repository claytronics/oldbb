#include "block.h"
#include "world.h"

Block* seeIfNeighborAt(Block *b, Face face)
{
    int x, y, z;

	// get point we are looking for
	getPointInDirection(b->x, b->y, b->z, face, &x, &y, &z);


	// naive search
	Block *block;
	Q_FOREACH(block, getBlockList(), blockLink)
	{
	  if ((block->x == x) &&
	      (block->y == y) &&
	      (block->z == z))
	    {
	      Q_ENDFOREACH(getBlockList());
	      return block;
	    }
	}
	Q_ENDFOREACH(getBlockList());
	// not found
	return 0;
}


Face determineDirection(int sx, int sy, int sz, int dx, int dy, int dz)
{
	if ((dz - sz) != 0)
	{
		if (dz > sz)
			return North;
		return South;
	}
	if ((dx - sx) != 0)
	{
		if (dx > sx)
			return West;
		return East;
	}
	if ((dy - sy) != 0)
	{
		if (dy > sy)
			return Top;
		return Down;
	}
	return -1;
}

int determineFaceTowards(Block *src, Block *dest)
{
	return determineDirection(src->x, src->y, src->z, dest->x, dest->y, dest->z);
}

void getPointInDirection(int sx, int sy, int sz, Face i, int* dx, int* dy, int* dz)
{
	*dx = sx;
	*dy = sy;
	*dz = sz;
	switch (i)
	{
	case Top:
		*dy = sy + 1;
		break;
	case Down:
		*dy = sy - 1;
		break;
	case North:
		*dz = sz + 1;
		break;
	case South:
		*dz = sz - 1;
		break;
	case East:
		*dx = sx - 1;
		break;
	case West:
		*dx = sx + 1;
		break;
	case NumFaces:
	default:
		break;
	}
}

char *faceToString(Face face)
{
	switch (face)
	{
	case Top:
		return "Top";
	case Down:
		return "Down";
	case North:
		return "North";
	case South:
		return "South";
	case East:
		return "East";
	case West:
		return "West";
	case NumFaces:
	default:
		return "Unknown";
	}
}
