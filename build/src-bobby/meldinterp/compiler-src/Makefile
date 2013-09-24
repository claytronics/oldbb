ifeq ($(BBASE)x,x)
 $(error "You must define BBASE")
endif

ifeq ($(ARCH)x,x)
 $(error "You must define ARCH (try export ARCH=`uname -m`-`uname -s` replacing / with -"))
endif

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

TARGET := meldCompiler$(EXE_EXT)

build:	$(TARGET)

install:	$(TARGET)
	/bin/cp -f $(TARGET) $(BBASE)/bin/arch-$(ARCH)/

$(TARGET):	src
	$(MAKE) -C src mlton
	cp src/meldCompiler ./$(TARGET)

src:	meldCompiler.tgz
	mkdir -p src
	cd src; tar -m -xf ../meldCompiler.tgz
	touch src

clean:
	/bin/rm -rf src/*

reallyclean:
	/bin/rm -rf src $(TARGET)
