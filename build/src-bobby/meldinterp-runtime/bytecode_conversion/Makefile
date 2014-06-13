OS = $(shell uname -s)

INCLUDE_DIRS = -I.
LIBRARY_DIRS =

ARCH = -march=x86-64
FLAGS =
LIBS = 
OPTIMIZATIONS = -O3

WARNINGS = -Wall -Wextra #-Werror
C0X = -std=c++0x

CFLAGS = $(ARCH) $(OPTIMIZATIONS) $(WARNINGS) $(DEBUG) $(INCLUDE_DIRS) $(FLAGS) $(C0X)
LIBRARIES = $(LIBS)
CXX = g++

CXXFLAGS = $(CFLAGS)
LDFLAGS = $(LIBRARY_DIRS) $(LIBRARIES)
COMPILE = $(CXX) $(CXXFLAGS) $(OBJS)

SRCS =  parser.cpp \
	instr.cpp

OBJS = $(patsubst %.cpp,%.o,$(SRCS))

convertBytecode: $(OBJS) main.o
	         $(COMPILE) main.o -o convertBytecode $(LDFLAGS)

clean:
	find . -name '*.o' | xargs rm -f
	rm -f convertBytecode
