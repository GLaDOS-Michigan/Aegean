// $Id: GenerateData.java 2660 2009-02-21 19:46:36Z yangwang $
package org.apache.zookeeper.test;

import org.apache.zookeeper.CreateMode;
import org.apache.zookeeper.ZooDefs;
import org.apache.zookeeper.ZooKeeper;
import org.apache.zookeeper.data.ACL;
import org.apache.zookeeper.data.Id;
import org.apache.zookeeper.Watcher;
import org.apache.zookeeper.WatchedEvent;

import java.util.ArrayList;
import java.util.List;

public class GenerateData {
	static private class MyWatcher implements Watcher {
        public void process(WatchedEvent event) {
            System.err.println(event);
        }
    }
	public static void main(String[] args) throws Exception {
		if(args.length!=3){
			System.out.println("<Usage> java GenerateData host dataNum dataSize");
			System.exit(1);
		}
		ZooKeeper zk = new ZooKeeper(args[0], 3000, null);
		List<ACL> acl = new ArrayList<ACL>();
		ACL newAcl = new ACL();
		newAcl.setId(new Id("world", "anyone"));
		newAcl.setPerms(ZooDefs.Perms.CREATE | ZooDefs.Perms.READ
				| ZooDefs.Perms.WRITE | ZooDefs.Perms.DELETE);
		acl.add(newAcl);
		int dataNum=Integer.parseInt(args[1]);
		int dataSize=Integer.parseInt(args[2]);
		byte []data=new byte[dataSize];
		for(int i=0;i<dataNum;i++){
			System.out.println("Adding "+i);
			zk.create("/"+Integer.toString(i), data, acl, CreateMode.PERSISTENT);
		}
		Thread.sleep(1000);
		//zk.close();
		
	}
}

