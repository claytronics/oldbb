#!/bin/sh

usage(){
    echo "Usage: compile-meld.sh meldfile (without extension)"
    echo "Example: compile-meld.sh LM-programs/ends2"
    echo "Make sure to run . ./initbb.sh script beforehand"
    exit 1
}

[[ $# -eq 0 ]] && usage

echo "Running make a first time"
make -C $BBASE/src-bobby
if [ $? != 0 ]; then
   echo "Failed to compile tools"
   exit 1
fi

sbcl --eval "(load \"$BBASE/meld-compiler/setup\")" \
     --eval "(ql:quickload \"cl-meld\")" \
     --eval "(cl-meld:meld-compile \"$PWD/$1.meld\" \"$PWD/$1.meld\")" \
     --eval "(quit)"
if [ $? != 0 ]; then
   echo "Failed to compile file $1.meld"
   exit 1
fi
echo "Compilation done"

echo "Generating .bb file"
$BBASE/src-bobby/meldinterp-runtime/LMParser $PWD/$1.m
if [ $? != 0 ]; then
   echo "Failed to parse byte-code file $1.m"
   exit 1
fi

if [ "$BB" == "block" ]; then
  echo "Moving .bb file to arch-blocks"
  dir=$BBASE/apps/sample-meld/arch-blocks/meldinterp-runtime
else
  echo "Moving .bb file to arch-$ARCH"
  dir=$BBASE/apps/sample-meld/arch-$ARCH/meldinterp-runtime
fi
mkdir -p $dir
mv $1.bb $dir/blinkyblocks.bb || exit 1

echo "Compiling the VM with the LM program"
make -C $BBASE/apps/sample-meld

echo "Run $BBASE/apps/sample-meld/arch-$ARCH/blinkyblocks -c $BBASE/apps/configs/line.txt"
echo "Done."
