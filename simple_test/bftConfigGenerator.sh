#!/bin/bash


#$1 orderCrashFailures = 0
#$2 orderLiarFailures = 0
#$3 execCrashFailures = 0
#$4 execLiarFailures = 2
#$5 verifierCrashFailures
#$6 verifierLiarFailures
#$7 clientCount = 
#$8 filtered = true
#$9 filterCrashFailures = 0
#$10 filterLiarFailures = 0

SERVER_FILE=./execs
CLIENT_FILE=./clients
ORDER_FILE=./orders
FILTER_FILE=./filters
VERIFIER_FILE=./verifiers


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

EXPECTED_ARGS=10
LESS_ARGS=7
if [ $# -ne $EXPECTED_ARGS ]
then
  if [ $# -ne $LESS_ARGS ]
  then
    echo "Usage: `basename $0` orderCrash orderLiar execCrash execLiar verifierCrash verifierLiar clientCount [filtered] [filterCrash] [filterLiar]"
    exit $E_BADARGS
  else
    filtered="false"
    filterCrash=0
    filterLiar=0
  fi
else
  filtered=$8
  filterCrash=$9
  filterLiar=${10}
fi

echo orderCrashFailures = $1
echo orderLiarFailures = $2
echo execCrashFailures = $3
echo execLiarFailures = $4
echo verifierCrashFailures = $5
echo verifierLiarFailures = $6
echo clientCount = $7
echo filtered = $filtered
echo filterCrashFailures = $filterCrash
echo filterLiarFailures = $filterLiar
echo
let "order_num=2*$1+$2+1"
let "exec_num=$3+$4+1"
if [ $9 -gt ${10} ]
then
    let "filter_num=${10}+2*$9+1"
else
    let "filter_num=$9+2*${10}+1"
fi
let "verifier_num=1"
client_num=$7
if [ "$8" == "true" ]; then
	let "total_ports=$order_num+$exec_num+$verifier_num+$filter_num+1"
else
	let "total_ports=$order_num+$exec_num+$verifier_num+1"
fi

rm keys -rf
mkdir keys

#Generate Ordered Nodes
if [ ${#ORDER_NAME[*]} -ne $order_num ]
then
	echo "Wrong order num"
fi

for ((i=0; i<$order_num; i++))
do
	line="ORDER.$i = "
	for((j=0; j<$total_ports; j++))
	do
		let "port=6000+$j"
		line=$line${ORDER_NAME[$i]}:$port" "
	done
	index=0;
	for word in `java -cp bft.jar:FlexiCoreProvider-1.6p3.signed.jar BFT.util.KeyGen --generate 1`
	do
    		if [ $index -eq 2 ]
    		then
        		pub=$word
    		fi
    		if [ $index -eq 5 ]
    		then
        		priv=$word
    		fi
    		if [ $index -eq 8 ]
    		then
        		secret=$word
    		fi
    		let "index=$index+1"
	done

	line=$line$pub
	echo $line
	echo PRIV = $priv > keys/ORDER$i.privk.properties
	echo PUB = $pub >> keys/ORDER$i.privk.properties
        echo SECRET = $secret >> keys/ORDER$i.privk.properties
done 
echo
#Generate Exec Nodes

if [ ${#SERVER_NAME[*]} -ne $exec_num ]
then
        echo "Wrong exec num"
fi
for ((i=0; i<$exec_num; i++))
do
        line="EXEC.$i = "
        for((j=0; j<$total_ports; j++))
        do
                let "port=7000+$j"
                line=$line${SERVER_NAME[$i]}:$port" "
        done
	index=0
	for word in `java -cp bft.jar:FlexiCoreProvider-1.6p3.signed.jar BFT.util.KeyGen --generate 1`
        do
                if [ $index -eq 2 ]
                then
                        pub=$word
                fi
                if [ $index -eq 5 ]
                then
                        priv=$word
                fi
                if [ $index -eq 8 ]
                then
                        secret=$word
                fi
                let "index=$index+1"
        done

        line=$line$pub
        echo $line
        echo PRIV = $priv > keys/EXEC$i.privk.properties
        echo PUB = $pub >> keys/EXEC$i.privk.properties
        echo SECRET = $secret >> keys/EXEC$i.privk.properties
done
echo

#Generate Verifier Nodes

if [ ${#VERIFIER_NAME[*]} -ne $verifier_num ]
then
        echo "Wrong verifier num"
fi
for ((i=0; i<$verifier_num; i++))
do
        line="VERIFIER.$i = "
        for((j=0; j<$total_ports; j++))
        do
                let "port=19000+$j"
                line=$line${SERVER_NAME[$i]}:$port" "
        done
        index=0
        for word in `java -cp bft.jar:FlexiCoreProvider-1.6p3.signed.jar BFT.util.KeyGen --generate 1`
        do
                if [ $index -eq 2 ]
                then
                        pub=$word
                fi
                if [ $index -eq 5 ]
                then
                        priv=$word
                fi
                if [ $index -eq 8 ]
                then
                        secret=$word
                fi
                let "index=$index+1"
        done

        line=$line$pub
        echo $line
        echo PRIV = $priv > keys/VERIFIER$i.privk.properties
        echo PUB = $pub >> keys/VERIFIER$i.privk.properties
        echo SECRET = $secret >> keys/VERIFIER$i.privk.properties
done
echo

if [ "$8" == "true" ]; then
	#Generate Filter Nodes
	if [ ${#FILTER_NAME[*]} -ne $filter_num ]
	then
	        echo "Wrong filter num"
	fi

	for ((i=0; i<$filter_num; i++))
	do
	        line="FILTER.$i = "
	        for((j=0; j<$total_ports; j++))
	        do
	                let "port=8000+$j"
	                line=$line${FILTER_NAME[$i]}:$port" "
	        done
		index=0
		for word in `java -cp bft.jar:FlexiCoreProvider-1.6p3.signed.jar BFT.util.KeyGen --generate 1`
        	do
                	if [ $index -eq 2 ]
                	then
                	        pub=$word
                	fi
             	   	if [ $index -eq 5 ]
                	then
                        	priv=$word
                	fi
                	if [ $index -eq 8 ]
                	then
                        	secret=$word
                	fi
                	let "index=$index+1"
        	done

        	line=$line$pub
        	echo $line
        	echo PRIV = $priv > keys/FILTER$i.privk.properties
        	echo PUB = $pub >> keys/FILTER$i.privk.properties
        	echo SECRET = $secret >> keys/FILTER$i.privk.properties
	done
	echo
fi
#Generate Client Nodes

client_machine_num=${#CLIENT_NAME[*]}
port=9000
for (( i=0; i<$client_num; i++))
do
	let "index=$i%$client_machine_num"
    	client_machine_name=${CLIENT_NAME[$index]}
	line="CLIENT.$i = "
    	for((j=0; j<$total_ports; j++))
        do
		let "port=$port+1"
                line=$line$client_machine_name:$port" "
        done
	index=0
	for word in `java -cp bft.jar:FlexiCoreProvider-1.6p3.signed.jar BFT.util.KeyGen --generate 1`
        do
                if [ $index -eq 2 ]
                then
                        pub=$word
                fi
                if [ $index -eq 5 ]
                then
                        priv=$word
                fi
                if [ $index -eq 8 ]
                then
                        secret=$word
                fi
                let "index=$index+1"
        done

        line=$line$pub
        echo $line
        echo PRIV = $priv > keys/CLIENT$i.privk.properties
        echo PUB = $pub >> keys/CLIENT$i.privk.properties
        echo SECRET = $secret >> keys/CLIENT$i.privk.properties
done
