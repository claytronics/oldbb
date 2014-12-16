#include "chunk.h"

void usage(void);
void readParameters(int argc, char** argv);
void sendIAmHost(void);
void insertLogChunk(Chunk *c);
void receiveLogs(void);
void stringifyLogs(void);

void sendColorCmd(int);
void sendIDToSet(uint16_t idToSet);
void sendResetCmd(void);
void send_tree_count(void);

static int kbhit(void);

int mylogger(int argc, char** argv);

void send_read_memory(uint16_t address,uint16_t num_bytes) ;
void send_read_register(uint16_t address) ;

uint8_t tree_count=0;
