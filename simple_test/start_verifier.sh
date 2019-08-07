#!/bin/bash
#../jikesrvm/dist/production_ia32-linux/rvm -Xbootclasspath/p:log4j-1.2.15.jar:bft.jar:FlexiCoreProvider-1.6p3.signed.jar:CoDec-build17-jdk13.jar:netty-3.2.1.Final-SNAPSHOT.jar BFT.verifier.VerifierBaseNode 0  test.properties
java -cp log4j-1.2.15.jar:bft.jar:FlexiCoreProvider-1.6p3.signed.jar:CoDec-build17-jdk13.jar:netty-3.2.1.Final.jar BFT.verifier.VerifierBaseNode 0  test.properties
