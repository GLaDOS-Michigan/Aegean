// $Id: Entry.java 722 2011-07-04 09:24:35Z aclement $

package BFT.messages;

import BFT.Parameters;
import util.UnsignedTypes;

public class Entry {//implements Comparable<Entry>{

    public Entry(Parameters param, long client, long sub, long req, long GC, Digest d) {
        clientId = client;
        subId = sub;
        requestId = req;
        command = null;
        digest = d;
        has_digest = true;
        parameters = param;
        GCId = GC;
    }

    public Entry(Parameters param, long client, long sub, long req, long GC, byte[] com) {
        clientId = client;
        subId = sub;
        requestId = req;
        command = com;
        bytes = null;
        has_digest = false;
        parameters = param;
        GCId = GC;
    }


    public Entry(Parameters param, long client, long sub, long req, Digest d) {
        clientId = client;
        subId = sub;
        requestId = req;
        command = null;
        digest = d;
        has_digest = true;
        parameters = param;
        GCId = 0;
    }

    public Entry(Parameters param, long client, long sub, long req, byte[] com) {
        clientId = client;
        subId = sub;
        requestId = req;
        command = com;
        bytes = null;
        has_digest = false;
        parameters = param;
        GCId = 0;
    }

    public static Entry fromBytes(Parameters param, byte[] bytes, int offset) {
        int orig = offset;
        // digest or command
        boolean has_digest = bytes[offset++] == 1;
        // client id
        byte[] tmp;
        long clientId = UnsignedTypes.bytesToLong(bytes, offset);
        offset += UnsignedTypes.uint32Size;
        // read the subId
        long subId = UnsignedTypes.bytesToLong(bytes, offset);
        offset += UnsignedTypes.uint32Size;
        // read the requestId
        //	tmp = new byte[4];
        long requestId = UnsignedTypes.bytesToLong(bytes, offset);
        offset += UnsignedTypes.uint32Size;
        // read GC id
        long GCId = UnsignedTypes.bytesToLong(bytes, offset);
        offset += UnsignedTypes.uint32Size;
        // read the command size
        //	tmp = new byte[4];
        int size = (int) (UnsignedTypes.bytesToLong(bytes, offset));
        offset += UnsignedTypes.uint32Size;
        // read the command
        byte[] com = new byte[size];
        for (int i = 0; i < com.length; i++, offset++) {
            com[i] = bytes[offset];
        }
        Entry entry;
        if (has_digest) {
            Digest d = Digest.fromBytes(param, com);
            com = null;
            entry = new Entry(param, clientId, subId, requestId, GCId, d);
        } else {
            entry = new Entry(param, clientId, subId, requestId, GCId, com);
        }
        return entry;
    }


    public static Entry[] getList(Parameters param, byte[] bytes, int e_count) {
        byte[] tmp = new byte[4];
        Entry[] v = new Entry[e_count];
        int count = 0;
        int offset = 0;
        while (offset < bytes.length) {
            v[count++] = Entry.fromBytes(param, bytes, offset);
            offset += v[count - 1].getSize();
        }
        if (count != e_count) {
            throw new RuntimeException("Ill formed entry list");
        }
        return v;
    }

    protected long clientId;
    protected long subId;
    protected long requestId;
    protected long GCId;
    protected byte[] command;
    protected byte[] bytes;
    protected Digest digest;
    protected boolean has_digest;

    public Parameters getParameters() {
        return parameters;
    }

    public void setParameters(Parameters parameters) {
        this.parameters = parameters;
    }

    protected Parameters parameters;


    public boolean has_digest() {
        return has_digest;
    }

    public long getClient() {
        return clientId;
    }

    public long getGCId() {
        return GCId;
    }

    public long getSubId() {
        return subId % 10;
    }

    public long getFakeSubId() {
        return subId;
    }

    public long getRequestId() {
        return requestId;
    }

    public byte[] getCommand() {
        return command;
    }

    public Digest getDigest() {
        return digest;
    }

    public void setSubId(long subId) {
        this.subId = subId;
    }

    public void setBytes(byte[] bytes) {
        this.bytes = bytes;
    }

    public byte[] getBytes() {
        if (bytes == null) {
            int offset = 0;
            bytes = new byte[getSize()];
            // digest or command
            bytes[offset++] = has_digest ? (byte) 1 : (byte) 0;
            // write the client id
            UnsignedTypes.longToBytes(clientId, bytes, offset);
            offset += UnsignedTypes.uint32Size;
            // write the sub id
            UnsignedTypes.longToBytes(subId, bytes, offset);
            offset += UnsignedTypes.uint32Size;
            // write the request id
            UnsignedTypes.longToBytes(requestId, bytes, offset);
            offset += UnsignedTypes.uint32Size;
            // write the GC id
            UnsignedTypes.longToBytes(GCId, bytes, offset);
            offset += UnsignedTypes.uint32Size;
            // write the command command or digest
            if (has_digest) {
                // size first
                UnsignedTypes.longToBytes(parameters.digestLength, bytes, offset);
                offset += UnsignedTypes.uint32Size;
                // write the digest
                byte[] tmp = digest.getBytes();
                for (int i = 0; i < tmp.length; i++, offset++) {
                    bytes[offset] = tmp[i];
                }
            } else {
                // size first
                UnsignedTypes.longToBytes(command.length, bytes, offset);
                offset += UnsignedTypes.uint32Size;
                // write the command
                for (int i = 0; i < command.length; i++, offset++) {
                    bytes[offset] = command[i];
                }
            }
            if (offset != getSize()) {
                BFT.Debug.kill("off by one in entry.getbytes()");
            }
        }
        return bytes;
    }


    Digest myDig;

    public boolean matches(Digest d) {
        return d.equals(getMyDigest());
    }

    public Digest getMyDigest() {
        if (digest != null) {
            BFT.Debug.kill("Should never get my digest if the digest already exists");
        }
        if (myDig == null) {
            myDig = new Digest(parameters, getBytes());
        }
        return myDig;
    }

    public int getSize() {
        return ((command != null) ? command.length : parameters.digestLength) + 5 * MessageTags.uint32Size + 1;
    }


    public boolean equals(Entry e) {
        boolean res = getClient() == e.getClient() && getRequestId() == e.getRequestId() && has_digest == e.has_digest;
        if (!has_digest && res) {
            for (int i = 0; res && i < command.length; i++) {
                res = res && getCommand()[i] == e.getCommand()[i];
            }
        } else if (res) {
            res = digest.equals(e.digest);
        }
        return res;
    }

    public String toString() {
        return clientId + ":" + subId + ":" + requestId;
    }

    public void setCommand(byte[] command) {
        this.command = command;
    }

//     long offset;
//     public void setOffset(long off){
// 	if (offset != -1)
// 	    BFT.Debug.kill("Cannot set an offset if one already exists");
// 	else
// 	    offset = off;
//     }

//     public long getOffset(){
// 	return offset;
//     }


//     public int compareTo(Entry o){
// 	if (getOffset() == o.getOffset())
// 	    return 0;
// 	else if (getOffset() < o.getOffset())
// 	    return -1;
// 	else 
// 	    return 1;
//     }

}
