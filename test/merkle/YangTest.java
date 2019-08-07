package merkle;

import java.util.HashMap;

import merkle.wrapper.MTMapWrapper;

public class YangTest

{

	public static void test() throws Exception

	{
		MTMapWrapper map = new MTMapWrapper(new HashMap<String, Object>(),
				false, true, false);

		MerkleTreeInstance.add(map);

		MerkleTreeInstance.getShimInstance().setVersionNo(1);

		Object o = new Object();

		MerkleTreeInstance.add(o);

		map.put("toto", o);

		MerkleTreeInstance.getShimInstance().setVersionNo(2);
		
		map.put("titi", o);

		MerkleTreeInstance.getShimInstance().rollBack(1);

		Object o2 = map.get("toto");

		if (o2 == null)
		{
			System.out.println("it works");
		} else
		{
			System.out.println("it does not work");
		}

	}

	public static void main(String[] args) throws Exception
	{
		test();
	}

}
