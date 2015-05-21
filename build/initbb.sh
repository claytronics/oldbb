#!/bin/bash -fe
#
# this file sets up important environment variables
#

export BBASE=$(pwd)
export ARCH=$(uname -m)-$(uname -s)
if [ ! -e $BBASE/bin/arch-$ARCH ]; then
  echo "No predefined files for $ARCH.";
  echo "You must mkdir $BBASE/bin/arch-$ARCH; cd src-bobby; make build; make install";
  return -1;
fi
export PATH=$PATH:$BBASE/bin/arch-$ARCH:$BBASE/bin
echo "Remember to add a path to the WINAVR directories"
echo "Ready to blink!"

