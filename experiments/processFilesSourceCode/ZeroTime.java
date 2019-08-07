package org.apache.zookeeper.test;

import java.io.BufferedReader;
import java.io.*;
import java.util.StringTokenizer;

public class ZeroTime {
	
	public static void main(String []args) throws Exception{
		String fileName=args[0];
		long startTime=Long.parseLong(args[1]);
		long endTime=Long.parseLong(args[2]);
		BufferedReader reader=new BufferedReader(new InputStreamReader(new FileInputStream(fileName)));
		String line=null;
		while((line=reader.readLine())!=null){
			if(line.startsWith("#req")){
				StringTokenizer stk=new StringTokenizer(line);
				String req=stk.nextToken();
				long reqStartTime=Long.parseLong(stk.nextToken())-startTime;
				long reqEndTime=Long.parseLong(stk.nextToken())-startTime;
				String clientId=stk.nextToken();
				System.out.println(req+" "+reqStartTime+" "+reqEndTime+" "+clientId);
			}
		}
	}

}

