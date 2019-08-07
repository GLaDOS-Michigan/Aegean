package Applications.tpcw_new.request_player.remote;

import Applications.tpcw_new.request_player.ConnectionStatement;
import Applications.tpcw_new.request_player.DBStatements;
import Applications.tpcw_new.request_player.DataBase;
import Applications.tpcw_new.request_player.RequestPlayerUtils;
import Applications.tpcw_new.request_player.transaction_handler.TPCW_TransactionHandler;
import Applications.tpcw_servlet.*;
import Applications.tpcw_servlet.message.TPCW_TransactionMessage;
import Applications.tpcw_servlet.message.TransactionMessageInterface;
import Applications.tpcw_servlet.message.TransactionMessageWrapperInterface;
import Applications.tpcw_servlet.util.TPCWServletUtils;
import BFT.Debug;
import BFT.Parameters;
import BFT.exec.*;
import BFT.util.UnsignedTypes;
import merkle.MerkleTreeInstance;

import java.io.*;
import java.sql.Date;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Vector;

/**
 * @author lcosvse
 */

public class RealtimeTPCWServer implements RequestHandler, RequestFilter {
    protected static HashMap<String, ArrayList<RequestKey>> keys = null;
    protected static ConnectionStatement[] connections = null;
    protected DataBase dataBase = null;

    /* to access to the statements */
    protected DBStatements dbStatements = null;

    protected ReplyHandler replyHandler = null;
    protected int id;
    protected Parameters parameters = null;

    public RealtimeTPCWServer(Parameters parameters, ReplyHandler replyHandler, int id) {
        this.parameters = parameters;
        this.replyHandler = replyHandler;
        this.id = id;
        // initialize the statements
        dbStatements = (DBStatements) RequestPlayerUtils
                .loadClass(RequestPlayerUtils.DBStatements);

        // create DB
        dataBase = (DataBase) RequestPlayerUtils
                .loadClass(RequestPlayerUtils.DBClass);
        dataBase.initDB();
        //MerkleTreeInstance.get().finishThisVersion();
        //merkle.Tools.printHash(MerkleTreeInstance.get().getHash());
        // initialize the statements
        dbStatements = (DBStatements) RequestPlayerUtils
                .loadClass(RequestPlayerUtils.DBStatements);
        initRules();
    }

