package request_player;

/* List of SQL statements used by TPCW when the database is Apache Derby */
public class DerbyStatements implements DBStatements
{

	/*
	 * (non-Javadoc)
	 * 
	 * @see databases.DBStatements#getName()
	 */
	@Override
	public String getName()
	{
		return "SELECT c_fname,c_lname FROM customer WHERE c_id = ?";
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see databases.DBStatements#getBook()
	 */
	@Override
	public String getBook()
	{
		return "SELECT * FROM item,author WHERE item.i_a_id = author.a_id AND i_id = ?";
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see databases.DBStatements#getCustomer()
	 */
	@Override
	public String getCustomer()
	{
		return "SELECT * FROM customer, address, country WHERE customer.c_addr_id = address.addr_id AND address.addr_co_id = country.co_id AND customer.c_uname = ?";
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see databases.DBStatements#doSubjectSearch()
	 */
	@Override
	public String doSubjectSearch()
	{
		return "SELECT * FROM item, author WHERE item.i_a_id = author.a_id AND item.i_subject = ? ORDER BY item.i_title FETCH FIRST 50 ROWS ONLY";
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see databases.DBStatements#doTitleSearch()
	 */
	@Override
	public String doTitleSearch()
	{
		return "SELECT * FROM item, author WHERE item.i_a_id = author.a_id AND item.i_title LIKE ? ORDER BY item.i_title FETCH FIRST 50 ROWS ONLY";
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see databases.DBStatements#doAuthorSearch()
	 */
	@Override
	public String doAuthorSearch()
	{
		return "SELECT * FROM author, item WHERE author.a_lname LIKE ? AND item.i_a_id = author.a_id ORDER BY item.i_title FETCH FIRST 50 ROWS ONLY";
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see databases.DBStatements#getNewProducts()
	 */
	@Override
	public String getNewProducts()
	{
		return "SELECT i_id, i_title, a_fname, a_lname " + "FROM item, author "
				+ "WHERE item.i_a_id = author.a_id "
				+ "AND item.i_subject = ? "
				+ "ORDER BY item.i_pub_date DESC,item.i_title "
				+ "FETCH FIRST 50 ROWS ONLY";
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see databases.DBStatements#getBestSellers()
	 */
	@Override
	public String getBestSellers()
	{
		return "SELECT i_id, i_title, a_fname, a_lname "
				+ "FROM item, author, order_line "
				+ "WHERE item.i_id = order_line.ol_i_id "
				+ "AND item.i_a_id = author.a_id "
				+ "AND order_line.ol_o_id > (SELECT MAX(o_id)-3333 FROM orders)"
				+ "AND item.i_subject = ? "
				+ "GROUP BY i_id, i_title, a_fname, a_lname "
				+ "ORDER BY SUM(ol_qty) DESC " + "FETCH FIRST 50 ROWS ONLY";
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see databases.DBStatements#getRelated()
	 */
	@Override
	public String getRelated()
	{
		return "SELECT J.i_id,J.i_thumbnail from item I, item J where (I.i_related1 = J.i_id or I.i_related2 = J.i_id or I.i_related3 = J.i_id or I.i_related4 = J.i_id or I.i_related5 = J.i_id) and I.i_id = ?";
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see databases.DBStatements#adminUpdate1()
	 */
	@Override
	public String adminUpdate1()
	{
		return "UPDATE item SET i_cost = ?, i_image = ?, i_thumbnail = ?, i_pub_date = CURRENT_DATE WHERE i_id = ?";
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see databases.DBStatements#adminUpdate2()
	 */
	@Override
	public String adminUpdate2()
	{
		return "SELECT ol_i_id "
				+ "FROM orders, order_line "
				+ "WHERE orders.o_id = order_line.ol_o_id "
				+ "AND NOT (order_line.ol_i_id = ?) "
				+ "AND orders.o_c_id IN (SELECT o_c_id "
				+ "                      FROM orders, order_line "
				+ "                      WHERE orders.o_id = order_line.ol_o_id "
				+ "                      AND orders.o_id > (SELECT MAX(o_id)-10000 FROM orders)"
				+ "                      AND order_line.ol_i_id = ?) "
				+ "GROUP BY ol_i_id " + "ORDER BY SUM(ol_qty) DESC "
				+ "FETCH FIRST 5 ROWS ONLY";
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see databases.DBStatements#adminUpdate3()
	 */
	@Override
	public String adminUpdate3()
	{
		return "UPDATE item SET i_related1 = ?, i_related2 = ?, i_related3 = ?, i_related4 = ?, i_related5 = ? WHERE i_id = ?";
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see databases.DBStatements#GetUserName()
	 */
	@Override
	public String GetUserName()
	{
		return "SELECT c_uname FROM customer WHERE c_id = ?";
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see databases.DBStatements#GetPassword()
	 */
	@Override
	public String GetPassword()
	{
		return "SELECT c_passwd FROM customer WHERE c_uname = ?";
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see databases.DBStatements#getRelated1()
	 */
	@Override
	public String getRelated1()
	{
		return "SELECT i_related1 FROM item where i_id = ?";
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see databases.DBStatements#GetMostRecentOrder1()
	 */
	@Override
	public String GetMostRecentOrder1()
	{
		return "SELECT o_id " + "FROM customer, orders "
				+ "WHERE customer.c_id = orders.o_c_id " + "AND c_uname = ? "
				+ "ORDER BY o_date, orders.o_id DESC "
				+ "FETCH FIRST 1 ROW ONLY";
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see databases.DBStatements#GetMostRecentOrder2()
	 */
	@Override
	public String GetMostRecentOrder2()
	{
		return "SELECT orders.*, customer.*, " + "  cc_xacts.cx_type, "
				+ "  ship.addr_street1 AS ship_addr_street1, "
				+ "  ship.addr_street2 AS ship_addr_street2, "
				+ "  ship.addr_state AS ship_addr_state, "
				+ "  ship.addr_zip AS ship_addr_zip, "
				+ "  ship_co.co_name AS ship_co_name, "
				+ "  bill.addr_street1 AS bill_addr_street1, "
				+ "  bill.addr_street2 AS bill_addr_street2, "
				+ "  bill.addr_state AS bill_addr_state, "
				+ "  bill.addr_zip AS bill_addr_zip, "
				+ "  bill_co.co_name AS bill_co_name "
				+ "FROM customer, orders, cc_xacts," + "  address AS ship, "
				+ "  country AS ship_co, " + "  address AS bill,  "
				+ "  country AS bill_co " + "WHERE orders.o_id = ? "
				+ "  AND cx_o_id = orders.o_id "
				+ "  AND customer.c_id = orders.o_c_id "
				+ "  AND orders.o_bill_addr_id = bill.addr_id "
				+ "  AND bill.addr_co_id = bill_co.co_id "
				+ "  AND orders.o_ship_addr_id = ship.addr_id "
				+ "  AND ship.addr_co_id = ship_co.co_id "
				+ "  AND orders.o_c_id = customer.c_id";
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see databases.DBStatements#GetMostRecentOrder3()
	 */
	@Override
	public String GetMostRecentOrder3()
	{
		return "SELECT * " + "FROM order_line, item " + "WHERE ol_o_id = ? "
				+ "AND ol_i_id = i_id";
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see databases.DBStatements#createEmptyCart1()
	 */
	@Override
	public String createEmptyCart1()
	{
		return "SELECT COUNT(*) FROM shopping_cart";
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see databases.DBStatements#createEmptyCart2()
	 */
	@Override
	public String createEmptyCart2()
	{
		return "INSERT INTO shopping_cart (sc_id, sc_time) VALUES ( ? , CURRENT_TIMESTAMP )";
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see databases.DBStatements#addItem1()
	 */
	@Override
	public String addItem1()
	{
		return "SELECT scl_qty FROM shopping_cart_line WHERE scl_sc_id = ? AND scl_i_id = ?";
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see databases.DBStatements#addItem2()
	 */
	@Override
	public String addItem2()
	{
		return "UPDATE shopping_cart_line SET scl_qty = ? WHERE scl_sc_id = ? AND scl_i_id = ?";
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see databases.DBStatements#addItem3()
	 */
	@Override
	public String addItem3()
	{
		return "INSERT into shopping_cart_line (scl_sc_id, scl_qty, scl_i_id) VALUES (?,?,?)";
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see databases.DBStatements#refreshCart1()
	 */
	@Override
	public String refreshCart1()
	{
		return "DELETE FROM shopping_cart_line WHERE scl_sc_id = ? AND scl_i_id = ?";
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see databases.DBStatements#refreshCart2()
	 */
	@Override
	public String refreshCart2()
	{
		return "UPDATE shopping_cart_line SET scl_qty = ? WHERE scl_sc_id = ? AND scl_i_id = ?";
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see databases.DBStatements#addRandomItemToCartIfNecessary()
	 */
	@Override
	public String addRandomItemToCartIfNecessary()
	{
		return "SELECT COUNT(*) from shopping_cart_line where scl_sc_id = ?";
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see databases.DBStatements#resetCartTime()
	 */
	@Override
	public String resetCartTime()
	{
		return "UPDATE shopping_cart SET sc_time = CURRENT_TIMESTAMP WHERE sc_id = ?";
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see databases.DBStatements#getCart()
	 */
	@Override
	public String getCart()
	{
		return "SELECT * " + "FROM shopping_cart_line, item "
				+ "WHERE scl_i_id = item.i_id AND scl_sc_id = ?";
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see databases.DBStatements#refreshSession()
	 */
	@Override
	public String refreshSession()
	{
		return "UPDATE customer SET c_login = CURRENT TIMESTAMP, c_expiration = {fn TIMESTAMPADD(SQL_TSI_HOUR, 2, CURRENT TIMESTAMP)} WHERE c_id = ?";
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see databases.DBStatements#createNewCustomer1()
	 */
	@Override
	public String createNewCustomer1()
	{
		return "INSERT into customer (c_id, c_uname, c_passwd, c_fname, c_lname, c_addr_id, c_phone, c_email, c_since, c_last_login, c_login, c_expiration, c_discount, c_balance, c_ytd_pmt, c_birthdate, c_data) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see databases.DBStatements#createNewCustomer2()
	 */
	@Override
	public String createNewCustomer2()
	{
		return "SELECT max(c_id) FROM customer";
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see databases.DBStatements#getCDiscount()
	 */
	@Override
	public String getCDiscount()
	{
		return "SELECT c_discount FROM customer WHERE customer.c_id = ?";
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see databases.DBStatements#getCaddrID()
	 */
	@Override
	public String getCaddrID()
	{
		return "SELECT c_addr_id FROM customer WHERE customer.c_id = ?";
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see databases.DBStatements#getCAddr()
	 */
	@Override
	public String getCAddr()
	{
		return "SELECT c_addr_id FROM customer WHERE customer.c_id = ?";
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see databases.DBStatements#enterCCXact()
	 */
	@Override
	public String enterCCXact()
	{
		return "INSERT into cc_xacts (cx_o_id, cx_type, cx_num, cx_name, cx_expiry, cx_xact_amt, cx_xact_date, cx_co_id) "
				+ "VALUES (?, ?, ?, ?, ?, ?, CURRENT DATE, (SELECT co_id FROM address, country WHERE addr_id = ? AND addr_co_id = co_id))";
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see databases.DBStatements#clearCart()
	 */
	@Override
	public String clearCart()
	{
		return "DELETE FROM shopping_cart_line WHERE scl_sc_id = ?";
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see databases.DBStatements#enterAddress1()
	 */
	@Override
	public String enterAddress1()
	{
		return "SELECT co_id FROM country WHERE co_name = ?";
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see databases.DBStatements#enterAddress2()
	 */
	@Override
	public String enterAddress2()
	{
		return "SELECT addr_id FROM address " + "WHERE addr_street1 = ? "
				+ "AND addr_street2 = ? " + "AND addr_city = ? "
				+ "AND addr_state = ? " + "AND addr_zip = ? "
				+ "AND addr_co_id = ?";
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see databases.DBStatements#enterAddress3()
	 */
	@Override
	public String enterAddress3()
	{
		return "INSERT into address (addr_id, addr_street1, addr_street2, addr_city, addr_state, addr_zip, addr_co_id) "
				+ "VALUES (?, ?, ?, ?, ?, ?, ?)";
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see databases.DBStatements#enterAddress4()
	 */
	@Override
	public String enterAddress4()
	{
		return "SELECT max(addr_id) FROM address";
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see databases.DBStatements#enterOrder1()
	 */
	@Override
	public String enterOrder1()
	{
		return "INSERT into orders (o_id, o_c_id, o_date, o_sub_total, "
				+ "o_tax, o_total, o_ship_type, o_ship_date, "
				+ "o_bill_addr_id, o_ship_addr_id, o_status) "
				+ "VALUES (?, ?, CURRENT DATE, ?, 8.25, ?, ?, CAST({fn TIMESTAMPADD(SQL_TSI_DAY, ?, CURRENT DATE)} as DATE), ?, ?, 'Pending')";
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see databases.DBStatements#enterOrder2()
	 */
	@Override
	public String enterOrder2()
	{
		return "SELECT count(o_id) FROM orders";
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see databases.DBStatements#addOrderLine()
	 */
	@Override
	public String addOrderLine()
	{
		return "INSERT into order_line (ol_id, ol_o_id, ol_i_id, ol_qty, ol_discount, ol_comments) "
				+ "VALUES (?, ?, ?, ?, ?, ?)";
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see databases.DBStatements#getStock()
	 */
	@Override
	public String getStock()
	{
		return "SELECT i_stock FROM item WHERE i_id = ?";
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see databases.DBStatements#setStock()
	 */
	@Override
	public String setStock()
	{
		return "UPDATE item SET i_stock = ? WHERE i_id = ?";
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see databases.DBStatements#verifyDBConsistency1()
	 */
	@Override
	public String verifyDBConsistency1()
	{
		return "SELECT c_id FROM customer";
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see databases.DBStatements#verifyDBConsistency2()
	 */
	@Override
	public String verifyDBConsistency2()
	{
		return "SELECT i_id FROM item";
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see databases.DBStatements#verifyDBConsistency3()
	 */
	@Override
	public String verifyDBConsistency3()
	{
		return "SELECT addr_id FROM address";
	}

	@Override
	public int code_getName()
	{
		return 0;
	}

	@Override
	public int code_getBook()
	{
		return 1;
	}

	@Override
	public int code_getCustomer()
	{
		return 2;
	}

	@Override
	public int code_doSubjectSearch()
	{
		return 3;
	}

	@Override
	public int code_doTitleSearch()
	{
		return 4;
	}

	@Override
	public int code_doAuthorSearch()
	{
		return 5;
	}

	@Override
	public int code_getNewProducts()
	{
		return 6;
	}

	@Override
	public int code_getBestSellers()
	{
		return 7;
	}

	@Override
	public int code_getRelated()
	{
		return 8;
	}

	@Override
	public int code_adminUpdate1()
	{
		return 9;
	}

	@Override
	public int code_adminUpdate2()
	{
		return 10;
	}

	@Override
	public int code_adminUpdate3()
	{
		return 11;
	}

	@Override
	public int code_GetUserName()
	{
		return 12;
	}

	@Override
	public int code_GetPassword()
	{
		return 13;
	}

	@Override
	public int code_getRelated1()
	{
		return 14;
	}

	@Override
	public int code_GetMostRecentOrder1()
	{
		return 15;
	}

	@Override
	public int code_GetMostRecentOrder2()
	{
		return 16;
	}

	@Override
	public int code_GetMostRecentOrder3()
	{
		return 17;
	}

	@Override
	public int code_createEmptyCart1()
	{
		return 18;
	}

	@Override
	public int code_createEmptyCart2()
	{
		return 19;
	}

	@Override
	public int code_addItem1()
	{
		return 20;
	}

	@Override
	public int code_addItem2()
	{
		return 21;
	}

	@Override
	public int code_addItem3()
	{
		return 22;
	}

	@Override
	public int code_refreshCart1()
	{
		return 23;
	}

	@Override
	public int code_refreshCart2()
	{
		return 24;
	}

	@Override
	public int code_addRandomItemToCartIfNecessary()
	{
		return 25;
	}

	@Override
	public int code_resetCartTime()
	{
		return 26;
	}

	@Override
	public int code_getCart()
	{
		return 27;
	}

	@Override
	public int code_refreshSession()
	{
		return 28;
	}

	@Override
	public int code_createNewCustomer1()
	{
		return 29;
	}

	@Override
	public int code_createNewCustomer2()
	{
		return 30;
	}

	@Override
	public int code_getCDiscount()
	{
		return 31;
	}

	@Override
	public int code_getCaddrID()
	{
		return 32;
	}

	@Override
	public int code_getCAddr()
	{
		return 33;
	}

	@Override
	public int code_enterCCXact()
	{
		return 34;
	}

	@Override
	public int code_clearCart()
	{
		return 35;
	}

	@Override
	public int code_enterAddress1()
	{
		return 36;
	}

	@Override
	public int code_enterAddress2()
	{
		return 37;
	}

	@Override
	public int code_enterAddress3()
	{
		return 38;
	}

	@Override
	public int code_enterAddress4()
	{
		return 39;
	}

	@Override
	public int code_enterOrder1()
	{
		return 40;
	}

	@Override
	public int code_enterOrder2()
	{
		return 41;
	}

	@Override
	public int code_addOrderLine()
	{
		return 42;
	}

	@Override
	public int code_getStock()
	{
		return 43;
	}

	@Override
	public int code_setStock()
	{
		return 44;
	}

	@Override
	public int code_verifyDBConsistency1()
	{
		return 45;
	}

	@Override
	public int code_verifyDBConsistency2()
	{
		return 46;
	}

	@Override
	public int code_verifyDBConsistency3()
	{
		return 47;
	}

	@Override
	public int numberStatements()
	{
		return 48;
	}

	@Override
	public int convertStatementToCode(String statement)
	{
		if (statement.equals(getName()))
		{
			return code_getName();
		}
		else if (statement.equals(getBook()))
		{
			return code_getBook();
		}
		else if (statement.equals(getCustomer()))
		{
			return code_getCustomer();
		}
		else if (statement.equals(doSubjectSearch()))
		{
			return code_doSubjectSearch();
		}
		else if (statement.equals(doTitleSearch()))
		{
			return code_doTitleSearch();
		}
		else if (statement.equals(doAuthorSearch()))
		{
			return code_doAuthorSearch();
		}
		else if (statement.equals(getNewProducts()))
		{
			return code_getNewProducts();
		}
		else if (statement.equals(getBestSellers()))
		{
			return code_getBestSellers();
		}
		else if (statement.equals(getRelated()))
		{
			return code_getRelated();
		}
		else if (statement.equals(adminUpdate1()))
		{
			return code_adminUpdate1();
		}
		else if (statement.equals(adminUpdate2()))
		{
			return code_adminUpdate2();
		}
		else if (statement.equals(adminUpdate3()))
		{
			return code_adminUpdate3();
		}
		else if (statement.equals(GetUserName()))
		{
			return code_GetUserName();
		}
		else if (statement.equals(GetPassword()))
		{
			return code_GetPassword();
		}
		else if (statement.equals(getRelated1()))
		{
			return code_getRelated1();
		}
		else if (statement.equals(GetMostRecentOrder1()))
		{
			return code_GetMostRecentOrder1();
		}
		else if (statement.equals(GetMostRecentOrder2()))
		{
			return code_GetMostRecentOrder2();
		}
		else if (statement.equals(GetMostRecentOrder3()))
		{
			return code_GetMostRecentOrder3();
		}
		else if (statement.equals(createEmptyCart1()))
		{
			return code_createEmptyCart1();
		}
		else if (statement.equals(createEmptyCart2()))
		{
			return code_createEmptyCart2();
		}
		else if (statement.equals(addItem1()))
		{
			return code_addItem1();
		}
		else if (statement.equals(addItem2()))
		{
			return code_addItem2();
		}
		else if (statement.equals(addItem3()))
		{
			return code_addItem3();
		}
		else if (statement.equals(refreshCart1()))
		{
			return code_refreshCart1();
		}
		else if (statement.equals(refreshCart2()))
		{
			return code_refreshCart2();
		}
		else if (statement.equals(addRandomItemToCartIfNecessary()))
		{
			return code_addRandomItemToCartIfNecessary();
		}
		else if (statement.equals(resetCartTime()))
		{
			return code_resetCartTime();
		}
		else if (statement.equals(getCart()))
		{
			return code_getCart();
		}
		else if (statement.equals(refreshSession()))
		{
			return code_refreshSession();
		}
		else if (statement.equals(createNewCustomer1()))
		{
			return code_createNewCustomer1();
		}
		else if (statement.equals(createNewCustomer2()))
		{
			return code_createNewCustomer2();
		}
		else if (statement.equals(getCDiscount()))
		{
			return code_getCDiscount();
		}
		else if (statement.equals(getCaddrID()))
		{
			return code_getCaddrID();
		}
		else if (statement.equals(getCAddr()))
		{
			return code_getCAddr();
		}
		else if (statement.equals(enterCCXact()))
		{
			return code_enterCCXact();
		}
		else if (statement.equals(clearCart()))
		{
			return code_clearCart();
		}
		else if (statement.equals(enterAddress1()))
		{
			return code_enterAddress1();
		}
		else if (statement.equals(enterAddress2()))
		{
			return code_enterAddress2();
		}
		else if (statement.equals(enterAddress3()))
		{
			return code_enterAddress3();
		}
		else if (statement.equals(enterAddress4()))
		{
			return code_enterAddress4();
		}
		else if (statement.equals(enterOrder1()))
		{
			return code_enterOrder1();
		}
		else if (statement.equals(enterOrder2()))
		{
			return code_enterOrder2();
		}
		else if (statement.equals(addOrderLine()))
		{
			return code_addOrderLine();
		}
		else if (statement.equals(getStock()))
		{
			return code_getStock();
		}
		else if (statement.equals(setStock()))
		{
			return code_setStock();
		}
		else if (statement.equals(verifyDBConsistency1()))
		{
			return code_verifyDBConsistency1();
		}
		else if (statement.equals(verifyDBConsistency2()))
		{
			return code_verifyDBConsistency2();
		}
		else if (statement.equals(verifyDBConsistency3()))
		{
			return code_verifyDBConsistency3();
		}

		System.out.println("Unknown statement: " + statement);
		return -1;
	}

	@Override
	public String convertCodeToStatement(int code)
	{
		if (code == code_getName())
		{
			return getName();
		}
		else if (code == code_getBook())
		{
			return getBook();
		}
		else if (code == code_getCustomer())
		{
			return getCustomer();
		}
		else if (code == code_doSubjectSearch())
		{
			return doSubjectSearch();
		}
		else if (code == code_doTitleSearch())
		{
			return doTitleSearch();
		}
		else if (code == code_doAuthorSearch())
		{
			return doAuthorSearch();
		}
		else if (code == code_getNewProducts())
		{
			return getNewProducts();
		}
		else if (code == code_getBestSellers())
		{
			return getBestSellers();
		}
		else if (code == code_getRelated())
		{
			return getRelated();
		}
		else if (code == code_adminUpdate1())
		{
			return adminUpdate1();
		}
		else if (code == code_adminUpdate2())
		{
			return adminUpdate2();
		}
		else if (code == code_adminUpdate3())
		{
			return adminUpdate3();
		}
		else if (code == code_GetUserName())
		{
			return GetUserName();
		}
		else if (code == code_GetPassword())
		{
			return GetPassword();
		}
		else if (code == code_getRelated1())
		{
			return getRelated1();
		}
		else if (code == code_GetMostRecentOrder1())
		{
			return GetMostRecentOrder1();
		}
		else if (code == code_GetMostRecentOrder2())
		{
			return GetMostRecentOrder2();
		}
		else if (code == code_GetMostRecentOrder3())
		{
			return GetMostRecentOrder3();
		}
		else if (code == code_createEmptyCart1())
		{
			return createEmptyCart1();
		}
		else if (code == code_createEmptyCart2())
		{
			return createEmptyCart2();
		}
		else if (code == code_addItem1())
		{
			return addItem1();
		}
		else if (code == code_addItem2())
		{
			return addItem2();
		}
		else if (code == code_addItem3())
		{
			return addItem3();
		}
		else if (code == code_refreshCart1())
		{
			return refreshCart1();
		}
		else if (code == code_refreshCart2())
		{
			return refreshCart2();
		}
		else if (code == code_addRandomItemToCartIfNecessary())
		{
			return addRandomItemToCartIfNecessary();
		}
		else if (code == code_resetCartTime())
		{
			return resetCartTime();
		}
		else if (code == code_getCart())
		{
			return getCart();
		}
		else if (code == code_refreshSession())
		{
			return refreshSession();
		}
		else if (code == code_createNewCustomer1())
		{
			return createNewCustomer1();
		}
		else if (code == code_createNewCustomer2())
		{
			return createNewCustomer2();
		}
		else if (code == code_getCDiscount())
		{
			return getCDiscount();
		}
		else if (code == code_getCaddrID())
		{
			return getCaddrID();
		}
		else if (code == code_getCAddr())
		{
			return getCAddr();
		}
		else if (code == code_enterCCXact())
		{
			return enterCCXact();
		}
		else if (code == code_clearCart())
		{
			return clearCart();
		}
		else if (code == code_enterAddress1())
		{
			return enterAddress1();
		}
		else if (code == code_enterAddress2())
		{
			return enterAddress2();
		}
		else if (code == code_enterAddress3())
		{
			return enterAddress3();
		}
		else if (code == code_enterAddress4())
		{
			return enterAddress4();
		}
		else if (code == code_enterOrder1())
		{
			return enterOrder1();
		}
		else if (code == code_enterOrder2())
		{
			return enterOrder2();
		}
		else if (code == code_addOrderLine())
		{
			return addOrderLine();
		}
		else if (code == code_getStock())
		{
			return getStock();
		}
		else if (code == code_setStock())
		{
			return setStock();
		}
		else if (code == code_verifyDBConsistency1())
		{
			return verifyDBConsistency1();
		}
		else if (code == code_verifyDBConsistency2())
		{
			return verifyDBConsistency2();
		}
		else if (code == code_verifyDBConsistency3())
		{
			return verifyDBConsistency3();
		}

		System.out.println("Unknown code: " + code);
		return null;
	}

	@Override
	public boolean statementIsRead(int code)
	{
		if (code == code_adminUpdate1()
				|| code == code_adminUpdate3()
				|| code == code_createEmptyCart2()
				|| code == code_addItem2()
				|| code == code_addItem3()
				|| code == code_refreshCart1()
				|| code == code_refreshCart2()
				|| code == code_resetCartTime()
				|| code == code_refreshSession()
				|| code == code_createNewCustomer1()
				|| code == code_enterCCXact()
				|| code == code_clearCart()
				|| code == code_enterAddress3()
				|| code == code_enterOrder1()
				|| code == code_addOrderLine()
				|| code == code_setStock())
		{
			return false;
		}
		else
		{
			return true;
		}
	}
}
