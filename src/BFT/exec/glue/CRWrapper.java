package BFT.exec.glue;
//
//public class CRWrapper implements Runnable {
//	
//	transient private CRGlueThread thread;
//	transient private Object task;
//
//	public CRWrapper(CRGlueThread thread, Object task) {
//		this.thread = thread;
//		this.task = task;
//	}
//	
//	@Override
//	public void run() {
//		thread.invokeSuperDoAppTask(task);
//	}
//}

public class CRWrapper implements Runnable {
    private CRGlueThread thread;

    public CRWrapper(CRGlueThread thread) {
        this.thread = thread;
    }

    @Override
    public void run() {
        thread.workerLoop();
    }
}