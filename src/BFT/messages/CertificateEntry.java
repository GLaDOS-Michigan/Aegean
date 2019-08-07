// $Id: CertificateEntry.java 728 2011-09-11 23:44:18Z aclement $

package BFT.messages;

import BFT.Parameters;
import util.UnsignedTypes;

public class CertificateEntry {
    protected HistoryDigest hd;
    protected CommandBatch commands;
    protected NonDeterminism nondet;
    protected byte[] bytes;
    protected Parameters parameters;

    public CertificateEntry(Parameters param, HistoryDigest h, CommandBatch com,
                            NonDeterminism nd, Digest cp) {
        hd = h;
        commands = com;
        nondet = nd;
        cphash = cp;
        bytes = null;
        parameters = param;
    }

    public String toString() {
        String out = "==============\n\t history: " + hd + "\n";
        out = out + "\t command: " + commands + "\n";
        out = out + "\t nondet : " + nondet + "\n";
        out = out + "\t cphash: " + cphash;
        out = out + "==============";
        return out;
    }

    public CertificateEntry(byte[] bytes, int offset, Parameters param) {
        parameters = param;
        byte[] tmp;

        // history digest
        tmp = new byte[parameters.digestLength];
        for (int i = 0; i < tmp.length; i++, offset++) {
            tmp[i] = bytes[offset];
        }
        hd = HistoryDigest.fromBytes(tmp, param);
        // cphash
        tmp = new byte[parameters.digestLength];
        for (int i = 0; i < tmp.length; i++, offset++) {
            tmp[i] = bytes[offset];
        }
        cphash = Digest.fromBytes(parameters, tmp);
        // nondet size
        int sz = util.UnsignedTypes.bytesToInt(bytes, offset);
        offset += util.UnsignedTypes.uint16Size;
        // nondet
        tmp = new byte[sz];
        for (int i = 0; i < tmp.length; i++, offset++) {
            tmp[i] = bytes[offset];
        }
        nondet = new NonDeterminism(tmp);

        // commandbatchsize in bytes
        sz = (int) util.UnsignedTypes.bytesToLong(bytes, offset);
        offset += util.UnsignedTypes.uint32Size;

        // entries in commandbatch
        int count = util.UnsignedTypes.bytesToInt(bytes, offset);
        offset += util.UnsignedTypes.uint16Size;

        // command batch itself
        tmp = new byte[sz];
        for (int i = 0; i < tmp.length; i++, offset++) {
            tmp[i] = bytes[offset];
        }
        commands = new CommandBatch(tmp, count, param);
    }

    public HistoryDigest getHistoryDigest() {
        return hd;
    }

    public CommandBatch getCommandBatch() {
        return commands;
    }

    public NonDeterminism getNonDeterminism() {
        return nondet;
    }

    Digest cphash;

    public Digest getCPHash() {
        return cphash;
    }

    Digest dig = null;

    public Digest getDigest() {
        if (dig == null) {
            dig = new Digest(parameters, getBytes());
        }
        return dig;
    }

    public boolean equals(CertificateEntry e) {
        return e != null && getDigest().equals(e.getDigest());
    }

    public int getSize() {
        return getBytes().length;
    }

    public byte[] getBytes() {
        if (bytes == null) {
            bytes = new byte[parameters.digestLength + // HistoryDigest.size()
                    parameters.digestLength +    // Digest.size()
                    nondet.getSize() +
                    commands.getSize() +
                    BFT.messages.MessageTags.uint16Size * 2 +
                    BFT.messages.MessageTags.uint32Size];

            int offset = 0;
            // write down the history digest
            byte[] tmp = hd.getBytes();
            for (int i = 0; i < parameters.digestLength; i++, offset++) {
                bytes[offset] = tmp[i];
            }
            // writedown the cphash
            tmp = cphash.getBytes();
            for (int i = 0; i < parameters.digestLength; i++, offset++) {
                bytes[offset] = tmp[i];
            }

            // write down the nondet size in bytes
            util.UnsignedTypes.intToBytes(nondet.getSize(), bytes, offset);
            offset += UnsignedTypes.uint16Size;

            // write downt the nondeterminism
            tmp = nondet.getBytes();
            for (int i = 0; i < tmp.length; i++, offset++) {
                bytes[offset] = tmp[i];
            }

            // write down the command size in bytes
            util.UnsignedTypes.longToBytes(commands.getSize(), bytes, offset);
            offset += UnsignedTypes.uint32Size;

            // write down the command entries
            util.UnsignedTypes.intToBytes(commands.getEntryCount(), bytes, offset);
            offset += UnsignedTypes.uint16Size;
            // write down the commands
            tmp = commands.getBytes();
            for (int i = 0; i < tmp.length; i++, offset++) {
                bytes[offset] = tmp[i];
            }
            if (bytes.length != offset) {
                throw new RuntimeException("offset construction is flawed");
            }
        }
        return bytes;
    }

}