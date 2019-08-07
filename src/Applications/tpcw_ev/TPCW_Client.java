/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package Applications.tpcw_ev;

import Applications.tpcw_ev.messages.*;
import BFT.clientShim.ClientShimBaseNode;
import BFT.network.PassThroughNetworkQueue;
import BFT.network.netty.NettyTCPReceiver;
import BFT.network.netty.NettyTCPSender;
import BFT.util.Role;

import java.sql.Connection;
import java.sql.Date;
import java.sql.DriverManager;
import java.util.Random;
import java.util.Vector;

/**
 * @author yangwang
 */
public class TPCW_Client {

    private ClientShimBaseNode clientShim;
    int bookId;
    int shoppingId;
    Customer customer;
    Random r;
    State state = State.INIT;
    int client_id;
    int req_id = 0;
    private final static String URL = "jdbc:h2:mem:testServer;MULTI_THREADED=1;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000;LOCK_MODE=3";
    private final static String USER = "sa";
    private final static String PASSWORD = "sa";

    public TPCW_Client(String membership, int id) {
        client_id = id;
        r = new Random(id);
        try {
            //BFT.Parameters.useDummyTree = true;
            org.h2.Driver.load();
            Connection con = DriverManager.getConnection(URL, USER, PASSWORD);
            con.setAutoCommit(false);
            TPCW_Populate.serverPopulate = false;
            TPCW_Populate.populate(con, 10);
        } catch (Exception e) {
            e.printStackTrace();
            System.exit(-1);
        }

        clientShim = new ClientShimBaseNode(membership, id);
        clientShim.getParameters().useDummyTree = true;
        NettyTCPSender sendNet = new NettyTCPSender(clientShim.getParameters(), clientShim.getMembership(), 1);
        clientShim.setNetwork(sendNet);

        Role[] roles = new Role[3];
        roles[0] = Role.ORDER;
        roles[1] = Role.EXEC;
        roles[2] = Role.FILTER;

        PassThroughNetworkQueue ptnq = new PassThroughNetworkQueue(clientShim);
        NettyTCPReceiver receiveNet = new NettyTCPReceiver(roles,
                clientShim.getMembership(), ptnq, 1);

        clientShim.start();
    }

    public void doInit() throws Exception {
        // Reset state
        bookId = -1;
        shoppingId = -1;
        customer = null;

        // Choose next state
        int p = r.nextInt(100);
        // 20%
        if (p < 20) {
            state = State.GET_BEST_SELLERS;
        } // 20%
        else if (p < 40) {
            state = State.GET_BOOKS_BY_SUBJECT;
        } // 20%
        else if (p < 60) {
            state = State.GET_BOOKS_BY_TITLE;
        } // 20%
        else if (p < 80) {
            state = State.GET_BOOKS_BY_AUTHOR;
        } // 20%
        else if (p < 100) {
            state = State.NEW_PRODUCTS_BY_SUBJECT;
        }

    }

    long startTime;
    long endTime;

    public void getBestSellers() {
        startTime = System.currentTimeMillis();
        int subjectIndex = r.nextInt(TPCW_Populate.NUM_SUBJECTS);
        String subject = TPCW_Populate.SUBJECTS[subjectIndex];
        GetBestSellersRequest request = new GetBestSellersRequest(subject);
        byte[] reply = clientShim.execute(request.getBytes());
        DatabaseReply dReply = new DatabaseReply(reply);
        Vector<ShortBook> bestSellers = (Vector<ShortBook>) dReply.obj;
        bookId = bestSellers.get(r.nextInt(bestSellers.size())).i_id;
        endTime = System.currentTimeMillis();
        System.err.println("#req" + req_id + " " + startTime + " " + endTime + " " + client_id);
        req_id++;
    }

    public void getBooksBySubject() {
        startTime = System.currentTimeMillis();
        int subjectIndex = r.nextInt(TPCW_Populate.NUM_SUBJECTS);
        String subject = TPCW_Populate.SUBJECTS[subjectIndex];

        Vector<Book> booksBySubject = null;
        DoSubjectSearchRequest request = new DoSubjectSearchRequest(subject);
        byte[] reply = clientShim.execute(request.getBytes());
        DatabaseReply dReply = new DatabaseReply(reply);
        booksBySubject = (Vector<Book>) dReply.obj;
        bookId = booksBySubject.get(r.nextInt(booksBySubject.size())).i_id;
        endTime = System.currentTimeMillis();
        System.err.println("#req" + req_id + " " + startTime + " " + endTime + " " + client_id);
        req_id++;
    }

