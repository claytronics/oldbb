CC = gcc
CFLAGS = -Wall

build: LMParser

LMParser: LMParser.o
parser.o: LMParser.c LMParser.h

install: 
	cp LMParser $(BBASE)/bin/arch-$(ARCH)	

clean:
	rm -f LMParser LMParser.o
