/* 
 * TPCW_Database.java - Contains all of the code involved with database
 *                      accesses, including all of the JDBC calls. These
 *                      functions are called by many of the servlets.
 *
 ************************************************************************
 *
 * This is part of the the Java TPC-W distribution,
 * written by Harold Cain, Tim Heil, Milo Martin, Eric Weglarz, and Todd
 * Bezenek.  University of Wisconsin - Madison, Computer Sciences
 * Dept. and Dept. of Electrical and Computer Engineering, as a part of
 * Prof. Mikko Lipasti's Fall 1999 ECE 902 course.
 *
 * Copyright (C) 1999, 2000 by Harold Cain, Timothy Heil, Milo Martin, 
 *                             Eric Weglarz, Todd Bezenek.
 *
 * This source code is distributed "as is" in the hope that it will be
 * useful.  It comes with no warranty, and no author or distributor
 * accepts any responsibility for the consequences of its use.
 *
 * Everyone is granted permission to copy, modify and redistribute
 * this code under the following conditions:
 *
 * This code is distributed for non-commercial use only.
 * Please contact the maintainer for restrictions applying to 
 * commercial use of these tools.
 *
 * Permission is granted to anyone to make or distribute copies
 * of this code, either as received or modified, in any
 * medium, provided that all copyright notices, permission and
 * nonwarranty notices are preserved, and that the distributor
 * grants the recipient permission for further redistribution as
 * permitted by this document.
 *
 * Permission is granted to distribute this code in compiled
 * or executable form under the same conditions that apply for
 * source code, provided that either:
 *
 * A. it is accompanied by the corresponding machine-readable
 *    source code,
 * B. it is accompanied by a written offer, with no time limit,
 *    to give anyone a machine-readable copy of the corresponding
 *    source code in return for reimbursement of the cost of
 *    distribution.  This written offer must permit verbatim
 *    duplication by anyone, or
 * C. it is distributed by someone who received only the
 *    executable form, and is accompanied by a copy of the
 *    written offer of source code that they received concurrently.
 *
 * In other words, you are welcome to use, share and improve this codes.
 * You are forbidden to forbid anyone else to use, share and improve what
 * you give them.
 *
 ************************************************************************/
package Applications.tpcw_servlet;

import Applications.tpcw_servlet.message.TPCW_TransactionMessage;
import Applications.tpcw_servlet.message.TransactionMessageInterface;
import Applications.tpcw_servlet.message.TransactionMessageWrapper;
import Applications.tpcw_servlet.message.TransactionMessageWrapperInterface;
import Applications.tpcw_servlet.util.TPCWServletUtils;
import BFT.Debug;
import BFT.clientShim.ClientGlueInterface;
import BFT.clientShim.ClientShimBaseNode;
import BFT.exec.ExecBaseNode;
import BFT.generalcp.GeneralCP;
import BFT.network.PassThroughNetworkQueue;
import BFT.network.netty.NettyTCPReceiver;
import BFT.network.netty.NettyTCPSender;
import BFT.util.Role;
import merkle.wrapper.MTCollectionWrapper;

import java.io.*;
import java.sql.Date;
import java.util.Vector;

public class TPCW_Database {
    private transient static ClientShimBaseNode[] clientShims = null;
    private transient static ExecBaseNode ebn = null;
    private transient static GeneralCP generalCP = null;

    public static int noOfClient = -1;

    public static void createEveH2Connector(ClientGlueInterface cg, String clientMembershipFile, int noOfClient, int subid) {
        assert (TPCW_Database.clientShims == null);
        TPCW_Database.noOfClient = noOfClient;
        TPCW_Database.clientShims = new ClientShimBaseNode[noOfClient];
        for (int i = 0; i < noOfClient; i++) {
            ClientShimBaseNode currentClient = new ClientShimBaseNode(clientMembershipFile, i, subid);
            currentClient.setGlue(cg);
            NettyTCPSender sendNet = new NettyTCPSender(currentClient.getParameters(), currentClient.getMembership(), 1);
            currentClient.setNetwork(sendNet);

            Role[] roles = new Role[3];
            roles[0] = Role.VERIFIER;
            roles[1] = Role.EXEC;
            roles[2] = Role.FILTER;

            PassThroughNetworkQueue ptnq = new PassThroughNetworkQueue(currentClient);
            new NettyTCPReceiver(roles,
                    currentClient.getMembership(), ptnq, 1);
            currentClient.start();

            TPCW_Database.clientShims[i] = currentClient;
        }
    }

    public static void setExecBaseNode(ExecBaseNode ebn) {
        assert (TPCW_Database.ebn == null);
        TPCW_Database.ebn = ebn;
    }