    public void getBooksByTitle() throws Exception {
        startTime = System.currentTimeMillis();
        String title = TPCW_Populate.titles.get(r.nextInt(TPCW_Populate.titles.size()));
        int titleOffset = Math.max(0, (r.nextInt(title.length()) - 4));
        title = title.substring(titleOffset, titleOffset + 3);
        title = "%" + title + "%";

        Vector<Book> booksByTitle = null;
        DoTitleSearchRequest request = new DoTitleSearchRequest(title);
        byte[] reply = clientShim.execute(request.getBytes());
        DatabaseReply dReply = new DatabaseReply(reply);
        booksByTitle = (Vector<Book>) dReply.obj;
        bookId = booksByTitle.get(r.nextInt(booksByTitle.size())).i_id;
        endTime = System.currentTimeMillis();
        System.err.println("#req" + req_id + " " + startTime + " " + endTime + " " + client_id);
        req_id++;
    }

    public void getBooksByAuthor() throws Exception {
        startTime = System.currentTimeMillis();
        String author = TPCW_Populate.authors.get(r.nextInt(TPCW_Populate.authors.size()));
        author = author.substring(0, author.length() - 1);
        author = "%" + author + "%";

        Vector<Book> booksByAuthor = null;
        DoAuthorSearchRequest request = new DoAuthorSearchRequest(author);
        byte[] reply = clientShim.execute(request.getBytes());
        DatabaseReply dReply = new DatabaseReply(reply);
        booksByAuthor = (Vector<Book>) dReply.obj;
        bookId = booksByAuthor.get(r.nextInt(booksByAuthor.size())).i_id;
        endTime = System.currentTimeMillis();
        System.err.println("#req" + req_id + " " + startTime + " " + endTime + " " + client_id);
        req_id++;
    }

    public void getNewProductsBySubject() throws Exception {
        startTime = System.currentTimeMillis();
        int subjectIndex = r.nextInt(TPCW_Populate.NUM_SUBJECTS);
        String subject = TPCW_Populate.SUBJECTS[subjectIndex];

        Vector<ShortBook> newProductsBySubject = null;
        GetNewProductsRequest request = new GetNewProductsRequest(subject);
        byte[] reply = clientShim.execute(request.getBytes());
        DatabaseReply dReply = new DatabaseReply(reply);
        newProductsBySubject = (Vector<ShortBook>) dReply.obj;

        bookId = newProductsBySubject.get(r.nextInt(newProductsBySubject.size())).i_id;
        endTime = System.currentTimeMillis();
        System.err.println("#req" + req_id + " " + startTime + " " + endTime + " " + client_id);
        req_id++;
    }

    public void getRelatedBooks() throws Exception {
        startTime = System.currentTimeMillis();
        Vector<Integer> i_id_vec = new Vector<Integer>();
        Vector<String> i_thumbnail_vec = new Vector<String>();
        GetRelatedRequest request = new GetRelatedRequest(bookId, i_id_vec, i_thumbnail_vec);
        byte[] reply = clientShim.execute(request.getBytes());
        //DatabaseReply dReply = new DatabaseReply(reply);
        endTime = System.currentTimeMillis();
        System.err.println("#req" + req_id + " " + startTime + " " + endTime + " " + client_id);
        req_id++;

    }

    private void createCart() throws Exception {
        startTime = System.currentTimeMillis();
        CreateEmptyCartRequest request = new CreateEmptyCartRequest();
        byte[] reply = clientShim.execute(request.getBytes());
        DatabaseReply dReply = new DatabaseReply(reply);
        shoppingId = (Integer) dReply.obj;
        endTime = System.currentTimeMillis();
        System.err.println("#req" + req_id + " " + startTime + " " + endTime + " " + client_id);
        req_id++;
    }

    private void fillCart() throws Exception {
        startTime = System.currentTimeMillis();
        Vector<Integer> ids = new Vector<Integer>();
        Vector<Integer> quantities = new Vector<Integer>();
        int p = r.nextInt(100);
        // proba 50% to add another item
        if (p < 50) {
            ids.add(bookId);
            quantities.add(1);
        }

        DoCartRequest request = new DoCartRequest(shoppingId, ids, quantities);
        byte[] reply = clientShim.execute(request.getBytes());
        DatabaseReply dReply = new DatabaseReply(reply);
        endTime = System.currentTimeMillis();
        System.err.println("#req" + req_id + " " + startTime + " " + endTime + " " + client_id);
        req_id++;
    }

