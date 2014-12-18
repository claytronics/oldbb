#include "chunk.h"
#include <string>

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
void ask_attendence(void);

static int kbhit(void);

int mylogger(int argc, char** argv);

void send_read_memory(uint16_t address,uint16_t num_bytes) ;
void send_read_register(uint16_t address) ;

extern volatile uint8_t tree_count;
extern volatile uint8_t resp_rxed;
extern volatile uint8_t attendance_rxed;
extern std::string attendance;
