CC = g++
CFLAGS = -O2 -Wall -I .

# This flag includes the Pthreads library on a Linux box.
# Others systems will probably require something different.
LIB = -lpthread

all: tiny cgi

tiny: csapp.o tiny.c hostserial.o circ_buffer.o chunk.o logtable.o mylogger.o
	g++ $(CFLAGS) -o tiny tiny.c csapp.o  hostserial.o circ_buffer.o chunk.o logtable.o mylogger.o $(LIB)

hostserial.o:
	g++ $(CFLAGS) -Ilogger -c logger/hostserial.c

circ_buffer.o:
	g++ $(CFLAGS) -Ilogger -c logger/circ_buffer.c

chunk.o:
	g++ $(CFLAGS) -Ilogger -c logger/chunk.c

logtable.o:
	g++ $(CFLAGS) -Ilogger -c logger/logtable.c

csapp.o:
	g++ $(CFLAGS) -c csapp.c

mylogger.o:
	g++ $(CFLAGS) -Ilogger -c logger/mylogger.c

#tiny.o:
#	gcc $(CFLAGS) -I. -c tiny.c csapp.o 

cgi:
	(cd cgi-bin; make)

# Generate the files used to test max cached file size of 100Kib
cachefiles:
	dd if=/dev/urandom of=1kib.dat bs=1 count=1024           # OK 
	dd if=/dev/urandom of=100kibminus.dat bs=1 count=102399  # OK (one byte smaller than legal size)
	dd if=/dev/urandom of=100kibexact.dat bs=1 count=102400  # OK (exact legal size)
	dd if=/dev/urandom of=100kibplus.dat bs=1 count=102401   # not OK (one byte larger than legal size)
	dd if=/dev/urandom of=200kib.dat bs=1 count=204800       # not OK (twice the legal size)

clean:
	rm -f *.o tiny *~
	(cd cgi-bin; make clean)

