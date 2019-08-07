#!/bin/bash

rm bft.jar
cp ../dist/lib/bft.jar .
rm lib/bft.jar
cp bft.jar lib/

java -ea -cp lib/bft.jar:lib/FlexiCoreProvider-1.6p3.signed.jar:lib/CoDec-build17-jdk13.jar BFT.membership.MembershipGenerator
