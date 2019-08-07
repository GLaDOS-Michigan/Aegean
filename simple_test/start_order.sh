#!/bin/bash

if [ -z $1 ]; then
	numNodes=1
else 
	numNodes=$1
fi

for ((i=0; i < numNodes; i++)); do
java -Djava.library.path=. -cp log4j-1.2.15.jar:bft.jar:FlexiCoreProvider-1.6p3.signed.jar:CoDec-build17-jdk13.jar:netty-3.2.1.Final.jar:h2.jar BFT.order.OrderBaseNode $i test.properties &
done
