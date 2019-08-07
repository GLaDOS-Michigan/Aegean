// $Id: Principal.java 199 2010-04-13 00:43:47Z manos $
package BFT.membership;

import BFT.Parameters;

import javax.crypto.Mac;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.security.interfaces.RSAPublicKey;

public class Principal {

    InetAddress ip;
    int port;
    String pubKeyString;
    private RSAPublicKey pubKey;
    Mac macKey;

    public Principal() {
    }   // a dummy object

    public Principal(InetAddress _ip, int _port, String _pubKey) {
        ip = _ip;
        port = _port;
        pubKeyString = _pubKey;
    }

    public Principal(InetAddress _ip, int _port, String _pubKey, Mac _macKey) {
        ip = _ip;
        port = _port;
        pubKeyString = _pubKey;
        macKey = _macKey;       // CAUTION: this constructor is not called yet, so macKey will be empty
    }


    /**
     * Creates a Principal from a IP:port string representation
     */
    public Principal(Parameters param, String s, String _pubKey) {

        try {
            String[] split = s.split(":", 2);
            ip = InetAddress.getByName(split[0]);
            port = Integer.parseInt(split[1]);
            pubKeyString = _pubKey;
            pubKey = BFT.util.KeyGen.getPubKeyFromString(param, pubKeyString);
        } catch (UnknownHostException e) {
            //System.out.println(s);
            e.printStackTrace();
        }
    }

    public InetAddress getIP() {
        return ip;
    }

    public int getPort() {
        return port;
    }

    public String getPubKeyString() {
        return pubKeyString;
    }

    public RSAPublicKey getPublicKey() {
        return pubKey;
    }

    public Mac getMacKey() {
        return macKey;
    }

    public void setMacKey(Mac mac) {
        this.macKey = mac;
    }
} 
