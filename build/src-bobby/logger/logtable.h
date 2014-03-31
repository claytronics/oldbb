// logtable.h - provide logs management for host

#ifndef _LOGTABLE_H_
#define _LOGTABLE_H_

#include <map>
#include <list>
#include <stdint.h>
#include <time.h>
#include <string>

//#define USE_TIMEOUT

#ifdef USE_TIMEOUT
// Timeout: 3 seconds
#define MSG_TIMEOUT 3
#endif

class Block;
class Message;
class Fragment;
class logTable;

class Block {
public:
    uint16_t id;
    uint8_t nbRec;
    uint8_t lastMessageId;
    
    std::map<uint8_t, Message*> messages;
 
    Block(uint16_t bId);
    Block(Block const& b);
    ~Block();
    void insert(Message *m);
};

class Message {
public:
    Block *block;
    uint8_t id;
    uint8_t size; // nb of fragments
    std::string str;
    time_t first;
    std::map<uint8_t, Fragment*> fragments;
    
    

    // timeout!
    Message(Block *b, uint8_t i, uint8_t s);
    Message(Block *b, uint8_t i);
    Message(Message const& m);
    ~Message();
    void assemble();
    void print();
    void setSize(uint8_t s);
    void insert(Fragment *f);
    bool isCompleted();
};

class Fragment {
public:    
    Message *msg;
    uint8_t id;
    std::string str;

    Fragment(Message *m, uint8_t i, std::string s);
    Fragment(Fragment const& f);
    ~Fragment();
    void print();
};

class LogTable {
public:
    std::map<uint16_t, Block*> logs;

    LogTable();
    ~LogTable();
    void insert(uint16_t bId, uint8_t i, uint8_t f, uint8_t s, std::string str);
    void printAll();
    void printCompleted();
    void removeCompleted();
    void printStats();
};

#endif

