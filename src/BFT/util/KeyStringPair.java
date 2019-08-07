package BFT.util;

public class KeyStringPair {

    private Pair<String, String> core;

    public KeyStringPair(String pubString, String privString) {
        core = new Pair<String, String>(pubString, privString);
    }

    public String getPubString() {
        if (core == null) throw new RuntimeException("Uninitialzed core");
        return core.getLeft();
    }

    public String getPrivString() {
        if (core == null) throw new RuntimeException("Uninitialzed core");
        return core.getRight();
    }

}