    public static void setGeneralCP(GeneralCP generalCP) {
        TPCW_Database.generalCP = generalCP;
    }

    public static ExecBaseNode getExecBaseNode() {
        assert (TPCW_Database.ebn != null);
        return TPCW_Database.ebn;
    }

    private static byte[] sendEveRequest(Object request) {
        // This makes a nested request to the backend DB service
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        ObjectOutputStream oos;
        try {
            oos = new ObjectOutputStream(bos);
            oos.writeObject(request);
            oos.flush();
        } catch (IOException e) {
            e.printStackTrace();
        }

        byte[] byteRequest = bos.toByteArray();
        if(generalCP == null ) {
            byte[] reply = ebn.execNestedRequest(byteRequest, clientShims);
            return reply;
        }
        else if(ebn == null) {
            System.err.println("Using generalGP");
            return generalCP.execNestedRequest(byteRequest, clientShims);
        }
        else
        {
            Debug.kill(new Exception("Both generalCP and ebn cannot be null"));
            return null;
        }
    }

    private static ObjectInputStream getOIS(byte[] raw_data) {
        ByteArrayInputStream bis = new ByteArrayInputStream(raw_data);
        ObjectInputStream ois = null;
        try {
            ois = new ObjectInputStream(bis);
        } catch (IOException e) {
            e.printStackTrace();
        }

        assert (ois != null);
        return ois;
    }

    @SuppressWarnings("unchecked") // unchecked warning is suppressed due to template impl.
    private static <RET_T, PARA_T> RET_T makeQuery(String txnName, PARA_T parameter) {
        TransactionMessageWrapperInterface wrapper = new
                TransactionMessageWrapper(txnName, parameter);

        byte[] reply = sendEveRequest(wrapper);

        ObjectInputStream ois = getOIS(reply);

        RET_T result = null;
        try {
            result = (RET_T) ois.readObject();
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }

        //assert(result != null);
        if (result == null) {
            System.err.print("Got null as result, txnName = " + txnName);
        }

        return result;
    }

    public static void main(String[] args) {
        //Integer integer = new Integer(0x11111111);
        TransactionMessageInterface tmi = new TPCW_TransactionMessage();
        tmi.pushInt(123);
        tmi.pushString("12345");
        Vector<String> vec = new Vector<String>();
        vec.add("X");
        vec.add("Y");
        tmi.pushVectorString(vec);

        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        ObjectOutputStream oos;
        try {
            oos = new ObjectOutputStream(bos);
            oos.writeObject(tmi);
            oos.flush();
        } catch (IOException e) {
            e.printStackTrace();
        }

        byte[] byteData = bos.toByteArray();

        try {
            ByteArrayInputStream bis = new ByteArrayInputStream(byteData);
            ObjectInputStream ois = new ObjectInputStream(bis);
            //Integer recon_integer = (Integer) ois.readObject();
            tmi = (TransactionMessageInterface) ois.readObject();

            System.err.println(tmi.popSimpleObject(TPCWServletUtils.intMarker).toString());
            System.err.println(tmi.popSimpleObject(TPCWServletUtils.stringMarker));
            vec = tmi.popVector(TPCWServletUtils.stringVectorMarker);
            System.err.println("Len: " + vec.size());
            for (int i = 0; i < vec.size(); i++)
                System.err.print(vec.elementAt(i));
        } catch (Exception e) {
            e.printStackTrace();
        }


    }

    public static String[] getName(int c_id) {
        return makeQuery("getName", new Integer(c_id));
    }

    public static Book getBook(int i_id) {
        return makeQuery("getBook", new Integer(i_id));
    }

    public static Customer getCustomer(String UNAME) {
        Customer cust = makeQuery("getCustomer", UNAME);
        if (cust == null) {
            System.err.println(", UNAME = " + UNAME);
        }
        return cust;
    }

    public static Vector<Book> doSubjectSearch(String search_key) {
        return makeQuery("doSubjectSearch", search_key);
    }

    public static Vector<Book> doTitleSearch(String search_key) {
        return makeQuery("doTitleSearch", search_key);
    }

    public static Vector<Book> doAuthorSearch(String search_key) {
        return makeQuery("doAuthorSearch", search_key);
    }

    public static Vector<ShortBook> getNewProducts(String subject) {
        return makeQuery("getNewProducts", subject);
    }

    public static Vector<ShortBook> getBestSellers(String subject) {
        return makeQuery("getBestSellers", subject);
    }

    public static void getRelated(int i_id, MTCollectionWrapper<Integer> i_id_vec, MTCollectionWrapper<String> i_thumbnail_vec) {
        TransactionMessageInterface tm =
                makeQuery("getRelated", new Integer(i_id));

        i_id_vec.clear();
        i_thumbnail_vec.clear();

        Vector<Integer> new_i_id = tm.popVector(TPCWServletUtils.intVectorMarker);
        i_id_vec.addAll(new_i_id);

        Vector<String> new_i_thumbnail = tm.popVector(TPCWServletUtils.stringVectorMarker);
        i_thumbnail_vec.addAll(new_i_thumbnail);
    }

