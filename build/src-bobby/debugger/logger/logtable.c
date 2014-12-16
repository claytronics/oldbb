// logtable.c - provide logs management for host

#include <iostream>
#include "logtable.h"
#include <sstream>


using namespace std;

/********************************************************
 *    Block class
 ********************************************************/

Block::Block(uint16_t bId) {
    id = bId;
    lastMessageId = 0;
    nbRec = 0;
}

Block::Block(Block const& b) {
    id = b.id;
    messages = b.messages;
    lastMessageId = b.lastMessageId;
    nbRec = b.nbRec;
}

Block::~Block() { }

void Block::insert(Message *m) {
	lastMessageId = max((unsigned int)m->id+1, (unsigned int)lastMessageId);
    messages.insert(pair<uint16_t, Message*>(m->id, m));
}

/********************************************************
 *    Message class
 ********************************************************/

Message::Message(Block *b, uint8_t i, uint8_t s) {
    block = b;
    id = i;
    size = s;
    time(&first); 
}

Message::Message(Block *b, uint8_t i) {
    block = b;
    id = i;
    size = 1;
    time(&first); 
}

Message::Message(Message const& m) {
    block = m.block;
    id = m.id;
    size = m.size;
    first = m.first;
    fragments = m.fragments;
}

Message::~Message() {
	map<uint8_t, Fragment*>::iterator itf;
	Fragment *frag = NULL;
	
	for (itf = fragments.begin(); itf != fragments.end(); itf++) {
		frag = itf->second;
		fragments.erase (itf);		
		delete frag;
		itf = fragments.begin();		
		if( itf == fragments.end()) {
			return;
		}
	}
}

void Message::insert(Fragment *f) {
    fragments.insert(pair<uint8_t, Fragment*>(f->id, f));
}
    
void Message::setSize(uint8_t s) {
    size = s;
}

void Message::assemble() {
    str = "";
    map<uint8_t, Fragment*>::iterator it;

    for (int i = 0; i < size; i++) {
        it = fragments.find(i);
        if (it == fragments.end()) {
            str += " [missing] ";
        } else {
            str += it->second->str;
        }
    }    
}

bool Message::isCompleted() {
#ifdef USE_TIMEOUT   
   time_t now;
   time(&now);
   return ( (fragments.size() == size) || (difftime(now,first) > MSG_TIMEOUT));
#else
   return (fragments.size() == size);
#endif
}

void Message::print() {
    cout << "[" << block->id << ", msg:" << (unsigned int) id+1 <<  "] " << str << endl;
}

string  Message::print_s() {
#if 0
	string result;
	result = "";
	result ="[" + std::to_string( block->id);
	cout <<result<<endl;
	//return result + ", msg:" + ((unsigned int) (id+1) )+  "] " + str + endl;
	return result + ", msg:" +   "] " + str + "\n";
#endif
	std::stringstream ss;
	ss << "[" << block->id << ", msg:" << (unsigned int) id+1 <<  "] " << str << endl;
	string str = ss.str();
	return str;
}

/********************************************************
 *    Fragment class
 ********************************************************/

Fragment::Fragment(Message *m, uint8_t i, string s) {
    msg = m;
    id = i;
    str = s;
}

Fragment::Fragment(Fragment const& f) {
    msg = f.msg;
    id = f.id;
    str = f.str;
}

Fragment::~Fragment() { }

void Fragment::print() {
    cout << str;
}

/********************************************************
 *    LogTable class
 ********************************************************/

LogTable::LogTable() { }

LogTable::~LogTable(){ }

