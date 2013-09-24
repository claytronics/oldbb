# 1 "/seth/claycvs/svn/blinkyblocks/newcode/src/runtime/ensemble.bb"
#include "bb.h"
#include "ensemble.h"
#include <stdint.h>

int16_t read_fcn_top () { return blockIdAtFace(this(), Top); }
void write_fcn_top (int16_t junk) { return; }

int16_t read_fcn_bottom() { return blockIdAtFace(this(), Down); }
void write_fcn_bottom (int16_t junk) { return; }

int16_t read_fcn_front () { return blockIdAtFace(this(), North); }
void write_fcn_front (int16_t junk) { return; }

int16_t read_fcn_back ()  { return blockIdAtFace(this(), South); }
void write_fcn_back (int16_t junk) { return; }

int16_t read_fcn_left () { return blockIdAtFace(this(), West); }
void write_fcn_left (int16_t junk) { return; }

int16_t read_fcn_right () { return blockIdAtFace(this(), East); }
void write_fcn_right (int16_t junk) { return; }

int16_t read_fcn_neighbors () { return getNeighborCount(); }
void write_fcn_neighbors (int16_t junk) { return; }

int16_t read_fcn_id() { return this()->id; }
void write_fcn_id(int16_t junk) { return; }
