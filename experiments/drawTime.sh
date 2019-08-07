#!/bin/bash

# $1 is the directory
source configuration.sh
mkdir -p $1/zeroed
rm $1/zeroed/*
set -x
for machine in `ls $1/rawData`
do
	startline=`grep startTime $1/rawData/$machine/machine.txt`
	startTime=`echo $startline|cut -f2 -d' '`
	echo $machine.startTime=$startTime
	endline=`grep endTime $1/rawData/$machine/machine.txt`
        endTime=`echo $endline|cut -f2 -d' '`
        echo $machine."  "endTime=$endTime
	for file in `ls $1/rawData/$machine/client*/*.txt`
	do
		echo processing $file
		let "index=$index+1"
		java -cp lib/zookeeper-dev.jar:lib/log4j-1.2.15.jar:lib/test.jar org.apache.zookeeper.test.ZeroTime $file $startTime $endTime >>$1/zeroed/big.txt
    sed -i '/-/d' $1/zeroed/big.txt
	done
done

let "start_ops=($TOTAL_OPS-$TEST_OPS)/2"
let "end_ops=$start_ops+$TEST_OPS"
echo final processing $1
echo Printing out Vals
echo $start_ops
echo $end_ops
echo $TOTAL_OPS
echo $INTERVAL_COUNT
echo $MAX_DIFFERENCE
java -cp lib/test.jar org.apache.zookeeper.test.ProcessZeroTimeThr $1/zeroed/big.txt $start_ops $end_ops $TOTAL_OPS 1000 $INTERVAL_COUNT $MAX_DIFFERENCE  >> timeThr.txt
echo finish $1 >> timeThr.txt