void LogTable::insert(uint16_t bId, uint8_t i, uint8_t f, uint8_t s, string str) {
	map<uint16_t, Block*>::iterator itb;
	map<uint8_t, Message*>::iterator itm;
	map<uint8_t, Fragment*>::iterator itf;
	Block *b = NULL;
	Message *msg = NULL;
	Fragment *frag = NULL;
	
	itb = logs.find(bId);
	if (itb == logs.end()) {
		b = new Block(bId);
		logs.insert(std::pair<uint16_t,Block*>(bId,b));
	} else {
		b = itb->second;
	}
	
	itm = b->messages.find(i);
	if (itm == b->messages.end()) {
		msg = new Message(b, i, s);
		b->insert(msg);
	} else {
		msg = itm->second;
	}
	
	itf = msg->fragments.find(f);
	if (itf == msg->fragments.end()) {
		frag = new Fragment(msg, f, str);
		msg->insert(frag);
	} else { // duplicate fragment
		frag = itf->second;
	}	
	//Message(Block *b, uint8_t i, uint8_t s);
}

void LogTable::printAll() {
	map<uint16_t, Block*>::iterator itb;
	map<uint8_t, Message*>::iterator itm;
	Block *b = NULL;
	Message *msg = NULL;
	
	for (itb = logs.begin(); itb != logs.end(); itb++) {
		b = itb->second;
		for (itm = b->messages.begin(); itm != b->messages.end(); itm++) {
			msg = itm->second;
			msg->assemble();
			msg->print();
		}
	}
}

void LogTable::printCompleted() {
	map<uint16_t, Block*>::iterator itb;
	map<uint8_t, Message*>::iterator itm;
	Block *b = NULL;
	Message *msg = NULL;
	
	for (itb = logs.begin(); itb != logs.end(); itb++) {
		b = itb->second;
		for (itm = b->messages.begin(); itm != b->messages.end(); itm++) {
			msg = itm->second;
			if (msg->isCompleted()) {
				msg->assemble();
				msg->print();
				b->nbRec++;
			}
		}
	}
}

void LogTable::removeCompleted() {
	map<uint16_t, Block*>::iterator itb;
	map<uint8_t, Message*>::iterator itm;
	Block *b = NULL;
	Message *msg = NULL;
	
	for (itb = logs.begin(); itb != logs.end(); itb++) {
		b = itb->second;
		for (itm = b->messages.begin(); itm != b->messages.end(); itm++) {
			msg = itm->second;
			if (msg->isCompleted()) {
				b->messages.erase (itm);
				delete msg;
				itm = b->messages.begin();
				if (itm  == b->messages.end()) {
					return;
				}
			}
		}
	}
}

void LogTable::printStats() {
	map<uint16_t, Block*>::iterator itb;
	map<uint8_t, Message*>::iterator itm;
	Block *b = NULL;
	unsigned int n = 0;
	unsigned int last = 0;
	
	cout << "-------------------------------------" << endl;
	cout << "Stats" << endl;
	for (itb = logs.begin(); itb != logs.end(); itb++) {
		b = itb->second;
		cout << b->id << ": " << (unsigned int) b->nbRec << "/" << (unsigned int) b->lastMessageId << endl;
		n += b->nbRec;
		last += b->lastMessageId;
	}
	cout << "-------------------------------------" << endl;
	cout << "Total: " << n << "/" << last << endl;
}

//this method is to all all the logs to a global string inorder to 
//generage
void LogTable::stringifyCompleted() {
	//cout<<__FUNCTION__<<__LINE__<<"\n";
	map<uint16_t, Block*>::iterator itb;
	map<uint8_t, Message*>::iterator itm;
	Block *b = NULL;
	Message *msg = NULL;
	//emptying previous log messages;
 	//log_message = "";	
	for (itb = logs.begin(); itb != logs.end(); itb++) {
		b = itb->second;
		for (itm = b->messages.begin(); itm != b->messages.end(); itm++) {
			msg = itm->second;
			if (msg->isCompleted()) {
				msg->assemble();
				//msg->print();
				//appending all the logs to this string that can be sent as the response
				log_message= log_message + msg->print_s();
				//cout<<msg->str<<endl;
				msg->print();
				//cout <<"log_message :"<< log_message<<endl;
				b->nbRec++;
			}
		}
	}
}

