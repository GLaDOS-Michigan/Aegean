package tpcw;

import BFT.exec.*;

import java.sql.Connection;
import java.sql.Date;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.List;
import java.util.Random;
import java.util.Vector;
import java.util.concurrent.ConcurrentHashMap;

enum RequestType {
    INIT, GET_BEST_SELLERS, GET_BOOKS_BY_SUBJECT, GET_BOOKS_BY_TITLE, GET_BOOKS_BY_AUTHOR, NEW_PRODUCTS_BY_SUBJECT, GET_RELATED_BOOKS, CREATE_CART, FILL_CART, GET_CUSTOMER, GET_MOST_RECENT_ORDER, DO_BUY_CONFIRM
};

public class TPCW_Server implements RequestHandler, RequestFilter {
    private final static String URL = "jdbc:h2:mem:testServer;MULTI_THREADED=1;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000;LOCK_MODE=3";
    private final static String USER = "sa";
    private final static String PASSWORD = "sa";
    public final static boolean TRACE = false;

    Random r = new Random();
    private ReplyHandler replyHandler;
    int id;
    RequestType state;
    int bookId;
    int shoppingId;
    Customer customer;
    int TPCW_id;

    private static ConcurrentHashMap<Thread, Connection> connections = new ConcurrentHashMap<Thread, Connection>();

    public TPCW_Server(int id, int nbTPCWs) {
        this.id = id;
        this.TPCW_id = id % nbTPCWs;
        r = new Random(id);
    }

    public void execRequest(byte[] request, RequestInfo info) {
        Connection conn = connections.get(Thread.currentThread());
        if (conn == null) {
            try {
                conn = DriverManager.getConnection(URL, USER,
                        PASSWORD);
            } catch (Exception e) {
                e.printStackTrace();
                return;
            }
            connections.put(Thread.currentThread(), conn);
        }
        //ByteArrayInputStream bis = new ByteArrayInputStream(request);
        RequestType type = RequestType.values()[0];
        replyHandler.result(new byte[1], info);
    }

    public void execReadOnly(byte[] request, RequestInfo info) {
        throw new RuntimeException("Not implemented yet");
    }

    public List<RequestKey> generateKeys(byte[] request) {
        return null;

    }

    public void execute(Connection con) throws Exception {
        // System.out.println("Client #" + id + ": execute called " +
        // nbExecutions
        // + " times");
        // nbExecutions++;
        switch (state) {
            case INIT:
                doInit(con);
                break;
            case GET_BEST_SELLERS:
                doGetBestSellers(con);
                break;
            case GET_BOOKS_BY_SUBJECT:
                doGetBooksBySubject(con);
                break;
            case GET_BOOKS_BY_TITLE:
                doGetBooksByTitle(con);
                break;
            case GET_BOOKS_BY_AUTHOR:
                doGetBooksByAuthor(con);
                break;
            case NEW_PRODUCTS_BY_SUBJECT:
                doGetNewProductsBySubject(con);
                break;
            case GET_RELATED_BOOKS:
                doGetRelatedBooks(con);
                break;
            case CREATE_CART:
                doCreateCart(con);
                break;
            case FILL_CART:
                doFillCart(con);
                break;
            case GET_CUSTOMER:
                doGetCustomer(con);
                break;
            case GET_MOST_RECENT_ORDER:
                doGetMostRecentOrder(con);
                break;
            case DO_BUY_CONFIRM:
                doDoBuyConfirm(con);
                break;
            default:
                System.err.println("Unknown state");
                break;
        }
    }

    public void doInit(Connection con) throws Exception {
        // Reset state
        bookId = -1;
        shoppingId = -1;
        customer = null;

        // Choose next state
        int p = r.nextInt(100);
        // 20%
        if (p < 20) {
            state = RequestType.GET_BEST_SELLERS;
        }
        // 20%
        else if (p < 40) {
            state = RequestType.GET_BOOKS_BY_SUBJECT;
        }
        // 20%
        else if (p < 60) {
            state = RequestType.GET_BOOKS_BY_TITLE;
        }
        // 20%
        else if (p < 80) {
            state = RequestType.GET_BOOKS_BY_AUTHOR;
        }
        // 20%
        else if (p < 100) {
            state = RequestType.NEW_PRODUCTS_BY_SUBJECT;
        }

        // Execute next state
        execute(con);
    }

