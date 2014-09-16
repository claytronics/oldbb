# this makefile will try and build everything in the subdirectories
# and copy what it should to the various directories.  

SUBDIRS := bbcompiler ldpsyntax cmdline-bootloader logger meldinterp-runtime

ifeq ($(BBASE)x,x)
 $(error "You must define BBASE, probably to the parent of this directory")
endif

ifeq ($(ARCH)x,x)
 $(error "You must define ARCH (try export ARCH=`uname -m`-`uname -s` replacing / with -"))
endif

build:	$(SUBDIRS)
	for i in $(SUBDIRS); do $(MAKE) -C $$i; done

install:	$(BBASE)/bin/arch-$(ARCH) $(SUBDIRS)
	for i in $(SUBDIRS); do $(MAKE) -C $$i install; done

$(BBASE)/bin/arch-$(ARCH):
	mkdir $(BBASE)/bin/arch-$(ARCH)

clean:
	for i in $(SUBDIRS); do $(MAKE) -C $$i clean; done

reallyclean:
	for i in $(SUBDIRS); do $(MAKE) -C $$i reallyclean; done
