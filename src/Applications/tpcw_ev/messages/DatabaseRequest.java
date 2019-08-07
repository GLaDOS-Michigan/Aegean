/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package Applications.tpcw_ev.messages;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

/**
 * @author manos
 */
public abstract class DatabaseRequest {

    int tag;

    public DatabaseRequest() {
    }

    public DatabaseRequest(int tag) {
        this.tag = tag;
    }

    public int getTag() {
        return tag;
    }

    abstract public byte[] getBytes();
    
    /*public DatabaseRequest(byte[] b) {
        try{
            ByteArrayInputStream bis=new ByteArrayInputStream(b);
            ObjectInputStream ois=new ObjectInputStream(bis);
            obj = ois.readObject();
        } catch(Exception e){
            e.printStackTrace();
        }
    }

    public byte[] getBytes() {
        try{
            ByteArrayOutputStream bos=new ByteArrayOutputStream();
            ObjectOutputStream oos=new ObjectOutputStream(bos);
            oos.writeObject(obj);
            oos.flush();
            return bos.toByteArray();
        } catch(Exception e){
            e.printStackTrace();
        }
        return null;
    }*/

    public void writeString(ObjectOutputStream oos, String str) throws IOException {
        oos.writeInt(str.length());
        oos.write(str.getBytes());
    }

    public String readString(ObjectInputStream ois) throws IOException {
        int length = ois.readInt();
        byte[] data = new byte[length];
        ois.read(data, 0, length);
        return new String(data);
    }

    public static DatabaseRequest readRequest(byte[] req) {
        try {
            ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(req));
            int tag = ois.readInt();
            switch (tag) {
                case MessageTags.getName:
                    return new GetNameRequest(req);
                case MessageTags.getBook:
                    return new GetBookRequest(req);
                case MessageTags.getCustomer:
                    return new GetCustomerRequest(req);
                case MessageTags.doSubjectSearch:
                    return new DoSubjectSearchRequest(req);
                case MessageTags.doTitleSearch:
                    return new DoTitleSearchRequest(req);
                case MessageTags.doAuthorSearch:
                    return new DoAuthorSearchRequest(req);
                case MessageTags.getNewProducts:
                    return new GetNewProductsRequest(req);
                case MessageTags.getBestSellers:
                    return new GetBestSellersRequest(req);
                case MessageTags.getRelated:
                    return new GetRelatedRequest(req);
                case MessageTags.adminUpdate:
                    return new AdminUpdateRequest(req);
                case MessageTags.getUserName:
                    return new GetUserNameRequest(req);
                case MessageTags.getPassword:
                    return new GetPasswordRequest(req);
                case MessageTags.getMostRecentOrder:
                    return new GetMostRecentOrderRequest(req);
                case MessageTags.createEmptyCart:
                    return new CreateEmptyCartRequest(req);
                case MessageTags.doCart:
                    return new DoCartRequest(req);
                case MessageTags.addItem:
                    return new AddItemRequest(req);
                case MessageTags.getCart:
                    return new GetCartRequest(req);
                case MessageTags.refreshSession:
                    return new RefreshSessionRequest(req);
                case MessageTags.createNewCustomer:
                    return new CreateNewCustomerRequest(req);
                case MessageTags.doBuyConfirm:
                    return new DoBuyConfirmRequest(req);
                case MessageTags.doBuyConfirm2:
                    return new DoBuyConfirmRequest2(req);
                case MessageTags.clearCart:
                    return new ClearCartRequest(req);
                case MessageTags.getStock:
                    return new GetStockRequest(req);
                case MessageTags.verifyDBConsistency:
                    return new VerifyDBConsistencyRequest(req);
                default:
                    throw new RuntimeException("Unexpected tag " + tag);
            }
        } catch (IOException e) {
            return null;
        }

    }

}
