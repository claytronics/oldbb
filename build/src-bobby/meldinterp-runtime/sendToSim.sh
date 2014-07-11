#!/bin/sh

if [ "$ARCH" == "" ]; then
 echo "ARCH not defined!"
else
 cp $1 ../../apps/sample-meld/arch-$ARCH/meldinterp-runtime/ends.bb;
fi
