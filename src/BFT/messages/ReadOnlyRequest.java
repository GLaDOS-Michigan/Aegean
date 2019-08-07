// $Id: ReadOnlyRequest.java 722 2011-07-04 09:24:35Z aclement $

package BFT.messages;

import BFT.Parameters;
import util.UnsignedTypes;


public class ReadOnlyRequest extends Request {

    public ReadOnlyRequest(Parameters param, long sender, RequestCore pay) {
        super(param, MessageTags.ReadOnlyRequest, sender, pay, true);
    }

    public ReadOnlyRequest(byte[] bytes, Parameters param) {
        super(bytes, param);
    }

    public static void main(String args[]) {
        byte[] tmp = new byte[8];
        for (int i = 0; i < 8; i++) {
            tmp[i] = (byte) i;
        }
        Parameters param = new Parameters();
        RequestCore vmb = new SignedRequestCore(param, 1, 0, 0, tmp);
        Request req = new ReadOnlyRequest(new Parameters(), 0, vmb);
        //System.out.println("initial: "+req.toString());
        UnsignedTypes.printBytes(req.getBytes());

        Request req2 = new ReadOnlyRequest(req.getBytes(), param);
        //System.out.println("\nsecondary: "+req2.toString());
        UnsignedTypes.printBytes(req2.getBytes());

        //System.out.println("old.equals(new): "+req.equals(req2));
    }
}