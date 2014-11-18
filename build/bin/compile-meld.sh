#!/bin/sh

usage(){
    echo "Usage: compile-meld.sh meldfile (without extension)"
    echo "Example: compile-meld.sh LM-programs/ends2"
    echo "Make sure to run . ./initbb.sh script beforehand"
    exit 1
}

[[ $# -eq 0 ]] && usage

case "$1" in
   /*)
     FILE=$1;;
   *)
     FILE=$PWD/$1;;
esac
case "$1" in
   *meld)
      FILE=$FILE;;
   *)
   echo "not"
      FILE=$FILE.meld;;
esac

if [ ! -f $FILE ]; then
   echo "File $FILE does not exist."
   exit 1
fi
OUTPUT=$PWD/$1

echo "Running make a first time"
make -C $BBASE/src-bobby
if [ $? != 0 ]; then
   echo "Failed to compile tools"
   exit 1
fi

compile_file=`mktemp -t compileXXXX`
sbcl --eval "(load \"$BBASE/meld-compiler/setup\")" \
     --eval "(ql:quickload \"cl-meld\")" \
     --eval "(cl-meld:meld-compile-exit \"$FILE\" \"$OUTPUT\")" \
     --no-userinit --non-interactive --noinform --noprint \
     --no-sysinit 2>&1 | tee $compile_file
if [ $? != 0 ]; then
   rm -f $compile_file
   echo "Failed to compile file $FILE (SBCL returned an error)"
   exit 1
fi
if [ ! -f $OUTPUT.m ]; then
   rm -f $compile_file
   echo "Failed to compile file $FILE.meld"
   exit 1
fi
if grep -q "WARNING" $compile_file; then
   echo "Compiler returned warnings!"
   read -p "Continue? y/n " yn
   if [ "$yn" != "y" ]; then
      rm -f $compile_file
      exit 1
   fi
fi
rm -f $compile_file
echo "Compilation done"

echo "Generating .bb file"
$BBASE/src-bobby/meldinterp-runtime/LMParser $OUTPUT.m
if [ $? != 0 ]; then
   echo "Failed to parse byte-code file $OUTPUT.m"
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
mv $OUTPUT.bb $dir/blinkyblocks.bb || exit 1

echo "Compiling the VM with the LM program"
make -C $BBASE/apps/sample-meld
if [ $? != 0 ]; then
   echo "Failed to compile the VM. Maybe you need to install OpenGL?"
   exit 1
fi

if [ "$BB" != "block" ]; then
   echo "==========> Run $BBASE/apps/sample-meld/arch-$ARCH/blinkyblocks -c $BBASE/apps/configs/line.txt"
fi
echo "Done."
