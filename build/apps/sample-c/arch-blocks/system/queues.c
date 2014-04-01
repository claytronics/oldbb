# 1 "/home/anaz/Desktop/oldbb-ssh/build/src-bobby/system/queues.bb"
// queues.c
//
// implementation of the queues

#ifndef _QUEUES_C_
#define _QUEUES_C_

#include "queues.h"

void retrySend(void)
{
    SendChunkQueue* currSq = ((SQTimeout *)thisTimeout)->sq;

    //Try to resend
    currSq->flags |= CLEAR_TO_SEND;
}

#endif
