#!/bin/sh

usage(){
    echo "Usage: compile-meld.sh meldfile (without extension)"
    echo "Exemple: compile-meld.sh LM-programs/ends2"
    echo "Make sure to run . ./initbb.sh script beforehand"
    exit 1
}

[[ $# -eq 0 ]] && usage

(cd ../../../../cl-meld/
echo "(load \"load\")
(cl-meld:meld-compile \"$BBASE/apps/sample-meld/$1.meld\" \"$BBASE/apps/sample-meld/$1\")" | sbcl)
echo "Compilation done"

echo "Generating .bb file"
LMParser $1.m

if [ "$BB" == "block" ]; then
  echo "Moving .bb file to arch-blocks"
  mv $1.bb arch-blocks/meldinterp-runtime/ends.bb
else
  echo "Moving .bb file to arch-$ARCH"
  mv $1.bb arch-$ARCH/meldinterp-runtime/ends.bb
fi

echo "Compiling the VM with the LM program"
make

echo "Done."