    private static void initRules() {
        keys = new HashMap<String, ArrayList<RequestKey>>();
        ArrayList<RequestKey> key = new ArrayList<RequestKey>();

        key.add(new RequestKey(true, "customer"));
        keys.put("getName", key);

        key = new ArrayList<RequestKey>();
        key.add(new RequestKey(true, "item"));
        key.add(new RequestKey(true, "author"));
        keys.put("getBook", key);

        key = new ArrayList<RequestKey>();
        key.add(new RequestKey(true, "customer"));
        key.add(new RequestKey(true, "address"));
        key.add(new RequestKey(true, "country"));
        keys.put("getCustomer", key);

        key = new ArrayList<RequestKey>();
        key.add(new RequestKey(true, "item"));
        key.add(new RequestKey(true, "author"));
        keys.put("doSubjectSearch", key);

        key = new ArrayList<RequestKey>();
        key.add(new RequestKey(true, "item"));
        key.add(new RequestKey(true, "author"));
        keys.put("doTitleSearch", key);

        key = new ArrayList<RequestKey>();
        key.add(new RequestKey(true, "item"));
        key.add(new RequestKey(true, "author"));
        keys.put("doAuthorSearch", key);

        key = new ArrayList<RequestKey>();
        key.add(new RequestKey(true, "item"));
        key.add(new RequestKey(true, "author"));
        keys.put("getNewProducts", key);

        key = new ArrayList<RequestKey>();
        key.add(new RequestKey(true, "item"));
        key.add(new RequestKey(true, "author"));
        key.add(new RequestKey(true, "order_line"));
        keys.put("getBestSellers", key);

        key = new ArrayList<RequestKey>();
        key.add(new RequestKey(true, "item"));
        keys.put("getRelated", key);

        key = new ArrayList<RequestKey>();
        key.add(new RequestKey(false, "item"));
        key.add(new RequestKey(true, "orders"));
        key.add(new RequestKey(true, "order_line"));
        keys.put("adminUpdate", key);

        key = new ArrayList<RequestKey>();
        key.add(new RequestKey(true, "customer"));
        keys.put("GetUserName", key);

        key = new ArrayList<RequestKey>();
        key.add(new RequestKey(true, "customer"));
        keys.put("GetPassword", key);

        key = new ArrayList<RequestKey>();
        key.add(new RequestKey(true, "customer"));
        key.add(new RequestKey(true, "orders"));
        key.add(new RequestKey(true, "cc_xacts"));
        key.add(new RequestKey(true, "address"));
        key.add(new RequestKey(true, "country"));
        key.add(new RequestKey(true, "order_line"));
        keys.put("GetMostRecentOrder", key);

        key = new ArrayList<RequestKey>();
        key.add(new RequestKey(false, "shopping_cart"));
        keys.put("createEmptyCart", key);

        key = new ArrayList<RequestKey>();
        key.add(new RequestKey(false, "shopping_cart_line"));
        key.add(new RequestKey(false, "shopping_cart"));
        key.add(new RequestKey(true, "item"));
        keys.put("doCart", key);

        key = new ArrayList<RequestKey>();
        key.add(new RequestKey(true, "shopping_cart_line"));
        key.add(new RequestKey(true, "item"));
        keys.put("getCart", key);

        key = new ArrayList<RequestKey>();
        key.add(new RequestKey(false, "customer"));
        keys.put("refreshSession", key);

        key = new ArrayList<RequestKey>();
        key.add(new RequestKey(false, "customer"));
        key.add(new RequestKey(true, "country"));
        key.add(new RequestKey(false, "address"));
        keys.put("createNewCustomer", key);

        key = new ArrayList<RequestKey>();
        key.add(new RequestKey(true, "customer"));
        key.add(new RequestKey(false, "item"));
        key.add(new RequestKey(true, "address"));
        key.add(new RequestKey(false, "shopping_cart_line"));
        key.add(new RequestKey(false, "orders"));
        key.add(new RequestKey(false, "order_line"));
        key.add(new RequestKey(false, "cc_xacts"));
        keys.put("doBuyConfirm1", key);

        key = new ArrayList<RequestKey>();
        key.add(new RequestKey(true, "customer"));
        key.add(new RequestKey(false, "item"));
        key.add(new RequestKey(true, "address"));
        key.add(new RequestKey(false, "shopping_cart_line"));
        key.add(new RequestKey(false, "orders"));
        key.add(new RequestKey(false, "order_line"));
        key.add(new RequestKey(false, "cc_xacts"));
        keys.put("doBuyConfirm2", key);

        key = new ArrayList<RequestKey>();
        key.add(new RequestKey(true, "customer"));
        key.add(new RequestKey(true, "item"));
        key.add(new RequestKey(true, "address"));
        keys.put("verifyDBConsistency", key);
    }

    private void createSession() {
        connections = new ConnectionStatement[this.parameters.getNumberOfClients()];
        for (int i = 0; i < connections.length; i++) {
            ConnectionStatement cs = new ConnectionStatement(dbStatements);
            cs.setRandomSeed(i);
            connections[i] = cs;
            System.err.println("created session for client " + i);
        }

        MerkleTreeInstance.get().getHash();
        System.err.println("Hash is get");
    }

