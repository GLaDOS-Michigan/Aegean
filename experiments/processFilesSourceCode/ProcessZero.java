package org.apache.zookeeper.test;

import java.io.*;
import java.text.DecimalFormat;
import java.util.StringTokenizer;

public class ProcessZero {

    public static void main(String []args) throws Exception{
        String fileName=args[0];
        int startOps=Integer.parseInt(args[1]);
        int endOps=Integer.parseInt(args[2]);
        int totalOps=Integer.parseInt(args[3]);
        int interval=Integer.parseInt(args[4]);
        int intervalCount=Integer.parseInt(args[5]);
        float maxDifference=Float.parseFloat(args[6]);
        String path=args[7];
        //System.out.println(path);
        if(path.endsWith("/")) {
            path=path.substring(0, path.length()-1);
        }
        //System.out.println(path);
        String numberString = path.substring(path.lastIndexOf("/")+1, path.length());
        int number=Integer.parseInt(numberString);
        //System.out.println(number);
        int []reqCounts=new int[100000];
        long []latencies=new long[100000];
        int maxIndex=0;
        BufferedReader reader=new BufferedReader(new InputStreamReader(new FileInputStream(fileName)));
        String line=null;
        int lineNo=0;
        long startTime=0;
        long endTime=Long.MAX_VALUE;
        while((line=reader.readLine())!=null){
            lineNo++;
            if(lineNo%totalOps==startOps){
                StringTokenizer stk=new StringTokenizer(line);
                stk.nextToken();
                long reqStartTime=Long.parseLong(stk.nextToken());
                if(reqStartTime>startTime){
                    startTime=reqStartTime;
                }
            }
            if(lineNo%totalOps==endOps){
                StringTokenizer stk=new StringTokenizer(line);
                stk.nextToken();
                long reqEndTime=Long.parseLong(stk.nextToken());
                if(reqEndTime<endTime){
                    endTime=reqEndTime;
                }
            }
        }
        //System.out.println(args[0]+" "+startTime+" "+endTime);
        reader.close();
        reader=new BufferedReader(new InputStreamReader(new FileInputStream(fileName)));
        int count = 0;
        double sumLatency = 0;

        while((line=reader.readLine())!=null){
            StringTokenizer stk=new StringTokenizer(line);
            stk.nextToken();
            long reqStartTime=Long.parseLong(stk.nextToken());
            long reqEndTime=Long.parseLong(stk.nextToken());
            if(reqStartTime>startTime&&reqEndTime<endTime){
                count++;
                double latency = reqEndTime-reqStartTime;
                sumLatency += latency;

                int index=(int)(reqStartTime-startTime)/interval;
                if(index>maxIndex){
                    maxIndex=index;
                }
                reqCounts[index]++;
                latencies[index]+=latency;
            }
        }

        long timePassed = endTime - startTime;
        double throughput = count / (timePassed/Math.pow(10,3));
        double averageLatency = sumLatency / count;
        DecimalFormat df = new DecimalFormat("#.#");

        System.out.println("client# throughput latency (new calculation): " + number+"\t"+ Math.round(throughput) + "\t"+ df.format(averageLatency));
        if(maxIndex > 999 ) {

            System.out.println("be careful about the throuhgput . I changed the array sizes to 100000 since it was giving error previously. " +
                    "I am not sure why they were keeping array of size 1000");
        }
        boolean correct=false;
        int []thrTemp=new int[intervalCount];
        float []latTemp=new float[intervalCount];
        for(int i=0;i<maxIndex-intervalCount;i++){
            for(int j=0;j<intervalCount;j++){
                thrTemp[j]=reqCounts[i+j]*1000/interval;
                latTemp[j]=(float)latencies[i+j]/(float)(reqCounts[i+j]);
            }
            correct=true;
            for(int j=0;j<intervalCount-1;j++){
                if(dif(thrTemp[j],thrTemp[j+1])>maxDifference
                        ||dif(latTemp[j],latTemp[j+1])>maxDifference){
                    correct=false;
                    break;
                }
            }
            if(correct==true){
				/*System.out.println("Succeed at "+i);
				for(int j=0;j<maxIndex;j++){
					System.out.println(j);
        	                        System.out.println(reqCounts[j]);
                	                System.out.println(latencies[j]);
	                        }*/

                int avgThr=0;
                float avgLat=0;
                for(int j=0;j<intervalCount;j++){
                    avgThr+=thrTemp[j];
                    avgLat+=latTemp[j];
                }
                System.out.println(number+"\t"+avgThr/intervalCount+"\t"+avgLat/intervalCount);
                break;
            }

        }
        if(correct==false)
            System.out.println("Cannot find a result");
        reader.close();
    }

    static float dif(float a, float b){
        return Math.abs((a-b)/a);
    }

}

