#ifndef _WORLD_H_
#define _WORLD_H_

#include "block.h"

typedef enum { Down, North, East, West, South, Top, NumFaces } Face;

Block* seeIfNeighborAt(Block *b, Face face);
Face determineDirection(int sx, int sy, int sz, int dx, int dy, int dz);
int determineFaceTowards(Block *src, Block *dest);
void getPointInDirection(int sx, int sy, int sz, Face i, int* dx, int* dy, int* dz);
char *faceToString(Face face);

#endif
