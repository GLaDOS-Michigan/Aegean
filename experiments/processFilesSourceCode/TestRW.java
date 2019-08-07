// $Id: TestRW.java 2660 2009-02-21 19:46:36Z yangwang $
package org.apache.zookeeper.test;

import org.apache.zookeeper.CreateMode;
import org.apache.zookeeper.ZooDefs;
import org.apache.zookeeper.ZooKeeper;
import org.apache.zookeeper.data.ACL;
import org.apache.zookeeper.data.Id;
import org.apache.zookeeper.data.Stat;
import org.apache.zookeeper.Watcher;
import org.apache.zookeeper.WatchedEvent;
import org.apache.zookeeper.AsyncCallback.DataCallback;
import org.apache.zookeeper.KeeperException;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.io.IOException;

import java.net.ConnectException;

public class TestRW {

    static private class MyWatcher implements Watcher {
        public void process(WatchedEvent event) {
            System.err.println(event);
        }
    }

	public static void main(String[] args) throws NumberFormatException, KeeperException, IOException, InterruptedException {
		if(args.length!=9){
			System.out.println("<Usage> java TestRW clientId host timeout dataNum dataSize read write totalOps usedOps");
			System.exit(1);
		}
		int clientId=Integer.parseInt(args[0]);
		String host=args[1];
		int timeout=Integer.parseInt(args[2]);
		ZooKeeper zk = new ZooKeeper(host, timeout, new MyWatcher());

		int dataNum=Integer.parseInt(args[3]);
		int dataSize=Integer.parseInt(args[4]);
		int read=Integer.parseInt(args[5]);
		int write=Integer.parseInt(args[6]);
		int totalOps=Integer.parseInt(args[7]);
		int usedOps=Integer.parseInt(args[8]);
		
        byte[] dataarray = new byte[dataSize];
        byte[] data = new byte[0];
		float readRatio=(float)read/(float)(read+write);
		Random r1=new Random(System.currentTimeMillis());
		Random r2=new Random(System.currentTimeMillis());
		System.err.println("#start "+System.currentTimeMillis());	
		for(int i=0;i<totalOps;i++){
			try {
				String path="/"+Math.abs(r2.nextInt())%dataNum;
				long startTime=System.currentTimeMillis();
				if(r1.nextFloat()<readRatio){
					Stat stat=new Stat();
					data=zk.getData(path, false, stat);
				} else {
					zk.setData(path, dataarray, -1);
				}
				long endTime=System.currentTimeMillis();
				System.err.println("#req"+i+" "+startTime+" "+endTime+" "+clientId);
			} catch (Exception e) {
				System.err.println("Could not connect. I ll wait for 3 seconds");
				Thread.sleep(3000);
				i--;
				continue;
			}
		}
		System.err.println("end "+System.currentTimeMillis());
		//zk.close();
		
	}
}

