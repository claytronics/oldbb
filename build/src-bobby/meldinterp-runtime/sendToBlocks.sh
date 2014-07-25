#!/bin/bash

./parser samples/$1.m
cp $1.bb ../../apps/sample-meld/arch-blocks/meldinterp-runtime/ends.bb;
