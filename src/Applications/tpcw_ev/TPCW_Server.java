package Applications.tpcw_ev;

import Applications.tpcw_ev.messages.*;
import BFT.exec.*;
import merkle.MerkleTreeInstance;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.sql.Connection;
import java.sql.Date;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

enum RequestType {

    INIT, GET_BEST_SELLERS, GET_BOOKS_BY_SUBJECT, GET_BOOKS_BY_TITLE, GET_BOOKS_BY_AUTHOR, NEW_PRODUCTS_BY_SUBJECT, GET_RELATED_BOOKS, CREATE_CART, FILL_CART, GET_CUSTOMER, GET_MOST_RECENT_ORDER, DO_BUY_CONFIRM
};

public class TPCW_Server implements RequestHandler, RequestFilter {

    public final static boolean TRACE = false;
    private ReplyHandler replyHandler;
    private int id;
    private static ConcurrentHashMap<Thread, Connection> connections = new ConcurrentHashMap<Thread, Connection>();
    private final static String URL = "jdbc:h2:mem:testServer;MULTI_THREADED=1;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000;LOCK_MODE=3";
    private final static String USER = "sa";
    private final static String PASSWORD = "sa";
    private static HashMap<Integer, ArrayList<RequestKey>> keys = new HashMap<Integer, ArrayList<RequestKey>>();
    private static String[] shopping_cart_names;
    private static String[] shopping_cart_line_names;

    private static int objId = 100;

    public static class Test {
    }

    private static void test() {
    /*Test tmp=new Test();
	//System.out.println(tmp.getMTFlag());
	tmp.setMTFlag(1);
	//tmp.setMTID(objId++);
	//System.out.println(tmp.getMTFlag());
	//Object []refs = tmp.getChildReferences();
	//System.out.println("refs.length="+refs.length);
	//for(int i=0;i<refs.length;i++)
	//    System.out.println(refs[i].getClass());
        MerkleTreeInstance.add(tmp);
	//System.out.println(tmp.getMTID());
	//System.out.println(tmp);
	//System.out.println(MerkleTreeInstance.get().getObject(tmp.getMTID()));
        tmp = null;*/

    }

