list=`find . | grep -v \.svn | grep \.java$ | sed "s#^.#$(pwd)#"`
echo "$list" > list.temp

while read line; do
	sed -i "" "s#MerkleTreeInstance.update[^)]*)\;##" $line
done < list.temp

rm list.temp
