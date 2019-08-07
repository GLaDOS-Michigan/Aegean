#!/bin/bash

ZK_DIRECTORY=/users/manos/adam_test
ZK_DATA_DIRECTORY=$ZK_DIRECTORY/data
ZK_LOG_DIRECTORY=$ZK_DIRECTORY/log
ZK_RESULT_DIRECTORY=$ZK_DIRECTORY/result/
SCP_RESULT_DIRECTORY=128.83.122.188:~/result
OLIPHAUNT_DIRECTORY="~/testmanos/"
OLIPHAUNT_LOG="~/testlog/"
SERVER_PORT=2181
SERVER_FILE=./execs
CLIENT_FILE=./clients
ORDER_FILE=./orders
FILTER_FILE=./filters
VERIFIER_FILE=./verifiers
MAPPINGS_FILE=./mappings

CLIENT_NUM='10'

TOTAL_OPS=400

TEST_OPS=300

TEST_READ=10

TEST_WRITE=1

INTERVAL=1000

INTERVAL_COUNT=2

MAX_DIFFERENCE=.3

counter=0
while read line
do
	SERVER_NAME[$counter]=$line
	counter=$counter+1
done < $SERVER_FILE

counter=0
while read line
do
        CLIENT_NAME[$counter]=$line
        counter=$counter+1
done < $CLIENT_FILE

counter=0
while read line
do
        ORDER_NAME[$counter]=$line
        counter=$counter+1
done < $ORDER_FILE

counter=0
while read line
do
        FILTER_NAME[$counter]=$line
        counter=$counter+1
done < $FILTER_FILE

counter=0
while read line
do
        VERIFIER_NAME[$counter]=$line
        counter=$counter+1
done < $VERIFIER_FILE

