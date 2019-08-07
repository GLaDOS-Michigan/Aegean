package request_player;

public interface DataBase
{

	final int NUM_CUSTOMERS = RequestPlayerUtils.NUM_EBS * 2880;
	final int NUM_ADDRESSES = 2 * NUM_CUSTOMERS;
	final int NUM_AUTHORS = (int) (.25 * RequestPlayerUtils.NUM_ITEMS);
	final int NUM_ORDERS = (int) (.9 * NUM_CUSTOMERS);

	void initDB();

	String getDBName();
}
