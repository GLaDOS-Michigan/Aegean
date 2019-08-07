#!/bin/bash

if [ -z $1 ]; then
	numNodes=1
else 
	numNodes=$1
fi

for ((i=0; i < numNodes; i++)); do
java -Xmx256M -Djava.library.path=. -cp log4j-1.2.15.jar:bft.jar:FlexiCoreProvider-1.6p3.signed.jar:CoDec-build17-jdk13.jar:netty-3.2.1.Final.jar Applications.benchmark.BenchServer test.properties $i 1000 1000 &>exec$i.txt &
#java -Xmx512M -Xbootclasspath/p:bft.jar:FlexiCoreProvider-1.6p3.signed.jar:CoDec-build17-jdk13.jar:netty-3.2.1.Final-SNAPSHOT.jar:h2.jar Applications.tpcw_ev.TPCW_Server test.properties $i &>exec$i.txt & 
#../jikesrvm/dist/production_ia32-linux/rvm -Xmx512M -Xbootclasspath/p:bft.jar:FlexiCoreProvider-1.6p3.signed.jar:CoDec-build17-jdk13.jar:netty-3.2.1.Final.jar:h2.jar Applications.tpcw_ev.TPCW_Server test.properties $i &>exec$i.txt & 

done
