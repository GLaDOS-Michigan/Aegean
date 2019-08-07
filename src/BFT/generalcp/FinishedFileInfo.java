package BFT.generalcp;

public class FinishedFileInfo {
    public long seqNo;
    public String fileName;

    public FinishedFileInfo(long seqNo, String fileName) {
        this.seqNo = seqNo;
        this.fileName = fileName;
    }
}
