// $Id: ClientRequest.java 722 2011-07-04 09:24:35Z aclement $

package BFT.messages;

import BFT.Parameters;
import util.UnsignedTypes;


public class ClientRequest extends Request {

    public ClientRequest(Parameters param, long sender, RequestCore pay, boolean b) {
        this(param, sender, -1L, pay, b);
    }

    public ClientRequest(Parameters param, long sender, long subId, RequestCore pay, boolean b, Digest d, long id) {
        super(param, MessageTags.ClientRequest, sender, subId, pay, false, d, id);
    }


    public ClientRequest(Parameters param, long sender, long subId, RequestCore pay, boolean b) {
        super(param, MessageTags.ClientRequest, sender, subId, pay, false);
    }

    public ClientRequest(Parameters param, long sender, RequestCore pay) {
        this(param, sender, -1L, pay);
    }

    public ClientRequest(Parameters param, long sender, long subId, RequestCore pay) {
        super(param, MessageTags.ClientRequest, sender, subId, pay);
    }

    public ClientRequest(Parameters param, long sender, long subId, RequestCore pay, Digest d, long id) {
        super(param, MessageTags.ClientRequest, sender, subId, pay, d, id);
    }

    public ClientRequest(byte[] bytes, Parameters param) {
        super(bytes, param);
    }

    public static void main(String args[]) {
        byte[] tmp = new byte[8];
        for (int i = 0; i < 8; i++) {
            tmp[i] = (byte) i;
        }
        Parameters param = new Parameters();
        SignedRequestCore vmb = new SignedRequestCore(new Parameters(), 1, 0, 0, tmp);
        Request req = new ClientRequest(param, 0, vmb);
        //System.out.println("initial: "+req.toString());
        UnsignedTypes.printBytes(req.getBytes());

        Request req2 = new ClientRequest(req.getBytes(), param);
        //System.out.println("\nsecondary: "+req2.toString());
        UnsignedTypes.printBytes(req2.getBytes());

        //System.out.println("old.equals(new): "+req.equals(req2));
    }
}
