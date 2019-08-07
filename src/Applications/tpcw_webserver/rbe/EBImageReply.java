package Applications.tpcw_webserver.rbe;

class EBImageReply {
    public EBImageReply(int dataSize, byte[] imageData, int bytesRead) {
        this.bytesRead = bytesRead;
        this.imageData = new byte[dataSize];
        for (int i = 0; i < Math.min(this.imageData.length, imageData.length); i++)
            this.imageData[i] = imageData[i];
    }

    public byte[] imageData;
    public int bytesRead;
}
