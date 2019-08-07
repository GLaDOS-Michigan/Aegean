package org.apache.zookeeper.test;

import java.io.*;
import java.util.StringTokenizer;

public class ProcessZeroTimeThr {
	
	public static void main(String []args) throws Exception{
		String fileName=args[0];
		int startOps=Integer.parseInt(args[1]);
		int endOps=Integer.parseInt(args[2]);
		int totalOps=Integer.parseInt(args[3]);
		int interval=Integer.parseInt(args[4]);
		int intervalCount=Integer.parseInt(args[5]);
		float maxDifference=Float.parseFloat(args[6]);
		int []reqCounts=new int[1000];
		long []latencies=new long[1000];
		int maxIndex=0;
		BufferedReader reader=new BufferedReader(new InputStreamReader(new FileInputStream(fileName)));
		String line=null;
		int lineNo=0;
		reader=new BufferedReader(new InputStreamReader(new FileInputStream(fileName)));
		while((line=reader.readLine())!=null){
			StringTokenizer stk=new StringTokenizer(line);
			stk.nextToken();
			long reqStartTime=Long.parseLong(stk.nextToken());
			long reqEndTime=Long.parseLong(stk.nextToken());
			int index=(int)(reqStartTime)/interval;
			if(index>maxIndex){
				maxIndex=index;
			}
			reqCounts[index]++;
			latencies[index]+=reqEndTime-reqStartTime;
		}
		for(int i=0;i<maxIndex;i++){
			System.out.println(i*interval/1000+" "+reqCounts[i]*1000/interval);
			System.out.println((i+1)*interval/1000+" "+reqCounts[i]*1000/interval);
		}
		reader.close();
	}
}