    public TPCW_Server(ReplyHandler replyHandler, int id) {
        this.id = id;
        this.replyHandler = replyHandler;
        MerkleTreeInstance.add(URL);
        org.h2.Driver.load();
        try {
            Connection con = DriverManager.getConnection(URL, USER, PASSWORD);
            con.setAutoCommit(false);
            TPCW_Populate.populate(con, 10);
            TPCW_Database.split = 10;
            System.gc();
            //MerkleTreeInstance.get().getHash();
            System.out.println("TPCW_Server started");
            //System.out.println("testDelete");
            //TPCW_Populate.testDelete();
	    /*for(int i=0;i<10;i++){
	        test();
		System.gc();
	    }*/
        } catch (Exception e) {
            e.printStackTrace();
            System.exit(-1);
        }

        shopping_cart_names = new String[TPCW_Database.split];
        shopping_cart_line_names = new String[TPCW_Database.split];
        for (int i = 0; i < TPCW_Database.split; i++) {
            shopping_cart_names[i] = "shopping_cart" + i;
            shopping_cart_line_names[i] = "shopping_cart_line" + i;
        }
        //build rules.
        ArrayList<RequestKey> key = new ArrayList<RequestKey>();
        key.add(new RequestKey(true, "customer"));
        keys.put(MessageTags.getName, key);
        key = new ArrayList<RequestKey>();
        key.add(new RequestKey(true, "item"));
        key.add(new RequestKey(true, "author"));
        keys.put(MessageTags.getBook, key);
        key = new ArrayList<RequestKey>();
        key.add(new RequestKey(true, "item"));
        key.add(new RequestKey(true, "address"));
        key.add(new RequestKey(true, "country"));
        keys.put(MessageTags.getCustomer, key);
        key = new ArrayList<RequestKey>();
        key.add(new RequestKey(true, "item"));
        key.add(new RequestKey(true, "author"));
        keys.put(MessageTags.doSubjectSearch, key);
        key = new ArrayList<RequestKey>();
        key.add(new RequestKey(true, "item"));
        key.add(new RequestKey(true, "author"));
        keys.put(MessageTags.doTitleSearch, key);
        key = new ArrayList<RequestKey>();
        key.add(new RequestKey(true, "item"));
        key.add(new RequestKey(true, "author"));
        keys.put(MessageTags.doAuthorSearch, key);
        key = new ArrayList<RequestKey>();
        key.add(new RequestKey(true, "item"));
        key.add(new RequestKey(true, "author"));
        keys.put(MessageTags.getNewProducts, key);
        key = new ArrayList<RequestKey>();
        key.add(new RequestKey(true, "item"));
        key.add(new RequestKey(true, "author"));
        key.add(new RequestKey(true, "order_line"));
        keys.put(MessageTags.getBestSellers, key);
        key = new ArrayList<RequestKey>();
        key.add(new RequestKey(true, "item"));
        keys.put(MessageTags.getRelated, key);
        key = new ArrayList<RequestKey>();
        key.add(new RequestKey(false, "item"));
        keys.put(MessageTags.adminUpdate, key);
        key = new ArrayList<RequestKey>();
        key.add(new RequestKey(true, "customer"));
        keys.put(MessageTags.getUserName, key);
        key = new ArrayList<RequestKey>();
        key.add(new RequestKey(true, "customer"));
        keys.put(MessageTags.getPassword, key);
        key = new ArrayList<RequestKey>();
        key.add(new RequestKey(true, "customer"));
        key.add(new RequestKey(true, "cc_xacts"));
        key.add(new RequestKey(true, "address"));
        key.add(new RequestKey(true, "country"));
        key.add(new RequestKey(true, "order_line"));
        keys.put(MessageTags.getMostRecentOrder, key);
        key = new ArrayList<RequestKey>();
        key.add(new RequestKey(false, "customer"));
        keys.put(MessageTags.refreshSession, key);
        key = new ArrayList<RequestKey>();
        key.add(new RequestKey(false, "customer"));
        key.add(new RequestKey(false, "address"));
        key.add(new RequestKey(true, "country"));
        key.add(new RequestKey(true, "address"));
        keys.put(MessageTags.createNewCustomer, key);
        key = new ArrayList<RequestKey>();
        key.add(new RequestKey(true, "item"));
        keys.put(MessageTags.getStock, key);
        key = new ArrayList<RequestKey>();
        key.add(new RequestKey(true, "item"));
        key.add(new RequestKey(true, "customer"));
        key.add(new RequestKey(true, "address"));
        keys.put(MessageTags.verifyDBConsistency, key);
        key = new ArrayList<RequestKey>();
        for (int i = 0; i < TPCW_Database.split; i++) {
            key.add(new RequestKey(false, "shopping_cart" + i));
        }
        keys.put(MessageTags.createEmptyCart, key);
    }

    public void execRequest(byte[] request, RequestInfo info) {
        Connection con = connections.get(Thread.currentThread());
        if (con == null) {
            try {
                con = DriverManager.getConnection(URL, USER, PASSWORD);
                con.setAutoCommit(false);
            } catch (Exception e) {
                e.printStackTrace();
                return;
            }
            connections.put(Thread.currentThread(), con);
        }
        //MerkleTreeInstance.getShimInstance().setRequestID(info);
        Random r = new Random(info.getRandom());
        Object ret = execute(request, con, r);
        DatabaseReply reply = new DatabaseReply(ret);
        replyHandler.result(reply.getBytes(), info);
    }

    public void execReadOnly(byte[] request, RequestInfo info) {
        throw new RuntimeException("Not implemented yet");
    }

