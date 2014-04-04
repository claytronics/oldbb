###################################################################
#####	USE THIS SCRIPT TO PERMANENTLY SET AN ID TO A BLOCK   #####
###################################################################

#!/bin/sh
if [ "$#" -eq 1 ];
  then
  sed -i "s/ARG_ID/$1/g" setID.bb
  export BB=block && make wipeout && make
  sudo chmod 777 /dev/ttyUSB0
  reprogrammer -p /dev/ttyUSB0 -f arch-blocks/setID.hex
  sed -i "s/$1/ARG_ID/g" setID.bb
  echo "Don't forget to set SOURCE in Makefile and uncomment LOG_DEBUG"
  echo "done"
else
  echo "Argument missing: ID"
  exit 0
 fi