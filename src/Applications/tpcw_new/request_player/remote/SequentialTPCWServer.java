package Applications.tpcw_new.request_player.remote;

import Applications.tpcw_new.request_player.ConnectionStatement;
import Applications.tpcw_new.request_player.RequestPlayerUtils;
import BFT.Debug;
import BFT.Parameters;
import BFT.exec.Info;
import BFT.generalcp.*;
import BFT.util.UnsignedTypes;

import java.io.*;

/**
 * Created by remzi on 8/29/17.
 */
public class SequentialTPCWServer extends RealtimeTPCWServer implements AppCPInterface {
    private CPAppInterface generalCP;
    private long lastSeqNo = 0;
    private String syncDir;

    public SequentialTPCWServer(Parameters parameters, CPAppInterface cpAppInterface, int id, String syncDir) {
        super(parameters, null, id);
        this.syncDir = syncDir;
        generalCP = cpAppInterface;
    }

    public static void main(String[] args) {
        if (args.length != 4) {
            System.out.println("Usage Server <membership> <id> <logpath> <snapshotPath>");
            return;
        }

        String membershipFile = args[0];
        int id = Integer.parseInt(args[1]);
        String logPath = args[2];
        String snapshotPath = args[3];
        System.out.println("Membership: " + membershipFile + ",id " + id + ", logPath: " + logPath + ",snapshotPath: " + snapshotPath);

        RequestPlayerUtils.initProperties(null);
        GeneralCP generalCP = new GeneralCP(id, membershipFile, logPath, snapshotPath);
        SequentialTPCWServer main = new SequentialTPCWServer(generalCP.getShimParameters(), generalCP, id, snapshotPath);
        main.createSession();

        generalCP.setupApplication(main);

    }

    private void createSession() {
        connections = new ConnectionStatement[this.parameters.getNumberOfClients()];
        for (int i = 0; i < connections.length; i++) {
            ConnectionStatement cs = new ConnectionStatement(dbStatements);
            cs.setRandomSeed(i);
            connections[i] = cs;
            System.err.println("created session for client " + i);
        }
    }

    @Override
    public void execAsync(byte[] request, RequestInfo info) {
        lastSeqNo = info.getSeqNo();
        exec(request, info);
    }

    @Override
    public void execReadonly(byte[] request, int clientId, long requestId) {
        throw new RuntimeException("read only is not implemented");
    }

    @Override
    public void loadSnapshot(String fileName) {
        throw new RuntimeException("loadSnapShot is not implemented");
    }

    @Override
    public void sync() {
        try {
            if (!parameters.uprightInMemory) {
                File syncFile = new File(this.syncDir + "/ht_sync_" + lastSeqNo);
                ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(syncFile));
                oos.writeLong(lastSeqNo);
                oos.close();
            } else {
                File syncFile = new File(this.syncDir + "/ht_sync_" + lastSeqNo);
                ByteArrayOutputStream bos = new ByteArrayOutputStream();
                ObjectOutputStream oos = new ObjectOutputStream(bos);
                oos.writeLong(lastSeqNo);
                oos.close();
                MemoryFS.write(syncFile.getAbsolutePath(), bos.toByteArray());
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        generalCP.syncDone(this.syncDir + "/ht_sync_" + lastSeqNo);
    }

    @Override
    protected void sendReply(Info info, Serializable data) {
        try {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            ObjectOutputStream oos = new ObjectOutputStream(bos);
            oos.writeObject(data);
            oos.flush();

            byte[] res = bos.toByteArray();
            Debug.debug(Debug.MODULE_EXEC, "Reply bytes are: " + UnsignedTypes.bytesToHexString(res));
            generalCP.execDone(res, (RequestInfo) info);
        } catch (Exception e) {
            Debug.debug(Debug.MODULE_EXEC, "lcosvse: failed sending reply!!");
            e.printStackTrace();
        }
    }
}
