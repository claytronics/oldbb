CC=gcc

ifeq ($(BBASE)x,x)
 $(error "You must define BBASE")
endif

ifeq ($(ARCH)x,x)
 X := $(shell uname -m)
 Y := $(shell uname -s)
 Y := $(subst /,-,$Y)
 ARCH := $X-$Y
 $(info Setting ARCH to $(ARCH))
endif

# file extension defines
ifeq ($(ARCH),i686-Cygwin)
EXE_EXT = .exe
else
EXE_EXT = 
endif

TARGET := ldpparser$(EXE_EXT)

top:	$(TARGET)

ldpparser$(EXE_EXT):	ldp.tab.o scanner.o table.o
	$(CC) -o $@ $^  -lfl

%.o:	%.c
	$(CC) -ggdb -o $@ -c $<

scanner.c:	scanner.flex
	flex -o $@ $^

ldp.tab.c:	ldp.y ldp.h
	bison -d $<

table.o:	table.c ldp.h

install:	$(TARGET)
	cp $(TARGET) $(BBASE)/bin/arch-$(ARCH)
	

.PHONY: clean
clean:
	/bin/rm -f *.o scanner.c ldp.tab.c ldp.tab.h scanner.h

.PHONY: reallyclean
reallyclean:
	/bin/rm -f *.o scanner.c ldp.tab.c ldp.tab.h scanner.h $(TARGET)


