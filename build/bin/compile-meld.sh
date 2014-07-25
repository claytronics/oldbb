#!/bin/sh

(cd cl-meld
echo "(load \"load\")
(cl-meld:meld-compile \"../$1.meld\" \"../$1\")" | sbcl)
echo "Compilation done - Copying file to parsing directory"
cp $1.m modifiedVM/oldbb/build/src-bobby/meldinterp-runtime/samples
echo "Done."
