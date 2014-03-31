OBJ := hostserial.o circ_buffer.o chunk.o logtable.o
OBJ_LOGGER := logger.o $(OBJ)

CFLAGS :=  -DHOST $(INC) -g -O1 -Wall
LDFLAGS=-lpthread
CC=g++

# file extension defines
ifeq ($(ARCH),i686-Cygwin)
 LIB_EXT := .a
 OBJ_EXT := .o
 EXE_EXT := .exe
else
ifeq ($(ARCH),i686-CYGWIN_NT-6.1-WOW64)
 LIB_EXT := .a
 OBJ_EXT := .o
 EXE_EXT := .exe
else
 LIB_EXT := .a
 OBJ_EXT := .o
 EXE_EXT := 
endif
endif

bblogger$(EXE_EXT):	$(OBJ_LOGGER)
	g++ $(CFLAGS) -o $@ $(OBJ_LOGGER) $(LDFLAGS)

circ_buffer.o: circ_buffer.c circ_buffer.h
hostserial.o: hostserial.c hostserial.h
chunk.o: chunk.c chunk.h
logtable.o: logtable.c logtable.h
logger.o: logger.c hostserial.o circ_buffer.o

.phony:	clean reallyclean install

install: bblogger$(EXE_EXT)
	/bin/cp -f $< $(BBASE)/bin/arch-$(ARCH)/

clean:
	/bin/rm -f $(OBJ_LOGGER)

reallyclean:	clean
	/bin/rm -f bblogger$(EXE_EXT)



