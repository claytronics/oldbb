/** @file render.h
 *
 *  A library for initializing and rendering an OpenGL scene.
 *
 *  @author Bobby Prochnow (rprochno)
 *  @bugs No known bugs.
 */

#ifndef __RENDER_H_
#define __RENDER_H_

/* - OpenGL Libraries - */
#ifdef __APPLE__
#include <OpenGL/gl.h>
#include <OpenGL/glu.h>
#include <GLUT/glut.h>
#else
#include <GL/gl.h>
#include <GL/glu.h>
#include <GL/glut.h>
#endif

/* - Blinky Block Libaries - */
#include "block.h"

/* - Depth Clearing - */ 
#define CLEAR_DEPTH 1.0 
 
/* - Perspective Constants - */ 
#define FOV     60.0f 
#define Z_NEAR  0.5f 
#define Z_FAR   500.0f 

/* - Window Size - */
#define WINDOW_WIDTH 640
#define WINDOW_HEIGHT 480

/* - GL Picking Macros - */
#define ENCODE_NAME(index, face) ((index) << 3 | (face))
#define DECODE_INDEX(name)       ((name) >> 3)
#define DECODE_FACE(name)        ((name) & 0x7)

/* - Initialization - */
void light_init ();
void material_init ();
void perspective_init (int window_width, int window_height);

/* - World - */
void world_init ();

/* - Drawing - */
void draw_block(Block *block);
void set_display_mode(GLenum disp_mode);
void redraw_world ();
void clear_screen ();
void set_transform (float vLandRotate[], float vLandTranslate[]);

#endif /* __RENDER_H_ */
