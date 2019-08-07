#!/bin/bash

if [ -z $1 ]; then
        echo "Usage: $0 <packageDir>"  
	exit
else
        packageDir=$1
fi

pushd $packageDir > /dev/null
list=`find . | grep -v \.svn | grep \.java$ | sed "s#^.#$(pwd)#"` 
pwd=`pwd`
#echo $pwd $list
popd > /dev/null

java -cp build/jar/ftinst.jar:lib/* JDT.ParseAST $pwd $list
