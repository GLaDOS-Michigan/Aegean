#!/bin/bash

if [ -z $1 ]; then
	numNodes=1
else 
	numNodes=$1
fi

for ((i=0; i < numNodes; i++)); do
#../jikesrvm/dist/production_ia32-linux/rvm -Xbootclasspath/p:log4j-1.2.15.jar:bft.jar:FlexiCoreProvider-1.6p3.signed.jar:CoDec-build17-jdk13.jar:netty-3.2.1.Final-SNAPSHOT.jar BFT.filter.FilterBaseNode $i test.properties 
java -cp log4j-1.2.15.jar:bft.jar:FlexiCoreProvider-1.6p3.signed.jar:CoDec-build17-jdk13.jar:netty-3.2.1.Final.jar BFT.filter.FilterBaseNode $i test.properties 
done


