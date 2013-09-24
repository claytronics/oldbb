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

top:

install:
	cp compile_bb.pl $(BBASE)/bin
	chmod +x $(BBASE)/bin/compile_bb.pl

clean:

reallyclean:
