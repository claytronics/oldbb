#!/bin/bash

if [ "$ARCH" == "" ]; then
 echo "ARCH not defined!"
else
 ./parser $1.m
 cp $1.bb ../../apps/sample-meld/arch-$ARCH/meldinterp-runtime/ends.bb;
fi
