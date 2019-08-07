package BFT.generalcp;

import java.util.concurrent.LinkedBlockingQueue;

public class SharedState {
    private LinkedBlockingQueue<FinishedFileInfo> logFilesToHash = new LinkedBlockingQueue<FinishedFileInfo>();
    private LinkedBlockingQueue<CPToken> hashOfSyncFiles = new LinkedBlockingQueue<CPToken>();
    private LinkedBlockingQueue<FinishedFileInfo> syncFilesToHash = new LinkedBlockingQueue<FinishedFileInfo>();

    private long seqNoReplied = -1;


    public void clear() {
        hashOfSyncFiles.clear();
        syncFilesToHash.clear();
    }

    public void reset(long seqNo) {
        logFilesToHash.clear();
        seqNoReplied = -1;
    }

    public void execDone(byte[] reply, RequestInfo info) {
        if (info.isLastReqBeforeCP()) {
            synchronized (this) {
                System.out.println("execDone for " + info.getSeqNo());
                this.seqNoReplied = info.getSeqNo();
                this.notifyAll();
            }
        }
    }

    public void waitForExec(long seqNo) {
        synchronized (this) {
            while (seqNoReplied < seqNo) {
                try {
                    this.wait();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
    }

    public void flushDone(long seqNo, String fileName) {
        synchronized (this) {
            System.out.println("Handle flushDone seqNo=" + seqNo + " " + fileName);
            this.logFilesToHash.add(new FinishedFileInfo(seqNo, fileName));
        }
    }


    public FinishedFileInfo getNextLogToHash() {
        try {
            return logFilesToHash.take();
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }


    private long snapshotSeqNo = -1;
    private boolean isSyncing = false;

    public void startSync(long seqNo) {
        synchronized (this) {
            this.isSyncing = true;
            this.snapshotSeqNo = seqNo;
        }
    }

    public void syncDone(String fileName) {
        synchronized (this) {
            this.isSyncing = false;
            this.notifyAll();
        }
        this.syncFilesToHash.add(new FinishedFileInfo(snapshotSeqNo, fileName));
    }

    public void waitForSyncDone() {
        synchronized (this) {
            while (this.isSyncing) {
                try {
                    this.wait();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
    }

    public FinishedFileInfo getNextSyncToHash() {
        try {
            return syncFilesToHash.take();
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    public void syncHashDone(CPToken token) {
        this.hashOfSyncFiles.add(token);
    }

    public CPToken getNextSyncHash() {
        try {
            return hashOfSyncFiles.take();
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

	/*private boolean isLoadingSnapshot = false;

	public void startLoadSnapshot() {
		synchronized (this) {
			this.isLoadingSnapshot = true;
		}
	}

	public void loadSnapshotDone() {
		synchronized (this) {
			this.isLoadingSnapshot = false;
			this.notifyAll();
		}
	}

	public void waitForLoadSnapshot() {
		synchronized (this) {
			while (this.isLoadingSnapshot) {
				try {
					this.wait();
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		}
	}*/

    private CPToken lastCPToken = new CPToken();

    public CPToken getLastCPToken() {
        return this.lastCPToken;
    }

    public void setLastCPToken(CPToken cpToken) {
        this.lastCPToken = cpToken;
    }
}
