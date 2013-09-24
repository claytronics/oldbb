OBJ := reprogrammer.o hostserial.o circ_buffer.o
CFLAGS :=  -DHOST $(INC) -g -O1 -Wall
LDFLAGS=-lpthread
CC=gcc

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


reprogrammer$(EXE_EXT):	$(OBJ)
	gcc $(CFLAGS) -o $@ $(OBJ) $(LDFLAGS)

circ_buffer.o: circ_buffer.c circ_buffer.h
hostserial.o: hostserial.c hostserial.h
reprogrammer.o:	reprogrammer.c hostserial.o circ_buffer.o

.phony:	clean reallyclean install

install:	reprogrammer$(EXE_EXT)
	/bin/cp -f $< $(BBASE)/bin/arch-$(ARCH)/
	
clean:
	/bin/rm -f $(OBJ)

reallyclean:	clean
	/bin/rm -f reprogrammer$(EXE_EXT)



