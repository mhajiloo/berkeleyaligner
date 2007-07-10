#!/bin/sh

if [ $# -lt 1 ] 	
	then
  		echo "Usage: $0 <configuration file> [aligner options...]"
  		exit 1
fi

OPTS="-server -mx200m -ea"
CONF=$1
shift

java $OPTS -jar berkeleyaligner.jar ++$CONF $@ 