    public List<RequestKey> generateKeys(byte[] request) {
        try {
            ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(request));
            int tag = ois.readInt();
            ArrayList<RequestKey> ret = new ArrayList<RequestKey>();
            //System.out.println("Generating keys for "+tag);
            switch (tag) {
                //build rules.
                case MessageTags.getName:
                case MessageTags.getBook:
                case MessageTags.getCustomer:
                case MessageTags.doSubjectSearch:
                case MessageTags.doTitleSearch:
                case MessageTags.doAuthorSearch:
                case MessageTags.getNewProducts:
                case MessageTags.getBestSellers:
                case MessageTags.getRelated:
                case MessageTags.adminUpdate:
                case MessageTags.getUserName:
                case MessageTags.getPassword:
                case MessageTags.getMostRecentOrder:
                case MessageTags.refreshSession:
                case MessageTags.createNewCustomer:
                case MessageTags.getStock:
                case MessageTags.verifyDBConsistency:
                case MessageTags.createEmptyCart:
                    return keys.get(tag);
                case MessageTags.doCart:
                    DoCartRequest req1 = new DoCartRequest(request);
                    int id = TPCW_Database.getIdFromShoppingId(req1.shoppind_id);
                    ret.add(new RequestKey(false, shopping_cart_line_names[id]));
                    ret.add(new RequestKey(false, shopping_cart_names[id]));
                    ret.add(new RequestKey(true, "item"));
                    return ret;
                case MessageTags.addItem:
                    AddItemRequest req2 = new AddItemRequest(request);
                    id = TPCW_Database.getIdFromShoppingId(req2.shopping_id);
                    ret.add(new RequestKey(false, shopping_cart_line_names[id]));
                    return ret;
                case MessageTags.getCart:
                    GetCartRequest req3 = new GetCartRequest(request);
                    id = TPCW_Database.getIdFromShoppingId(req3.shopping_id);
                    ret.add(new RequestKey(true, shopping_cart_line_names[id]));
                    ret.add(new RequestKey(true, "item"));
                    return ret;
                case MessageTags.doBuyConfirm:
                    DoBuyConfirmRequest req4 = new DoBuyConfirmRequest(request);
                    id = TPCW_Database.getIdFromShoppingId(req4.shopping_id);
                    ret.add(new RequestKey(true, "customer"));
                    ret.add(new RequestKey(true, "address"));
                    ret.add(new RequestKey(false, "order_line"));
                    ret.add(new RequestKey(false, "item"));
                    ret.add(new RequestKey(false, "cc_xacts"));
                    ret.add(new RequestKey(false, shopping_cart_line_names[id]));
                    return ret;
                case MessageTags.doBuyConfirm2:
                    DoBuyConfirmRequest2 req5 = new DoBuyConfirmRequest2(request);
                    id = TPCW_Database.getIdFromShoppingId(req5.shopping_id);
                    ret.add(new RequestKey(true, "customer"));
                    ret.add(new RequestKey(true, "address"));
                    ret.add(new RequestKey(false, "order_line"));
                    ret.add(new RequestKey(false, "item"));
                    ret.add(new RequestKey(false, "cc_xacts"));
                    ret.add(new RequestKey(false, shopping_cart_line_names[id]));
                    return ret;
                case MessageTags.clearCart:
                    ClearCartRequest req6 = new ClearCartRequest(request);
                    id = TPCW_Database.getIdFromShoppingId(req6.shopping_id);
                    ret.add(new RequestKey(false, shopping_cart_line_names[id]));
                    return ret;
                default:
                    throw new RuntimeException("Unexpected tag " + tag);
            }
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }

    }

    private Object execute(byte[] req, Connection con, Random r) {
        // System.out.println("Client #" + id + ": execute called " +
        // nbExecutions
        // + " times");
        DatabaseRequest request = DatabaseRequest.readRequest(req);
        try {
            switch (request.getTag()) {
                case Applications.tpcw_ev.messages.MessageTags.getBestSellers:
                    return doGetBestSellers((GetBestSellersRequest) request, con);
                case Applications.tpcw_ev.messages.MessageTags.doSubjectSearch:
                    return doGetBooksBySubject((DoSubjectSearchRequest) request, con);
                case Applications.tpcw_ev.messages.MessageTags.doTitleSearch:
                    return doGetBooksByTitle((DoTitleSearchRequest) request, con);
                case Applications.tpcw_ev.messages.MessageTags.doAuthorSearch:
                    return doGetBooksByAuthor((DoAuthorSearchRequest) request, con);
                case Applications.tpcw_ev.messages.MessageTags.getNewProducts:
                    return doGetNewProductsBySubject((GetNewProductsRequest) request, con);
                case Applications.tpcw_ev.messages.MessageTags.getRelated:
                    return doGetRelatedBooks((GetRelatedRequest) request, con);
                case Applications.tpcw_ev.messages.MessageTags.createEmptyCart:
                    return doCreateCart((CreateEmptyCartRequest) request, con, r);
                case Applications.tpcw_ev.messages.MessageTags.doCart:
                    return doFillCart((DoCartRequest) request, con, r);
                case Applications.tpcw_ev.messages.MessageTags.getCustomer:
                    return doGetCustomer((GetCustomerRequest) request, con);
                case Applications.tpcw_ev.messages.MessageTags.getMostRecentOrder:
                    return doGetMostRecentOrder((GetMostRecentOrderRequest) request, con);
                case Applications.tpcw_ev.messages.MessageTags.doBuyConfirm:
                    return doDoBuyConfirm((DoBuyConfirmRequest) request, con, r);
                default:
                    System.err.println("Unknown request " + request.getTag());
                    return null;
            }
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    /*public void doInit(Connection con) throws Exception {
    // Reset state
    bookId = -1;
    shoppingId = -1;
    customer = null;

    // Choose next state
    int p = r.nextInt(100);
    // 20%
    if (p < 20) {
    state = RequestType.GET_BEST_SELLERS;
    } // 20%
    else if (p < 40) {
    state = RequestType.GET_BOOKS_BY_SUBJECT;
    } // 20%
    else if (p < 60) {
    state = RequestType.GET_BOOKS_BY_TITLE;
    } // 20%
    else if (p < 80) {
    state = RequestType.GET_BOOKS_BY_AUTHOR;
    } // 20%
    else if (p < 100) {
    state = RequestType.NEW_PRODUCTS_BY_SUBJECT;
    }

    // Execute next state
    execute(con);
    }*/
    private Vector<ShortBook> doGetNewProductsBySubject(GetNewProductsRequest request, Connection con) throws Exception {
        //int subjectIndex = r.nextInt(TPCW_Populate.NUM_SUBJECTS);
        //String subject = TPCW_Populate.SUBJECTS[subjectIndex];

        if (TRACE) {
            System.out.println("Retrieving new products for subject = "
                    + request.subject);
        }
        Vector<ShortBook> newProductsBySubject = null;
        int nbRetry = 100;
        while (nbRetry > 0) {
            try {
                newProductsBySubject = TPCW_Database.getNewProducts(request.subject,
                        con);
            } catch (SQLException e) {
                // System.err
                // .println("doGetNewProductsBySubject raised an exception");
                // e.printStackTrace();
                con.rollback();
                nbRetry--;
                if (nbRetry <= 0) {
                    throw new Exception(
                            "doGetNewProductsBySubject failed 10 times");
                }
                continue;
            }
            break;
        }
        if (TRACE) {
            System.out.println("New product query on subject " + request.subject
                    + " was successful (returned "
                    + newProductsBySubject.size() + " products)");
        }

        return newProductsBySubject;
        //bookId = newProductsBySubject.get(r.nextInt(newProductsBySubject.size())).i_id;

        //state = RequestType.GET_RELATED_BOOKS;
    }

    private Vector<Book> doGetBooksByTitle(DoTitleSearchRequest request, Connection con) throws Exception {
        //String title = TPCW_Populate.titles.get(r.nextInt(TPCW_Populate.titles.size()));
        //int titleOffset = Math.max(0, (r.nextInt(title.length()) - 4));
        //title = title.substring(titleOffset, titleOffset + 3);
        //title = "%" + title + "%";

        if (TRACE) {
            System.out.println("Retrieving books with title " + request.search_key);
        }
        Vector<Book> booksByTitle = null;
        int nbRetry = 100;
        while (nbRetry > 0) {
            try {
                booksByTitle = TPCW_Database.doTitleSearch(request.search_key, con);
            } catch (SQLException e) {
                // System.err.println("doGetBooksByTitle raised an exception");
                // e.printStackTrace();
                con.rollback();
                nbRetry--;
                if (nbRetry <= 0) {
                    throw new Exception("doGetBooksByTitle failed 10 times");
                }
                continue;
            }
            break;
        }
        if (TRACE) {
            System.out.println("Book query with title " + request.search_key
                    + " was successful (returned " + booksByTitle.size()
                    + " books)");
        }

        return booksByTitle;
        //bookId = booksByTitle.get(r.nextInt(booksByTitle.size())).i_id;

        //state = RequestType.GET_RELATED_BOOKS;
    }

    private Vector<Book> doGetBooksByAuthor(DoAuthorSearchRequest request, Connection con) throws Exception {
        //String author = TPCW_Populate.authors.get(r.nextInt(TPCW_Populate.authors.size()));
        //author = author.substring(0, author.length() - 1);
        //author = "%" + author + "%";

        if (TRACE) {
            System.out.println("Retrieving books with author " + request.search_key);
        }
        Vector<Book> booksByAuthor = null;
        int nbRetry = 100;
        while (nbRetry > 0) {
            try {
                booksByAuthor = TPCW_Database.doAuthorSearch(request.search_key, con);
            } catch (SQLException e) {
                // System.err.println("doGetBooksByAuthor raised an exception");
                // e.printStackTrace();
                con.rollback();
                nbRetry--;
                if (nbRetry <= 0) {
                    throw new Exception("doGetBooksByAuthor failed 10 times");
                }
                continue;
            }
            break;
        }
        if (TRACE) {
            System.out.println("Book query with author " + request.search_key
                    + " was successful (returned " + booksByAuthor.size()
                    + " books)");
        }

        return booksByAuthor;
        //bookId = booksByAuthor.get(r.nextInt(booksByAuthor.size())).i_id;

        //state = RequestType.GET_RELATED_BOOKS;
    }

    private Vector<Book> doGetBooksBySubject(DoSubjectSearchRequest request, Connection con) throws Exception {
        //int subjectIndex = r.nextInt(TPCW_Populate.NUM_SUBJECTS);
        //String subject = TPCW_Populate.SUBJECTS[subjectIndex];

        if (TRACE) {
            System.out.println("Retrieving books for subject = " + request.search_key);
        }
        Vector<Book> booksBySubject = null;
        int nbRetry = 100;
        while (nbRetry > 0) {
            try {
                booksBySubject = TPCW_Database.doSubjectSearch(request.search_key, con);

            } catch (SQLException e) {
                // System.err.println("doGetBooksBySubject raised an exception");
                // e.printStackTrace();
                con.rollback();
                nbRetry--;
                if (nbRetry <= 0) {
                    throw new Exception("doGetBooksBySubject failed 10 times");
                }
                continue;
            }
            break;
        }
        if (TRACE) {
            System.out.println("Book query on subject " + request.search_key
                    + " was successful (returned " + booksBySubject.size()
                    + " books)");
        }

        return booksBySubject;
        //bookId = booksBySubject.get(r.nextInt(booksBySubject.size())).i_id;

        //state = RequestType.GET_RELATED_BOOKS;
    }

    private Vector<ShortBook> doGetBestSellers(GetBestSellersRequest request, Connection con) throws Exception {
        //int subjectIndex = r.nextInt(TPCW_Populate.NUM_SUBJECTS);
        //String subject = TPCW_Populate.SUBJECTS[subjectIndex];

        if (TRACE) {
            System.out.println("Retrieving best sellers for subject = "
                    + request.subject);
        }

        Vector<ShortBook> bestSellers = null;

        int nbRetry = 100;
        while (nbRetry > 0) {
            try {
                bestSellers = TPCW_Database.getBestSellers(request.subject, con);

            } catch (SQLException e) {
                // System.err.println("doGetBestSellers raised an exception");
                // e.printStackTrace();
                con.rollback();
                nbRetry--;
                if (nbRetry <= 0) {
                    throw new Exception("doGetBestSellers failed 10 times");
                }
                continue;
            }
            break;
        }

        if (TRACE) {
            System.out.println("Best seller query on " + request.subject
                    + " was successful (returned " + bestSellers.size()
                    + " books)");
        }

        if (bestSellers.size() == 0) {
            System.err.println("BEST_SELLER SIZE = 0");
        }

        //int bookId = bestSellers.get(r.nextInt(bestSellers.size())).i_id;
        return bestSellers;

    }

    private Vector<Integer> doGetRelatedBooks(GetRelatedRequest request, Connection con) throws Exception {
        Vector<Integer> i_id_vec = new Vector<Integer>();
        Vector<String> i_thumbnail_vec = new Vector<String>();
        if (TRACE) {
            System.out.println("Retrieving related books for book_id " + request.i_id);
        }
        int nbRetry = 100;
        while (nbRetry > 0) {
            try {
                TPCW_Database.getRelated(request.i_id, i_id_vec, i_thumbnail_vec,
                        con);

            } catch (SQLException e) {
                // System.err.println("doGetRelatedBooks raised an exception");
                // e.printStackTrace();
                con.rollback();
                nbRetry--;
                if (nbRetry <= 0) {
                    throw new Exception("doGetRelatedBooks failed 10 times");
                }
                continue;
            }
            break;
        }
        if (TRACE) {
            System.out.println("Related books query for book_id " + request.i_id
                    + " was successful (returned " + i_id_vec.size()
                    + " books)");
        }
        return i_id_vec;
        // Either the user creates a Cart and buy the books it saw
        // Or it goes back to INIT state

        /*int p = r.nextInt(100);
        // 90%
        if (p < 90) {
        state = RequestType.CREATE_CART;
        } // 90%
        else if (p < 100) {
        state = RequestType.INIT;
        }*/
    }

    private int doCreateCart(CreateEmptyCartRequest request, Connection con, Random r) throws Exception {
        int shoppingId = -1;
        if (TRACE) {
            System.out.println("Creating empty cart");
        }
        int nbRetry = 100;
        while (nbRetry > 0) {
            try {
                shoppingId = TPCW_Database.createEmptyCart(con, r);

            } catch (SQLException e) {
                // System.err.println("doCreateCart raised an exception");
                // e.printStackTrace();
                con.rollback();
                nbRetry--;
                if (nbRetry <= 0) {
                    throw new Exception("doCreateCart failed 10 times");
                }
                continue;
            }
            break;
        }
        if (TRACE) {
            System.out.println("Empty cart created with shopping id = "
                    + shoppingId);
        }
        return shoppingId;
        //state = RequestType.FILL_CART;
    }

    private Cart doFillCart(DoCartRequest request, Connection con, Random r) throws Exception {
        if (TRACE) {
            System.out.println("Filling cart");
        }
        /*Vector<Integer> ids = new Vector<Integer>();
        Vector<Integer> quantities = new Vector<Integer>();
        int p = r.nextInt(100);
        // proba 50% to add another item
        if (p < 50) {
        ids.add(bookId);
        quantities.add(1);
        }*/
        Cart cart = null;
        int nbRetry = 100;
        while (nbRetry > 0) {
            try {
                cart = TPCW_Database.doCart(request.shoppind_id, null, request.ids,
                        request.quantities, con, r);

            } catch (SQLException e) {
                // System.err.println("doFillCart raised an exception");
                // e.printStackTrace();
                con.rollback();
                nbRetry--;
                if (nbRetry <= 0) {
                    throw new Exception("doFillCart failed 10 times");
                }
                continue;
            }
            break;
        }
        if (TRACE) {
            System.out.println("Cart with shopping id = " + request.shoppind_id
                    + " filled");
        }
        return cart;
        //state = RequestType.GET_CUSTOMER;
    }

    private Customer doGetCustomer(GetCustomerRequest request, Connection con) throws Exception {
        if (TRACE) {
            System.out.println("Getting customer");
        }
        //int customerIndex = r.nextInt(TPCW_Populate.NUM_CUSTOMERS);
        //String customerName = TPCW_Populate.customers.get(customerIndex);
        int nbRetry = 100;
        Customer customer = null;

        while (nbRetry > 0) {
            try {
                customer = TPCW_Database.getCustomer(request.uname, con);
            } catch (SQLException e) {
                // System.err.println("doGetCustomer raised an exception");
                // e.printStackTrace();
                con.rollback();
                nbRetry--;
                if (nbRetry <= 0) {
                    throw new Exception("doGetCustomer failed 10 times");
                }
                continue;
            }
            break;
        }
        if (TRACE) {
            System.out.println("Getting customer");
        }
        return customer;
        /*int p = r.nextInt(100);
        // 10%
        if (p < 10) {
        state = RequestType.GET_MOST_RECENT_ORDER;
        }
        state = RequestType.DO_BUY_CONFIRM;*/
    }

    private Order doGetMostRecentOrder(GetMostRecentOrderRequest request, Connection con) throws Exception {
        if (TRACE) {
            System.out.println("Getting most recent order for customer "
                    + request.c_uname);
        }
        Vector<OrderLine> order_lines = new Vector<OrderLine>();
        Order order = null;

        int nbRetry = 100;
        while (nbRetry > 0) {
            try {
                order = TPCW_Database.GetMostRecentOrder(request.c_uname,
                        order_lines, con);
            } catch (SQLException e) {
                // System.err.println("doGetMostRecentOrder raised an exception");
                // e.printStackTrace();
                con.rollback();
                nbRetry--;
                if (nbRetry <= 0) {
                    throw new Exception("doGetMostRecentOrder failed 10 times");
                }
                continue;
            }
            break;
        }
        if (TRACE) {
            if (order != null) {
                System.out.println("Most recent order found");
            } else {
                System.out.println("Most recent order NOT found");
            }
        }
        return order;
        //state = RequestType.DO_BUY_CONFIRM;
    }

    private boolean doDoBuyConfirm(DoBuyConfirmRequest request, Connection con, Random r) throws Exception {
        if (TRACE) {
            System.out.println("doBuyConfirm for customer_id = "
                    + request.customer_id + " shoppingID = " + request.shopping_id);
        }

        //String cc_type = TPCW_Populate.credit_cards[r.nextInt(TPCW_Populate.num_card_types)];

        int nbRetry = 100;
        while (nbRetry > 0) {
            try {
                TPCW_Database.doBuyConfirm(request.shopping_id, request.customer_id, request.cc_type, /* cc_number */
                        12345, "cc_name", /* cc_expiry */ new Date(2010, 04, 27),
                        "shipping", con, r);
            } catch (SQLException e) {
                // System.err.println("doDoBuyConfirm raised an exception");
                // e.printStackTrace();
                con.rollback();
                nbRetry--;
                if (nbRetry <= 0) {
                    throw new Exception("doDoBuyConfirm failed 10 times");
                }
                continue;
            }
            break;
        }
        return true;
        // state = RequestType.INIT;
    }

    /*public boolean isNextARead() {
    switch (state) {
    case INIT:
    return true;
    case GET_BEST_SELLERS:
    return true;
    case GET_BOOKS_BY_SUBJECT:
    return true;
    case GET_BOOKS_BY_TITLE:
    return true;
    case GET_BOOKS_BY_AUTHOR:
    return true;
    case NEW_PRODUCTS_BY_SUBJECT:
    return true;
    case GET_RELATED_BOOKS:
    return true;
    case CREATE_CART:
    return false;
    case FILL_CART:
    return false;
    case GET_CUSTOMER:
    return true;
    case GET_MOST_RECENT_ORDER:
    return true;
    case DO_BUY_CONFIRM:
    return false;
    default:
    System.err.println("Unknown state");
    System.exit(-1);
    }
    return false;
    }*/
    public static void main(String args[]) throws Exception {
        if (args.length != 2) {
            System.out.println("Usage TPCW_Server <membership> <id>");
            return;
        }
        ExecBaseNode exec = new ExecBaseNode(args[0], Integer.parseInt(args[1]));
        TPCW_Server main = new TPCW_Server(exec, Integer.parseInt(args[1]));

/*	byte [][]haha=new byte[1000][];
	for(int i=0;i<1000;i++){
	    haha[i]=new byte[1048576];
	     System.gc();
	}*/
        System.gc();
        exec.start(main, main);
        System.out.println("Exec started");
        System.gc();
    }
}
