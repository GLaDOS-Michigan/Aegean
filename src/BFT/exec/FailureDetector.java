package BFT.exec;

import BFT.Parameters;
import org.apache.zookeeper.*;
import org.apache.zookeeper.data.*;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class FailureDetector implements Watcher {

    private final int index;
    private int primary;
    private final int other;
    private int dead;
    private ZooKeeper zk;

    private List<ACL> acl;
    private byte[] data = new byte[1];
    private Parameters parameters;

    public FailureDetector(int index, Parameters param) {
        this.index = index;
        this.parameters = param;
        if (index == 0) {
            other = 1;
        } else {
            other = 0;
        }

        try {
            zk = new ZooKeeper(parameters.ZKLocation, 3000, this);
        } catch (IOException e) {
            e.printStackTrace();
            return;
        }

        acl = new ArrayList<ACL>();
        ACL newAcl = new ACL();
        newAcl.setId(new Id("world", "anyone"));
        newAcl.setPerms(ZooDefs.Perms.CREATE | ZooDefs.Perms.READ
                | ZooDefs.Perms.WRITE | ZooDefs.Perms.DELETE);
        acl.add(newAcl);
        data[0] = (byte) index;

		/* Try to be primary */
        try {
            zk.create("/primary", data, acl, CreateMode.EPHEMERAL);
            primary = index;
        } catch (Exception e) {
            primary = other;
        }

        try {
            zk.create("/node" + index, data, acl, CreateMode.EPHEMERAL);
            Stat stat = zk.exists("/node" + other, true);
            if (stat != null) {
                dead = -1;
            } else
                dead = other;
        } catch (Exception e) {
            e.printStackTrace();
        }


    }

    public int primary() {
        return primary;
    }

    public int dead() {
        return dead;
    }

    public void process(WatchedEvent event) {

        if (event.getType() != Event.EventType.NodeCreated && event.getType() != Event.EventType.NodeDeleted)
            return;

        if (primary != index) {
            try {
                zk.create("/primary", data, acl, CreateMode.EPHEMERAL);
                primary = index;
            } catch (Exception e) {
                primary = other;
            }
        }

        try {
            Stat stat = zk.exists("/node" + other, true);
            if (stat != null) {
                dead = -1;
            } else
                dead = other;
        } catch (Exception e) {
            e.printStackTrace();
        }

    }


    public static void main(String[] args) throws Exception {
        int id = Integer.parseInt(args[0]);
        FailureDetector fd = new FailureDetector(id, new Parameters());
        System.out.println(fd.primary() + " " + fd.dead());
        Thread.sleep(10000);
        System.out.println(fd.primary() + " " + fd.dead());
    }
}
    
