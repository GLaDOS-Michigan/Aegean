package merkle;
import BFT.util.UnsignedTypes;
import java.security.Security;
import java.util.Random;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import de.flexiprovider.core.FlexiCoreProvider;
import java.io.*;

public class HashTest{

    public static void main(String []args) throws Exception{
	Security.addProvider(new FlexiCoreProvider());
	if(true){
	
	    new HashThread().start();
	    //new HashThread().start();
	    //new HashThread().start();
	    //new HashThread().start();
	}
	/*if(args.length!=3){
	    System.out.println("Usage: HashTest <hash algorithm> <data size> <rounds>");
	    return;
	}
	//Security.addProvider(new FlexiCoreProvider());
	String algorithm = args[0];
	int datasize = Integer.parseInt(args[1]);
	int rounds = Integer.parseInt(args[2]);
	Random r = new Random();
	byte []data = new byte[datasize];
	long time = System.currentTimeMillis();
	MessageDigest digest = MessageDigest.getInstance(algorithm);
	/*for(int i=0;i<rounds-1;i++){
	    digest = MessageDigest.getInstance(algorithm);
	}
	System.out.println("timeGetInstance="+(System.currentTimeMillis() - time));*/
	//byte[] hash = digest.digest();
	/*UnsignedTypes.printHash(hash);
	hash = digest.digest();
	UnsignedTypes.printHash(hash);
	digest.reset();
	hash = digest.digest();
	UnsignedTypes.printHash(hash);
	digest.reset();
	UnsignedTypes.printHash(digest.digest(data));
	UnsignedTypes.printHash(new BFT.messages.Digest(data, 0, data.length).getBytes());*/
	
	/*time = System.currentTimeMillis();
	for(int j=0;j<100;j++){
	    byte []tmp=new byte[10240];
	for(int i=0;i<rounds+1;i++){
	    /*ByteArrayOutputStream bos = new ByteArrayOutputStream();
            ObjectOutputStream oos = new ObjectOutputStream(bos);
	    oos.writeInt(1);
	    oos.writeInt(1);
	    oos.writeLong(1L);
	    oos.writeLong(2L);
	    oos.write(data);
	    oos.flush();
            //digest.reset();
            r.nextBytes(data);
            if(i==1)
		time = System.currentTimeMillis(); 
            hash = digest.digest(data);
	}
	System.out.println("time="+(System.currentTimeMillis() - time));
	}*/
    }

    public static class HashThread extends Thread{
	MessageDigest m;
	public int value=0;
	public void run(){
	    try{
		byte []data=new byte[1024];
		m = MessageDigest.getInstance("SHA256","FlexiCore");
		while(true){
		    long time = System.currentTimeMillis();
		    for(int i=0;i<100000;i++)
			m.digest(data);
		    System.out.println("time="+(System.currentTimeMillis()-time));
		}
	    }
	    catch(Exception e){
		e.printStackTrace();
	    }
	}	
    }


}
