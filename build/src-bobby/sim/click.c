/* - OpenGL Libraries - */
#include <GL/gl.h>
#include <GL/glu.h>
#include <GL/glut.h>

#include "click.h"
#include "render.h"

#define BUFFER_SIZE 512

Block* processHits (GLint hits, GLuint buffer[], int *face)
{
	GLuint depth;
	GLint choose, bIndex;
	int index;
	Block *block;

	if (hits == 0)
		return NULL;

	depth  = (GLuint)buffer[1];
	choose = (GLint)buffer[3];

	for (index = 1; index < hits; index++)
	{
		if (buffer[index*4+1] < depth)
		{
			choose = (GLint)buffer[index*4+3];
			depth  = (GLuint)buffer[index*4+1];
		}
	}
	
	bIndex = DECODE_INDEX(choose);
	if (face != NULL)
		*face = DECODE_FACE(choose);
	
	// TODO: we can do this by embedding the pointer in the name
	// instead of naively searching through all blocks
	Q_FOREACH(block, getBlockList(), blockLink)
	{
		if (block->id == bIndex)
			return block;
	}
	Q_ENDFOREACH(getBlockList());

	return NULL;
}

Block *click(int x, int y, int *face)
{
	GLuint  buffer[BUFFER_SIZE];
	GLint	viewport[4];
	GLint  hits;

	glGetIntegerv(GL_VIEWPORT, viewport);
	glSelectBuffer(BUFFER_SIZE, buffer);

	glRenderMode(GL_SELECT);

	glMatrixMode(GL_PROJECTION);
	glPushMatrix();
	glLoadIdentity();

	gluPickMatrix((GLdouble) x, (GLdouble) (viewport[3]-y), 1.0f, 1.0f, viewport);

	gluPerspective(FOV, (GLfloat) (WINDOW_WIDTH/WINDOW_HEIGHT), Z_NEAR, Z_FAR);
	glMatrixMode(GL_MODELVIEW);
	redraw_world();
	glMatrixMode(GL_PROJECTION);
	glPopMatrix();

	glMatrixMode(GL_MODELVIEW);
	hits=glRenderMode(GL_RENDER);

	return processHits(hits, buffer, face);
}
