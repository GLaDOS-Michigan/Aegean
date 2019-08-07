package BFT.generalcp;

import BFT.Parameters;
import BFT.serverShim.ServerShimInterface;

import java.io.File;
import java.io.FileInputStream;
import java.util.ArrayList;

public class HashThread extends Thread {

    private ServerShimInterface shim;
    private SharedState state;

    private SyncHashThread syncer;
    private Parameters parameters;

    public HashThread(Parameters param, ServerShimInterface shim, SharedState state) {
        this.shim = shim;
        this.parameters = param;
        this.state = state;
        this.syncer = new SyncHashThread();
        this.syncer.start();
    }

    private class SyncHashThread extends Thread {

        public void run() {
            while (true) {
                try {
                    FinishedFileInfo info = state.getNextSyncToHash();
                    System.out.println("Processing " + info.fileName);
                    ArrayList<StateToken> tmp = generateToken(info.fileName,
                            StateToken.SNAPSHOT, info.seqNo);
                    CPToken cpToken = new CPToken();
                    cpToken.getAppCPTokens().addAll(tmp);
                    cpToken.setCPSeqNo(info.seqNo);
                    state.syncHashDone(cpToken);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
    }

    public void handleLastCPToken(long seqNo) {
//        System.err.println("handleLstCPToken " + seqNo);
        try {
            CPToken cpToken = state.getNextSyncHash();
//            System.err.println("get next synchash");
            while (cpToken.getCPSeqNo() != seqNo) {
                cpToken = state.getNextSyncHash();
            }
//            System.err.println("Find token " + cpToken);
            CPToken lastCPToken = state.getLastCPToken();
            if (lastCPToken.getCPSeqNo() == seqNo) {
//                System.err.println("CP Token already updated");
                return;
            }
            int logCount = GeneralCP.APP_CP_INTERVAL
                    / BFT.order.Parameters.checkPointInterval;
            for (int i = 0; i < lastCPToken.getLogTokenSize(); i++) {
                if (lastCPToken.getLogToken(i).getSeqNo() > seqNo)
                    cpToken.addLogToken(
                            lastCPToken.getLogToken(i));
            }
            cpToken.setCPSeqNo(seqNo);
            state.setLastCPToken(cpToken);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void run() {
        boolean firstSync = true;
        while (true) {
            try {
                FinishedFileInfo finishedFileInfo = state.getNextLogToHash();
//                System.err.println("Hash for " + finishedFileInfo.fileName);
                ArrayList<StateToken> tmp = generateToken(
                        finishedFileInfo.fileName, StateToken.LOG,
                        finishedFileInfo.seqNo);
                if (tmp.size() > 1) {
                    throw new RuntimeException("Log file too large "
                            + finishedFileInfo.fileName);
                }
                CPToken lastCPToken = state.getLastCPToken();
                for (StateToken tk : tmp)
                    lastCPToken.addLogToken(tk);
                state.waitForExec(finishedFileInfo.seqNo);
//                System.err.println("ReturnCP " + lastCPToken);
                shim.returnCP(lastCPToken.getBytes(), finishedFileInfo.seqNo);

                // At a specific time, we need to replace logs with snapshot
                if (finishedFileInfo.seqNo % GeneralCP.APP_CP_INTERVAL == GeneralCP.APP_CP_INTERVAL - 1) {
//                    System.err.println("xxxx");
                    if (finishedFileInfo.seqNo - lastCPToken.getCPSeqNo() == 2 * GeneralCP.APP_CP_INTERVAL){
//                        System.err.println("yyyy");
                        handleLastCPToken(finishedFileInfo.seqNo
                                - GeneralCP.APP_CP_INTERVAL);
                    }
//                    System.err.println("zzzz");
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private ArrayList<StateToken> generateToken(String fileName, int type,
                                                long seqNo) {
        try {
            File tmp = new File(fileName);
            if (!parameters.uprightInMemory) {
                if (!tmp.exists())
                    throw new RuntimeException("generateToken cannot find "
                            + fileName);
            } else {
                if (!MemoryFS.exists(tmp.getAbsolutePath()))
                    throw new RuntimeException("generateToken cannot find "
                            + fileName);
            }
            String shortName = new File(fileName).getName();
            ArrayList<StateToken> list = new ArrayList<StateToken>();
            // calculate the hash of the file
            if (!parameters.uprightInMemory) {
                FileInputStream fis = new FileInputStream(fileName);

                if (type == StateToken.SNAPSHOT) {
                    byte[] data = new byte[1048576];
                    long offset = 0;

                    // System.out.println("available="+fis.available());
                    while (fis.available() > 0) {
                        if (fis.available() < 1048576)
                            data = new byte[fis.available()];
                        int len = fis.read(data);
                        StateToken stateToken = new StateToken(parameters, type, shortName,
                                offset, len, data, seqNo);
                        list.add(stateToken);
                        offset += len;
                    }
                } else {

                    byte[] data = new byte[fis.available()];
                    fis.read(data);
                    StateToken stateToken = new StateToken(parameters, type, shortName, 0,
                            data.length, data, seqNo);
                    list.add(stateToken);
                }
            } else {
                byte[] fileContent = MemoryFS.read(tmp.getAbsolutePath());

                if (type == StateToken.SNAPSHOT) {
                    byte[] data = new byte[1048576];
                    long offset = 0;

                    // System.out.println("available="+fis.available());
                    while (offset < fileContent.length) {
                        int len;
                        if (fileContent.length - offset < 1048576) {
                            data = new byte[fileContent.length - (int) offset];
                            len = fileContent.length - (int) offset;
                        } else
                            len = 1048576;
                        System.arraycopy(fileContent, (int) offset, data, 0, len);
                        StateToken stateToken = new StateToken(parameters, type, shortName,
                                offset, len, data, seqNo);
                        list.add(stateToken);
                        offset += len;
                    }
                } else {

                    StateToken stateToken = new StateToken(parameters, type, shortName, 0,
                            fileContent.length, fileContent, seqNo);
                    list.add(stateToken);
                }
            }
            return list;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }
}