    private void doGetNewProductsBySubject(Connection con) throws Exception {
        int subjectIndex = r.nextInt(TPCW_Populate.NUM_SUBJECTS);
        String subject = TPCW_Populate.SUBJECTS[subjectIndex];

        if (TRACE) {
            System.out.println("Retrieving new products for subject = "
                    + subject);
        }
        Vector<ShortBook> newProductsBySubject = null;
        int nbRetry = 100;
        while (nbRetry > 0) {
            try {
                newProductsBySubject = TPCW_Database.getNewProducts(subject,
                        con, TPCW_id);
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
            System.out.println("New product query on subject " + subject
                    + " was successful (returned "
                    + newProductsBySubject.size() + " products)");
        }

        bookId = newProductsBySubject.get(r
                .nextInt(newProductsBySubject.size())).i_id;

        state = RequestType.GET_RELATED_BOOKS;
    }

    private void doGetBooksByTitle(Connection con) throws Exception {
        String title = TPCW_Populate.titles.get(r.nextInt(TPCW_Populate.titles
                .size()));
        int titleOffset = Math.max(0, (r.nextInt(title.length()) - 4));
        title = title.substring(titleOffset, titleOffset + 3);
        title = "%" + title + "%";

        if (TRACE) {
            System.out.println("Retrieving books with title " + title);
        }
        Vector<Book> booksByTitle = null;
        int nbRetry = 100;
        while (nbRetry > 0) {
            try {
                booksByTitle = TPCW_Database.doTitleSearch(title, con, TPCW_id);
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
            System.out.println("Book query with title " + title
                    + " was successful (returned " + booksByTitle.size()
                    + " books)");
        }

        bookId = booksByTitle.get(r.nextInt(booksByTitle.size())).i_id;

        state = RequestType.GET_RELATED_BOOKS;
    }

    private void doGetBooksByAuthor(Connection con) throws Exception {
        String author = TPCW_Populate.authors.get(r
                .nextInt(TPCW_Populate.authors.size()));
        author = author.substring(0, author.length() - 1);
        author = "%" + author + "%";

        if (TRACE) {
            System.out.println("Retrieving books with author " + author);
        }
        Vector<Book> booksByAuthor = null;
        int nbRetry = 100;
        while (nbRetry > 0) {
            try {
                booksByAuthor = TPCW_Database.doAuthorSearch(author, con,
                        TPCW_id);
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
            System.out.println("Book query with author " + author
                    + " was successful (returned " + booksByAuthor.size()
                    + " books)");
        }

        bookId = booksByAuthor.get(r.nextInt(booksByAuthor.size())).i_id;

        state = RequestType.GET_RELATED_BOOKS;
    }

    private void doGetBooksBySubject(Connection con) throws Exception {
        int subjectIndex = r.nextInt(TPCW_Populate.NUM_SUBJECTS);
        String subject = TPCW_Populate.SUBJECTS[subjectIndex];

        if (TRACE) {
            System.out.println("Retrieving books for subject = " + subject);
        }
        Vector<Book> booksBySubject = null;
        int nbRetry = 100;
        while (nbRetry > 0) {
            try {
                booksBySubject = TPCW_Database.doSubjectSearch(subject, con,
                        TPCW_id);

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
            System.out.println("Book query on subject " + subject
                    + " was successful (returned " + booksBySubject.size()
                    + " books)");
        }

        bookId = booksBySubject.get(r.nextInt(booksBySubject.size())).i_id;

        state = RequestType.GET_RELATED_BOOKS;
    }

    private void doGetBestSellers(Connection con) throws Exception {
        int subjectIndex = r.nextInt(TPCW_Populate.NUM_SUBJECTS);
        String subject = TPCW_Populate.SUBJECTS[subjectIndex];

        if (TRACE) {
            System.out.println("Retrieving best sellers for subject = "
                    + subject);
        }

        Vector<ShortBook> bestSellers = null;

        int nbRetry = 100;
        while (nbRetry > 0) {
            try {
                bestSellers = TPCW_Database.getBestSellers(subject, con,
                        TPCW_id);

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
            System.out.println("Best seller query on " + subject
                    + " was successful (returned " + bestSellers.size()
                    + " books)");
        }

        if (bestSellers.size() == 0) {
            System.err.println("BEST_SELLER SIZE = 0");
        }

        bookId = bestSellers.get(r.nextInt(bestSellers.size())).i_id;

        state = RequestType.GET_RELATED_BOOKS;
    }

    private void doGetRelatedBooks(Connection con) throws Exception {
        Vector<Integer> i_id_vec = new Vector<Integer>();
        Vector<String> i_thumbnail_vec = new Vector<String>();
        if (TRACE) {
            System.out
                    .println("Retrieving related books for book_id " + bookId);
        }
        int nbRetry = 100;
        while (nbRetry > 0) {
            try {
                TPCW_Database.getRelated(bookId, i_id_vec, i_thumbnail_vec,
                        con, TPCW_id);

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
            System.out.println("Related books query for book_id " + bookId
                    + " was successful (returned " + i_id_vec.size()
                    + " books)");
        }

        // Either the user creates a Cart and buy the books it saw
        // Or it goes back to INIT state

        int p = r.nextInt(100);
        // 90%
        if (p < 90) {
            state = RequestType.CREATE_CART;
        }
        // 90%
        else if (p < 100) {
            state = RequestType.INIT;
        }
    }

    private void doCreateCart(Connection con) throws Exception {
        if (TRACE) {
            System.out.println("Creating empty cart");
        }
        int nbRetry = 100;
        while (nbRetry > 0) {
            try {
                shoppingId = TPCW_Database.createEmptyCart(con, TPCW_id);

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

        state = RequestType.FILL_CART;
    }

    private void doFillCart(Connection con) throws Exception {
        if (TRACE) {
            System.out.println("Filling cart");
        }
        Vector<Integer> ids = new Vector<Integer>();
        Vector<Integer> quantities = new Vector<Integer>();
        int p = r.nextInt(100);
        // proba 50% to add another item
        if (p < 50) {
            ids.add(bookId);
            quantities.add(1);
        }
        int nbRetry = 100;
        while (nbRetry > 0) {
            try {
                Cart cart = TPCW_Database.doCart(shoppingId, null, ids,
                        quantities, con, r, TPCW_id);

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
            System.out.println("Cart with shopping id = " + shoppingId
                    + " filled");
        }

        state = RequestType.GET_CUSTOMER;
    }

    private void doGetCustomer(Connection con) throws Exception {
        if (TRACE) {
            System.out.println("Getting customer");
        }
        int customerIndex = r.nextInt(TPCW_Populate.NUM_CUSTOMERS);
        String customerName = TPCW_Populate.customers.get(customerIndex);
        int nbRetry = 100;

        while (nbRetry > 0) {
            try {
                customer = TPCW_Database
                        .getCustomer(customerName, con, TPCW_id);
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

        int p = r.nextInt(100);
        // 10%
        if (p < 10) {
            state = RequestType.GET_MOST_RECENT_ORDER;
        }
        state = RequestType.DO_BUY_CONFIRM;
    }

    private void doGetMostRecentOrder(Connection con) throws Exception {
        if (TRACE) {
            System.out.println("Getting most recent order for customer "
                    + customer.c_id);
        }
        Vector<OrderLine> order_lines = new Vector<OrderLine>();
        Order order = null;

        int nbRetry = 100;
        while (nbRetry > 0) {
            try {
                order = TPCW_Database.GetMostRecentOrder(customer.c_uname,
                        order_lines, con, TPCW_id);
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

        state = RequestType.DO_BUY_CONFIRM;
    }

    private void doDoBuyConfirm(Connection con) throws Exception {
        if (TRACE) {
            System.out.println("doBuyConfirm for customer_id = "
                    + customer.c_id + " shoppingID = " + shoppingId);
        }

        String cc_type = TPCW_Populate.credit_cards[r
                .nextInt(TPCW_Populate.num_card_types)];

        int nbRetry = 100;
        while (nbRetry > 0) {
            try {
                TPCW_Database.doBuyConfirm(shoppingId, customer.c_id, cc_type, /* cc_number */
                        12345, "cc_name", /* cc_expiry */new Date(2010, 04, 27),
                        "shipping", con, r, TPCW_id);
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

        state = RequestType.INIT;
    }

    public boolean isNextARead() {
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
    }

}
