package Applications.tpcw_new.request_player;

public interface DBStatements {
    public abstract String getName();

    public abstract String getBook();

    public abstract String getCustomer();

    public abstract String doSubjectSearch();

    public abstract String doTitleSearch();

    public abstract String doAuthorSearch();

    public abstract String getNewProducts();

    public abstract String getBestSellers();

    public abstract String getRelated();

    public abstract String adminUpdate1();

    public abstract String adminUpdate2();

    public abstract String adminUpdate3();

    public abstract String GetUserName();

    public abstract String GetPassword();

    public abstract String getRelated1();

    public abstract String GetMostRecentOrder1();

    public abstract String GetMostRecentOrder2();

    public abstract String GetMostRecentOrder3();

    public abstract String createEmptyCart1();

    public abstract String createEmptyCart2();

    public abstract String addItem1();

    public abstract String addItem2();

    public abstract String addItem3();

    public abstract String refreshCart1();

    public abstract String refreshCart2();

    public abstract String addRandomItemToCartIfNecessary();

    public abstract String resetCartTime();

    public abstract String getCart();

    public abstract String refreshSession();

    public abstract String createNewCustomer1();

    public abstract String createNewCustomer2();

    public abstract String getCDiscount();

    public abstract String getCaddrID();

    public abstract String getCAddr();

    public abstract String enterCCXact();

    public abstract String clearCart();

    public abstract String enterAddress1();

    public abstract String enterAddress2();

    public abstract String enterAddress3();

    public abstract String enterAddress4();

    public abstract String enterOrder1();

    public abstract String enterOrder2();

    public abstract String addOrderLine();

    public abstract String getStock();

    public abstract String setStock();

    public abstract String verifyDBConsistency1();

    public abstract String verifyDBConsistency2();

    public abstract String verifyDBConsistency3();

    public abstract String commitTransaction();

    public abstract int code_getName();

    public abstract int code_getBook();

    public abstract int code_getCustomer();

    public abstract int code_doSubjectSearch();

    public abstract int code_doTitleSearch();

    public abstract int code_doAuthorSearch();

    public abstract int code_getNewProducts();

    public abstract int code_getBestSellers();

    public abstract int code_getRelated();

    public abstract int code_adminUpdate1();

    public abstract int code_adminUpdate2();

    public abstract int code_adminUpdate3();

    public abstract int code_GetUserName();

    public abstract int code_GetPassword();

    public abstract int code_getRelated1();

    public abstract int code_GetMostRecentOrder1();

    public abstract int code_GetMostRecentOrder2();

    public abstract int code_GetMostRecentOrder3();

    public abstract int code_createEmptyCart1();

    public abstract int code_createEmptyCart2();

    public abstract int code_addItem1();

    public abstract int code_addItem2();

    public abstract int code_addItem3();

    public abstract int code_refreshCart1();

    public abstract int code_refreshCart2();

    public abstract int code_addRandomItemToCartIfNecessary();

    public abstract int code_resetCartTime();

    public abstract int code_getCart();

    public abstract int code_refreshSession();

    public abstract int code_createNewCustomer1();

    public abstract int code_createNewCustomer2();

    public abstract int code_getCDiscount();

    public abstract int code_getCaddrID();

    public abstract int code_getCAddr();

    public abstract int code_enterCCXact();

    public abstract int code_clearCart();

    public abstract int code_enterAddress1();

    public abstract int code_enterAddress2();

    public abstract int code_enterAddress3();

    public abstract int code_enterAddress4();

    public abstract int code_enterOrder1();

    public abstract int code_enterOrder2();

    public abstract int code_addOrderLine();

    public abstract int code_getStock();

    public abstract int code_setStock();

    public abstract int code_verifyDBConsistency1();

    public abstract int code_verifyDBConsistency2();

    public abstract int code_verifyDBConsistency3();

    public abstract int code_commit();

    public abstract int numberStatements();

    public abstract int convertStatementToCode(String statement);

    public abstract String convertCodeToStatement(int code);

    public abstract boolean statementIsRead(int code);
}