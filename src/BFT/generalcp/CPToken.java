package BFT.generalcp;

import java.io.*;
import java.util.ArrayList;

public class CPToken {

    private ArrayList<StateToken> appCpTokens = new ArrayList<StateToken>();
    private ArrayList<StateToken> logTokens = new ArrayList<StateToken>();
    private long cpSeqNo = -1;

    public CPToken() {

    }


    public CPToken(ArrayList<StateToken> cpTokens, ArrayList<StateToken> logTokens) {
        if (cpTokens != null)
            this.appCpTokens = cpTokens;
        if (logTokens != null)
            this.logTokens = logTokens;
    }

    public void setCPSeqNo(long cpSeqNo) {
        this.cpSeqNo = cpSeqNo;
    }

    public long getCPSeqNo() {
        return this.cpSeqNo;
    }


    public long getLastSeqNo() {
        if (logTokens.size() == 0)
            return this.cpSeqNo;
        else
            return logTokens.get(logTokens.size() - 1).getSeqNo();
    }

    public long getCPFileSize() {
        StateToken lastCPToken = appCpTokens.get(appCpTokens.size() - 1);
        return lastCPToken.getOffset() + lastCPToken.getLength();
    }

    @Override
    public String toString() {
        StringBuilder str = new StringBuilder();
        str.append("cpSeqNo=" + cpSeqNo + " lastSeqNo=" + getLastSeqNo() + "\n");
        /*for (int i = 0; i < appCpTokens.size(); i++)
            str.append("appCpTokens[" + i + "]=" + appCpTokens.get(i).toString() + "\n");
        for (int i = 0; i < logTokens.size(); i++)
            str.append("logTokens[" + i + "]=" + logTokens.get(i).toString() + "\n");*/
        return str.toString();
    }

    public ArrayList<StateToken> getAppCPTokens() {
        return appCpTokens;
    }

    /*public ArrayList<StateToken> getLogTokens() {
        return logTokens;
    }*/

    public synchronized void addLogToken(StateToken token) {
        if (this.logTokens.size() == 0 || this.logTokens.get(logTokens.size() - 1).getSeqNo() < token.getSeqNo())
            this.logTokens.add(token);
    }

    public synchronized int getLogTokenSize() {
        return this.logTokens.size();
    }

    public synchronized StateToken getLogToken(int index) {
        return this.logTokens.get(index);
    }

    public void readBytes(InputStream in) throws IOException {
        DataInputStream dis = new DataInputStream(in);
        this.cpSeqNo = dis.readLong();
        int len = dis.readInt();
        appCpTokens = new ArrayList<StateToken>();
        for (int i = 0; i < len; i++) {
            StateToken tmp = new StateToken();
            tmp.readBytes(in);
            appCpTokens.add(tmp);
        }
        len = dis.readInt();
        logTokens = new ArrayList<StateToken>();
        for (int i = 0; i < len; i++) {
            StateToken tmp = new StateToken();
            tmp.readBytes(in);
            logTokens.add(tmp);
        }
    }


    public void writeBytes(OutputStream out) throws IOException {
        DataOutputStream dos = new DataOutputStream(out);
        dos.writeLong(this.cpSeqNo);
        dos.writeInt(appCpTokens.size());
        for (int i = 0; i < appCpTokens.size(); i++)
            appCpTokens.get(i).writeBytes(out);
        dos.writeInt(logTokens.size());
        for (int i = 0; i < logTokens.size(); i++)
            logTokens.get(i).writeBytes(out);

    }

    public byte[] getBytes() throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        this.writeBytes(bos);
        return bos.toByteArray();
    }

}