    /*
     * main. Creates a new RequestPlayerServer for realtime TPCW benchmark.
     * Usage: RequestPlayerServer server_port pool_size
     */
    public static void main(String[] args) {
        if (args.length != 2) {
            System.out.println("Usage Server <membership> <id>");
            return;
        }

        RequestPlayerUtils.initProperties(null);
        ExecBaseNode exec = new ExecBaseNode(args[0], Integer.parseInt(args[1]));
        RealtimeTPCWServer main = new RealtimeTPCWServer(exec.getParameters(), exec, Integer.parseInt(args[1]));
        main.createSession();
        exec.start(main, main);
        System.err.println("DB is started");

    }

    protected void sendReply(Info info, Serializable data) {
        try {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            ObjectOutputStream oos = new ObjectOutputStream(bos);
            oos.writeObject(data);
            oos.flush();

            byte[] res = bos.toByteArray();
            Debug.debug(Debug.MODULE_EXEC, "Reply butes are: " + UnsignedTypes.bytesToHexString(res));
            this.replyHandler.result(res, (RequestInfo) info);
        } catch (Exception e) {
            Debug.debug(Debug.MODULE_EXEC, "lcosvse: failed sending reply!!");
            e.printStackTrace();
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    protected int handleTransaction(TransactionMessageWrapperInterface tmw, ConnectionStatement cs, Info info) {
        String txnType = tmw.getTransactionType();

        if (txnType.equals("getName")) {
            int c_id = (Integer) tmw.getMessageData();
            String[] res = TPCW_TransactionHandler.getName(cs, c_id);
            this.sendReply(info, res);
        } else if (txnType.equals("getBook")) {
            int i_id = (Integer) tmw.getMessageData();
            Book book = TPCW_TransactionHandler.getBook(cs, i_id);
            this.sendReply(info, book);
        } else if (txnType.equals("getCustomer")) {
            String UNAME = (String) tmw.getMessageData();
            assert (UNAME != null);
            Customer customer = TPCW_TransactionHandler.getCustomer(cs, UNAME);
            this.sendReply(info, customer);
        } else if (txnType.equals("doSubjectSearch")) {
            String search_key = (String) tmw.getMessageData();
            Vector<Book> books = TPCW_TransactionHandler.doSubjectSearch(cs, search_key);
            this.sendReply(info, books);
        } else if (txnType.equals("doTitleSearch")) {
            String search_key = (String) tmw.getMessageData();
            Vector<Book> books = TPCW_TransactionHandler.doTitleSearch(cs, search_key);
            this.sendReply(info, books);
        } else if (txnType.equals("doAuthorSearch")) {
            String search_key = (String) tmw.getMessageData();
            Vector<Book> books = TPCW_TransactionHandler.doAuthorSearch(cs, search_key);
            this.sendReply(info, books);
        } else if (txnType.equals("getNewProducts")) {
            String subject = (String) tmw.getMessageData();
            Vector<ShortBook> shortBooks = TPCW_TransactionHandler.getNewProducts(cs, subject);
            this.sendReply(info, shortBooks);
        } else if (txnType.equals("getBestSellers")) {
            String subject = (String) tmw.getMessageData();
            Vector<ShortBook> shortBooks = TPCW_TransactionHandler.getBestSellers(cs, subject);
            this.sendReply(info, shortBooks);
        } else if (txnType.equals("getRelated")) {
            int i_id = (Integer) tmw.getMessageData();
            Vector<Integer> i_id_vec = new Vector<Integer>();
            Vector<String> i_thumbnail_vec = new Vector<String>();

            TPCW_TransactionHandler.getRelated(
                    cs, i_id, i_id_vec, i_thumbnail_vec);

            TransactionMessageInterface tmi = new TPCW_TransactionMessage();
            tmi.pushVectorInt(i_id_vec);
            tmi.pushVectorString(i_thumbnail_vec);
            this.sendReply(info, tmi);
        } else if (txnType.equals("adminUpdate")) {
            TransactionMessageInterface tmi = (TransactionMessageInterface) tmw.getMessageData();

            int i_id = (Integer) tmi.popSimpleObject(TPCWServletUtils.intMarker);
            double cost = new Double((String) tmi.popSimpleObject(TPCWServletUtils.stringMarker)).doubleValue();
            String image = (String) tmi.popSimpleObject(TPCWServletUtils.stringMarker);
            String thumbnail = (String) tmi.popSimpleObject(TPCWServletUtils.stringMarker);

            TPCW_TransactionHandler.adminUpdate(cs, i_id, cost, image, thumbnail);
            this.sendReply(info, new Integer(0));
        } else if (txnType.equals("GetUserName")) {
            int C_ID = (Integer) tmw.getMessageData();
            String res = TPCW_TransactionHandler.GetUserName(cs, C_ID);
            this.sendReply(info, res);
        } else if (txnType.equals("GetPassword")) {
            String C_UNAME = (String) tmw.getMessageData();
            String res = TPCW_TransactionHandler.GetPassword(cs, C_UNAME);
            this.sendReply(info, res);
        } else if (txnType.equals("GetMostRecentOrder")) {
            String C_UNAME = (String) tmw.getMessageData();
            Vector<OrderLine> order_lines = new Vector<OrderLine>();
            Order order = TPCW_TransactionHandler.GetMostRecentOrder(cs, C_UNAME, order_lines);

            TransactionMessageInterface tmi = new TPCW_TransactionMessage();
            tmi.pushVector(TPCWServletUtils.order_line, order_lines);
            tmi.pushSimpleObject(TPCWServletUtils.order, order);
            this.sendReply(info, tmi);
        } else if (txnType.equals("createEmptyCart")) {
            int res = TPCW_TransactionHandler.createEmptyCart(cs);
            this.sendReply(info, new Integer(res));
        } else if (txnType.equals("doCart")) {
            TransactionMessageInterface tmi = (TransactionMessageInterface) tmw.getMessageData();

            int SHOPPING_ID = (Integer) tmi.popSimpleObject(TPCWServletUtils.intMarker);
            Integer I_ID = (Integer) tmi.popSimpleObject(TPCWServletUtils.intMarker);
            Vector<String> ids = (Vector) tmi.popVector(TPCWServletUtils.stringVectorMarker);
            Vector<String> quantities = (Vector) tmi.popVector(TPCWServletUtils.stringVectorMarker);

            Cart cart = TPCW_TransactionHandler.doCart(cs, SHOPPING_ID, I_ID, ids, quantities);
            this.sendReply(info, cart);
        } else if (txnType.equals("getCart")) {
            TransactionMessageInterface tmi = (TransactionMessageInterface) tmw.getMessageData();

            int SHOPPING_ID = (Integer) tmi.popSimpleObject(TPCWServletUtils.intMarker);
            double c_discount = (Double) tmi.popSimpleObject(TPCWServletUtils.doubleMarker);

            Cart cart = TPCW_TransactionHandler.getCart(cs, SHOPPING_ID, c_discount);
            this.sendReply(info, cart);
        } else if (txnType.equals("refreshSession")) {
            int C_ID = (Integer) tmw.getMessageData();
            TPCW_TransactionHandler.refreshSession(cs, C_ID);
            this.sendReply(info, new Integer(0));
        } else if (txnType.equals("createNewCustomer")) {
            Customer cust = (Customer) tmw.getMessageData();
            Customer customer = TPCW_TransactionHandler.createNewCustomer(cs, cust, info.getTime());
            this.sendReply(info, customer);
        } else if (txnType.equals("doBuyConfirm1")) {
            TransactionMessageInterface tmi = (TransactionMessageInterface) tmw.getMessageData();

            int shopping_id = (Integer) tmi.popSimpleObject(TPCWServletUtils.intMarker);
            int customer_id = (Integer) tmi.popSimpleObject(TPCWServletUtils.intMarker);
            String cc_type = (String) tmi.popSimpleObject(TPCWServletUtils.stringMarker);
            long cc_number = (Long) tmi.popSimpleObject(TPCWServletUtils.longMarker);
            String cc_name = (String) tmi.popSimpleObject(TPCWServletUtils.stringMarker);
            Date cc_expiry = new Date(((Long) tmi.popSimpleObject(TPCWServletUtils.longMarker)).longValue());
            String shipping = (String) tmi.popSimpleObject(TPCWServletUtils.stringMarker);

            BuyConfirmResult result = TPCW_TransactionHandler.doBuyConfirm(cs, shopping_id, customer_id,
                    cc_type, cc_number, cc_name, cc_expiry, shipping);

            this.sendReply(info, result);
        } else if (txnType.equals("doBuyConfirm2")) {
            TransactionMessageInterface tmi = (TransactionMessageInterface) tmw.getMessageData();

            int shopping_id = (Integer) tmi.popSimpleObject(TPCWServletUtils.intMarker);
            int customer_id = (Integer) tmi.popSimpleObject(TPCWServletUtils.intMarker);
            String cc_type = (String) tmi.popSimpleObject(TPCWServletUtils.stringMarker);
            long cc_number = (Long) tmi.popSimpleObject(TPCWServletUtils.longMarker);
            String cc_name = (String) tmi.popSimpleObject(TPCWServletUtils.stringMarker);
            Date cc_expiry = new Date(((Long) tmi.popSimpleObject(TPCWServletUtils.longMarker)).longValue());
            String shipping = (String) tmi.popSimpleObject(TPCWServletUtils.stringMarker);
            String street_1 = (String) tmi.popSimpleObject(TPCWServletUtils.stringMarker);
            String street_2 = (String) tmi.popSimpleObject(TPCWServletUtils.stringMarker);
            String city = (String) tmi.popSimpleObject(TPCWServletUtils.stringMarker);
            String state = (String) tmi.popSimpleObject(TPCWServletUtils.stringMarker);
            String zip = (String) tmi.popSimpleObject(TPCWServletUtils.stringMarker);
            String country = (String) tmi.popSimpleObject(TPCWServletUtils.stringMarker);

            BuyConfirmResult result = TPCW_TransactionHandler.doBuyConfirm(cs, shopping_id, customer_id,
                    cc_type, cc_number, cc_name, cc_expiry, shipping,
                    street_1, street_2, city, state, zip, country);

            this.sendReply(info, result);

        } else if (txnType.equals("verifyDBConsistency")) {
            TPCW_TransactionHandler.verifyDBConsistency(cs);
            this.sendReply(info, new Integer(0));
        } else {
            throw new RuntimeException("Unknown Transaction.");
        }

        return 0;
    }

    @Override
    public void execRequest(byte[] request, RequestInfo info) {
        exec(request, info);
    }

    public void exec(byte[] request, Info info) {
        try {
            ByteArrayInputStream bis = new ByteArrayInputStream(request);
            ObjectInputStream ois = new ObjectInputStream(bis);
            TransactionMessageWrapperInterface tmw = (TransactionMessageWrapperInterface) ois.readObject();
            ConnectionStatement cs = connections[info.getClientId()];

            int res = handleTransaction(tmw, cs, info);
            assert (res == 0);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void execReadOnly(byte[] request, RequestInfo info) {
        throw new RuntimeException("Method not implemented.");
    }

    @Override
    public List<RequestKey> generateKeys(byte[] request) {
        try {
            ByteArrayInputStream bis = new ByteArrayInputStream(request);
            ObjectInputStream ois = new ObjectInputStream(bis);
            TransactionMessageWrapperInterface tm = (TransactionMessageWrapperInterface) ois.readObject();

            String txnType = tm.getTransactionType();
            List<RequestKey> ret = keys.get(txnType);
            if (ret == null) {
                System.err.println("OOOOOOps: txnType = " + txnType);
            }
            assert (ret != null);
            return ret;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }
}
