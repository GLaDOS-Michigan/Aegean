#!/bin/bash
#java -cp log4j-1.2.15.jar:bft.jar:FlexiCoreProvider-1.6p3.signed.jar:CoDec-build17-jdk13.jar:netty-3.2.1.Final.jar:h2.jar Applications.tpcw_ev.TPCW_Client test.properties 0 100 &
java -Xbootclasspath/p:log4j-1.2.15.jar:bft.jar:FlexiCoreProvider-1.6p3.signed.jar:CoDec-build17-jdk13.jar:netty-3.2.1.Final.jar:h2.jar Applications.echo.EchoClient 0  test.properties  500 0 00 &
