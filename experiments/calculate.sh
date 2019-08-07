#!/bin/bash
# $1 is the target directory in results
if [ -z $1 ]; then #True of the length if "STRING" is zero.
  echo "No experiment name provided. Exiting."
  exit
fi

mkdir -p results
RESULTS=./results

cd ${RESULTS}

mkdir -p $1/zeroed
rm -rf $1/zeroed/*

# TODO: Make this not rely on there being one machine
# Only copy in the raw data if it wasn't there
echo "Copying logs into rawData"
mkdir -p $1/rawData
rm -rf $1/rawData/*

clients=(`cat ../clients`)
noofelements=${#clients[*]}
counter=0
while [ $counter -lt $noofelements ]
do
  scp -r ../exp_log/${clients[$counter]} $1/rawData
  counter=$(( $counter + 1 ))
done

#for machine in 128.83.143.77 128.83.143.79 128.83.143.88 128.83.143.94; do #TODO: automate
#  scp -r ../exp_log/$machine $1/rawData
#done

execs=(`cat ../execs`)
#TODO why copying these?
cp ../test.properties $1/
cp ../test.properties.backend $1/
cp ../modified_super.py $1/
cp ../exp_log/${execs[0]}/middle-exec_0_out $1/ #TODO automate
cp ../exp_log/${execs[0]}/backend-exec_0_out $1/ #TODO automate
../process_timings.py $1/middle-exec_0_out > $1/timings.out


for machine in `ls $1/rawData`
do
  startline=`grep startTime $1/rawData/$machine/machine.txt`
  startTime=`echo $startline|cut -f2 -d' '`
  echo $machine.startTime=$startTime
  endline=`grep endTime $1/rawData/$machine/machine.txt | tail -n 1`
  endTime=`echo $endline|cut -f2 -d' '`
  echo $machine.endTime=$endTime
  for file in `ls $1/rawData/$machine/client_*/*.txt`
  do
    #echo processing $file

    let "index=$index+1"
		java -cp ../lib/zookeeper-dev.jar:../lib/log4j-1.2.15.jar:../lib/test.jar org.apache.zookeeper.test.ZeroTime $file $startTime $endTime >>$1/zeroed/big.txt
  done
done

../final_process.sh $1
