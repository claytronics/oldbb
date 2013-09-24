#!/bin/sh 

echo "Will turn all links into files under $*"
find $* -type l -exec ln2cp.sh "{}" \;

