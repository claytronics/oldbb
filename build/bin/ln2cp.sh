#!/bin/sh

name=${1##*/}
path=${1%$name}
if [ "$path" == "" ]; then path="./"; fi
cd $path
if [ -h $name ]; then
  s=`ls -l $name | sed -s 's/.*-> //'`
  echo "Copy $s to $name in $path"
  mv $name .$name.ln
  /bin/cp $s .
else
  echo $1 is already a regular file
fi