    private void getCustomer() throws Exception {
        startTime = System.currentTimeMillis();
        int customerIndex = r.nextInt(TPCW_Populate.NUM_CUSTOMERS);
        String customerName = TPCW_Populate.customers.get(customerIndex);

        GetCustomerRequest request = new GetCustomerRequest(customerName);
        byte[] reply = clientShim.execute(request.getBytes());
        DatabaseReply dReply = new DatabaseReply(reply);
        customer = (Customer) dReply.obj;
        endTime = System.currentTimeMillis();
        System.err.println("#req" + req_id + " " + startTime + " " + endTime + " " + client_id);
        req_id++;
    }

    private void getMostRecentOrder() throws Exception {
        startTime = System.currentTimeMillis();
        Vector<OrderLine> order_lines = new Vector<OrderLine>();
        GetMostRecentOrderRequest request = new GetMostRecentOrderRequest(customer.c_uname, order_lines);
        byte[] reply = clientShim.execute(request.getBytes());
        DatabaseReply dReply = new DatabaseReply(reply);
        Order order = (Order) dReply.obj;
        endTime = System.currentTimeMillis();
        System.err.println("#req" + req_id + " " + startTime + " " + endTime + " " + client_id);
        req_id++;

    }

    private void doDoBuyConfirm() throws Exception {
        startTime = System.currentTimeMillis();
        String cc_type = TPCW_Populate.credit_cards[r.nextInt(TPCW_Populate.num_card_types)];

        DoBuyConfirmRequest request = new DoBuyConfirmRequest(shoppingId, customer.c_id, cc_type, 12345, "cc_name", new Date(2010, 4, 27), "shipping");
        byte[] reply = clientShim.execute(request.getBytes());
        DatabaseReply dReply = new DatabaseReply(reply);
        boolean result = (Boolean) dReply.obj;
        //if (result) {
        //    System.out.println("Order confirmed");


        endTime = System.currentTimeMillis();
        System.err.println("#req" + req_id + " " + startTime + " " + endTime + " " + client_id);
        req_id++;
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 3) {
            System.out.println("Usage TPCW_Client <membership> <id> <round>");
        }
        String membership = args[0];
        int id = Integer.parseInt(args[1]);
        int round = Integer.parseInt(args[2]);

        TPCW_Client client = new TPCW_Client(membership, id);

        while (client.req_id < round) {
            switch (client.state) {
                case INIT:
                    client.doInit();
                    break;
                case GET_BEST_SELLERS:
                    client.getBestSellers();
                    client.state = State.GET_RELATED_BOOKS;
                    break;
                case GET_BOOKS_BY_SUBJECT:
                    client.getBooksBySubject();
                    client.state = State.GET_RELATED_BOOKS;
                    break;
                case GET_BOOKS_BY_TITLE:
                    client.getBooksByTitle();
                    client.state = State.GET_RELATED_BOOKS;
                    break;
                case GET_BOOKS_BY_AUTHOR:
                    client.getBooksByAuthor();
                    client.state = State.GET_RELATED_BOOKS;
                    break;
                case NEW_PRODUCTS_BY_SUBJECT:
                    client.getNewProductsBySubject();
                    client.state = State.GET_RELATED_BOOKS;
                    break;
                case GET_RELATED_BOOKS:
                    client.getRelatedBooks();
                    int p = client.r.nextInt(100);
                    // 90%
                    if (p < 50) {
                        client.state = State.CREATE_CART;
                    } // 90%
                    else if (p < 100) {
                        client.state = State.INIT;
                    }
                    break;
                case CREATE_CART:
                    client.createCart();
                    client.state = State.FILL_CART;
                    break;
                case FILL_CART:
                    client.fillCart();
                    client.state = State.GET_CUSTOMER;
                    break;
                case GET_CUSTOMER:
                    client.getCustomer();
                    client.state = State.DO_BUY_CONFIRM;
                    break;
                case DO_BUY_CONFIRM:
                    client.doDoBuyConfirm();
                    client.state = State.INIT;
                    break;
            }

        }
        System.out.println("Completed");
        System.exit(0);


    }
}
