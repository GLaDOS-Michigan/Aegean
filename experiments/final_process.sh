#!/bin/bash

start_ops=200
end_ops=800
TOTAL_OPS=1000
INTERVAL=1000
INTERVAL_COUNT=5
MAX_DIFFERENCE=.3

#cd results # to run from IDE
echo Final processing $1

# $1 -> experiment name
java -cp ../lib/zookeeper-dev.jar:../lib/test.jar org.apache.zookeeper.test.ProcessZero $1/zeroed/big.txt $start_ops $end_ops $TOTAL_OPS 250 10 $MAX_DIFFERENCE $1 >> $1/result_all.txt
java -cp ../lib/zookeeper-dev.jar:../lib/test.jar org.apache.zookeeper.test.ProcessZero $1/zeroed/big.txt $start_ops $end_ops $TOTAL_OPS 400 4 $MAX_DIFFERENCE $1 >> $1/result_all.txt
java -cp ../lib/zookeeper-dev.jar:../lib/test.jar org.apache.zookeeper.test.ProcessZero $1/zeroed/big.txt $start_ops $end_ops $TOTAL_OPS 1000 10 $MAX_DIFFERENCE $1 >> $1/result_all.txt

RESULT=`cat $1/result_all.txt | grep "Cannot find a result"`
if [ -z "$RESULT" ]; then #True of the length if "STRING" is zero.
  cat $1/result_all.txt
  exit
fi
echo "failed first..."

INTERVAL_COUNT=3
java -cp ../lib/zookeeper-dev.jar:../lib/test.jar org.apache.zookeeper.test.ProcessZero $1/zeroed/big.txt $start_ops $end_ops $TOTAL_OPS $INTERVAL $INTERVAL_COUNT $MAX_DIFFERENCE $1 > $1/result_all.txt
RESULT=`cat $1/result_all.txt | grep "Cannot find a result"`
if [ -z "$RESULT" ]; then
  cat $1/result_all.txt
  exit
fi
echo "failed second..."

INTERVAL=1250
java -cp ../lib/zookeeper-dev.jar:../lib/test.jar org.apache.zookeeper.test.ProcessZero $1/zeroed/big.txt $start_ops $end_ops $TOTAL_OPS $INTERVAL $INTERVAL_COUNT $MAX_DIFFERENCE $1 > $1/result_all.txt
RESULT=`cat $1/result_all.txt | grep "Cannot find a result"`
if [ -z "$RESULT" ]; then
  cat $1/result_all.txt
  exit
fi
echo "failed third..."

INTERVAL=750
INTERVAL_COUNT=2
MAX_DIFFERENCE=.3
java -cp ../lib/zookeeper-dev.jar:../lib/test.jar org.apache.zookeeper.test.ProcessZero $1/zeroed/big.txt $start_ops $end_ops $TOTAL_OPS $INTERVAL $INTERVAL_COUNT $MAX_DIFFERENCE $1 > $1/result_all.txt
RESULT=`cat $1/result_all.txt | grep "Cannot find a result"`
if [ -z "$RESULT" ]; then
  cat $1/result_all.txt
  exit
fi
echo "failed fourth..."

INTERVAL=250
INTERVAL_COUNT=2
MAX_DIFFERENCE=.3
java -cp ../lib/zookeeper-dev.jar:../lib/test.jar org.apache.zookeeper.test.ProcessZero $1/zeroed/big.txt $start_ops $end_ops $TOTAL_OPS $INTERVAL $INTERVAL_COUNT $MAX_DIFFERENCE $1 > $1/result_all.txt
cat $1/result_all.txt
