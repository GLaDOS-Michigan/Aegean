/**
 * $Id: Forker.java 722 2011-07-04 09:24:35Z aclement $
 */
package BFT.fork;

import BFT.serverShim.ServerShimInterface;


/**
 * @author riche
 */
public class Forker implements Runnable {

    protected boolean forked = false;
    protected ServerShimInterface shim;
    protected long seqNo;

    public Forker(ServerShimInterface shim, long seqNo) {
        this.shim = shim;
        this.seqNo = seqNo;
    }

    public Forker() {

    }

    /**
     * @param args
     */
    public static void main(String[] args) {

//		Forker f = new Forker();
//		
//		try {
//			Hashtable<Integer, String> ht = new Hashtable<Integer, String>();
//			ht.put(1, "Taylor is awesome");
//			FileWriter os = null;
//			int pid = f.sysfork();
//			if(pid == 0) {
//				os = new FileWriter("test" + pid + ".txt");
//				for(int i = 0; i < 10; i++) {
//					//System.out.println(ht.get(1));
//					os.write(ht.get(1) + "\n");
//					if (i == 5) ht.put(1, "Taylor is really awesome");
//				}
//			}
//			else {
//				os = new FileWriter("test" + pid + ".txt");
//				ht.put(1, "Taylor is not awesome");
//				for(int i = 0; i < 10; i++) {
//					//System.out.println(ht.get(1));
//					os.write(ht.get(1) + "\n");
//				}
//			}
//			os.close();
//		} catch (IOException e) {
//			BFT.Debug.kill(e);
//		}
    }

    //protected native int sysfork();
    protected int sysfork() {
        return 0;
    }

    //protected native void syswait();
    protected void syswait() {
    }

    //protected native void sysexit();
    protected void sysexit() {
    }

//	static {
//		System.loadLibrary("ForkerCheat");
//	}

    /* (non-Javadoc)
     * @see java.lang.Runnable#run()
     */
    public void run() {
//		if(sysfork() == 0) {
//			//System.out.println("I've been forked and all I got was this lousy T-Shirt");
//			sysexit();
//		}
//		else {
//			sayForked();
//			//System.out.println("\tPARENT about to wait");
//			syswait();
//			shim.returnCP(new byte[8], seqNo);
//		}
    }

    protected synchronized void sayForked() {
        forked = true;
        notifyAll();
    }

    public synchronized void waitForFork() {
        while (!forked) {
            try {
                wait();
            } catch (Exception e) {

            }
        }

    }

}
