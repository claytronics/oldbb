/** @file viewer.h
 *  
 *  The blinky block simulation visualizer.
 *  
 *  @author Bobby Prochnow (rprochno)
 *  @bugs No known bugs.
 */

#ifndef __VIEWER_H_
#define __VIEWER_H_

#include "block.h"

int viewer_init (int argc, char ** argv);
void event_loop ();

#endif /* __VIEWER_H_ */
