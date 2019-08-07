/*------------------------------------------------------------------------
 * rbe.EBWCustRegTrans.java
 * Timothy Heil
 * 10/13/99
 *
 * ECE902 Fall '99
 *
 * TPC-W customer registeration transition to the customer registration 
 *  page from the shopping cart page.
 *------------------------------------------------------------------------*/

package tpcw.real;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

public class CustRegTrans extends Transition {

    @Override
    public void execute(User eb, Connection con, Statement stat) throws SQLException {
        System.out.println("Executing EBWCustRegTrans");
        Benchmark rbe = eb.rbe;

        String username;
        // Retrieve C_ID from previous interactions
        int C_ID = -1;
        if (C_ID != -1) {
            // username = TPCW_Database.GetUserName(C_ID);
        } else {
            username = "";
        }
    }
}
