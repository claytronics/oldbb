#include "msghandlers.h"
#include "../sim/block.h"

MsgHandler msgHandlerTable[] = {
	(MsgHandler)0
};
int last_msg_table_entry = MSG_ILLEGAL;
char* mt2str(char m) { switch (m) {
} return "????"; }
void initThreadVars(void) {
	this()->PCConnection = 0;
	this()->blockTickRunning = 0;
	this()->currentIntensity = INTENSITY_MAX;
	this()->electing = 0;
	this()->firstCalibRec = 0;
	this()->firstCalibSend = 0;
	this()->isLeader = 0;
	this()->lastWaveId = 0;
	this()->localClockMaxReach = 0;
	this()->nbSync = 0;
	this()->offset = 0;
	this()->speedAvg = 0.0;
	this()->syncBy = NUM_PORTS;
	this()->toHost = 200;
}