    public static void adminUpdate(int i_id, double cost, String image, String thumbnail) {
        TransactionMessageInterface requestMessage = new TPCW_TransactionMessage();

        requestMessage.pushInt(i_id);
        requestMessage.pushString(new Double(cost).toString());
        requestMessage.pushString(image);
        requestMessage.pushString(thumbnail);

        int res = makeQuery("adminUpdate", requestMessage);

        assert (res == 0);
    }

    public static String GetUserName(int C_ID) {
        return makeQuery("GetUserName", new Integer(C_ID));
    }

    public static String GetPassword(String C_UNAME) {
        return makeQuery("GetPassword", C_UNAME);
    }

    public static Order GetMostRecentOrder(String C_UNAME, MTCollectionWrapper<OrderLine> order_lines) {
        TransactionMessageInterface replyMessage =
                makeQuery("GetMostRecentOrder", C_UNAME);

        order_lines.clear();

        Vector<OrderLine> newOrders = replyMessage.popVector(TPCWServletUtils.order_line);
        order_lines.addAll(newOrders);

        return replyMessage.popSimpleObject(TPCWServletUtils.order);

    }

    // ********************** Shopping Cart code below *************************

    // Called from: TPCW_shopping_cart_interaction
    public static int createEmptyCart() {
        return makeQuery("createEmptyCart", new String(":P"));
    }

    public static Cart doCart(int SHOPPING_ID, Integer I_ID, Vector<String> ids, Vector<String> quantities) {
        TransactionMessageInterface requestMessage = new TPCW_TransactionMessage();

        requestMessage.pushInt(SHOPPING_ID);
        requestMessage.pushInt(I_ID);
        requestMessage.pushVectorString(ids);
        requestMessage.pushVectorString(quantities);

        return makeQuery("doCart", requestMessage);
    }

    public static Cart getCart(int SHOPPING_ID, double c_discount) {
        TransactionMessageInterface requestMessage = new TPCW_TransactionMessage();

        requestMessage.pushInt(SHOPPING_ID);
        requestMessage.pushDouble(c_discount);

        return makeQuery("getCart", requestMessage);
    }

    // ************** Customer / Order code below *************************

    //This should probably return an error code if the customer
    //doesn't exist, but ...
    public static void refreshSession(int C_ID) {
        int res = makeQuery("refreshSession", new Integer(C_ID));
        assert (res == 0);
    }

    public static Customer createNewCustomer(Customer cust) {
        return makeQuery("createNewCustomer", cust);
    }

    //BUY CONFIRM

    public static BuyConfirmResult doBuyConfirm(int shopping_id,
                                                int customer_id,
                                                String cc_type,
                                                long cc_number,
                                                String cc_name,
                                                Date cc_expiry,
                                                String shipping) {

        TransactionMessageInterface requestMessage = new TPCW_TransactionMessage();

        requestMessage.pushInt(shopping_id);
        requestMessage.pushInt(customer_id);
        requestMessage.pushString(cc_type);
        requestMessage.pushLong(cc_number);
        requestMessage.pushString(cc_name);
        requestMessage.pushLong(cc_expiry.getTime());
        requestMessage.pushString(shipping);

        return makeQuery("doBuyConfirm1", requestMessage);
    }

    public static BuyConfirmResult doBuyConfirm(int shopping_id,
                                                int customer_id,
                                                String cc_type,
                                                long cc_number,
                                                String cc_name,
                                                Date cc_expiry,
                                                String shipping,
                                                String street_1, String street_2,
                                                String city, String state,
                                                String zip, String country) {

        TransactionMessageInterface requestMessage = new TPCW_TransactionMessage();

        requestMessage.pushInt(shopping_id);
        requestMessage.pushInt(customer_id);
        requestMessage.pushString(cc_type);
        requestMessage.pushLong(cc_number);
        requestMessage.pushString(cc_name);
        requestMessage.pushLong(cc_expiry.getTime());
        requestMessage.pushString(shipping);
        requestMessage.pushString(street_1);
        requestMessage.pushString(street_2);
        requestMessage.pushString(city);
        requestMessage.pushString(state);
        requestMessage.pushString(zip);
        requestMessage.pushString(country);

        return makeQuery("doBuyConfirm2", requestMessage);
    }

    public static void verifyDBConsistency() {
        int res = makeQuery("verifyDBConsistency", new String(":P"));
        assert (res == 0);
    }
}

