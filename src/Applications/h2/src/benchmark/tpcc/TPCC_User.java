/*
 * Copyright 2004-2010 H2 Group. Multiple-Licensed under the H2 License,
 * Version 1.0, and under the Eclipse Public License, Version 1.0
 * (http://h2database.com/html/license.html).
 * Initial Developer: H2 Group
 */
package tpcc;

import java.math.BigDecimal;
import java.sql.*;

/**
 * This class implements the functionality of one thread of BenchC.
 */
public class TPCC_User {

    int[] deck = {OP_NEW_ORDER, OP_NEW_ORDER, OP_NEW_ORDER, OP_NEW_ORDER,
            OP_NEW_ORDER, OP_NEW_ORDER, OP_NEW_ORDER, OP_NEW_ORDER,
            OP_NEW_ORDER, OP_NEW_ORDER, OP_PAYMENT, OP_PAYMENT, OP_PAYMENT,
            OP_PAYMENT, OP_PAYMENT, OP_PAYMENT, OP_PAYMENT, OP_PAYMENT,
            OP_PAYMENT, OP_PAYMENT, OP_ORDER_STATUS, OP_DELIVERY,
            OP_STOCK_LEVEL};

    private static final int OP_NEW_ORDER = 0, OP_PAYMENT = 1,
            OP_ORDER_STATUS = 2, OP_DELIVERY = 3, OP_STOCK_LEVEL = 4, INIT = 5;

    private static final BigDecimal ONE = new BigDecimal("1");
    int nbExecutions;

    private int state = INIT;

    private int warehouseId;
    private int terminalId;
    private TPCC_Random random;
    public int id;
    int TPCC_id;
    int nextOperationId = -1;

    TPCC_User(int id, int nbTPCCs) throws SQLException {
        this.id = id;
        this.terminalId = id;
        this.TPCC_id = id % nbTPCCs;
        random = new TPCC_Random(id);
    }

    /**
     * Process the list of operations (a 'deck') in random order.
     */
    public void execute(Connection conn, Statement stat) throws Exception {
        nbExecutions++;
        switch (state) {
            case INIT:
                init();
                break;
            case OP_NEW_ORDER:
                processNewOrder(conn, stat);
                break;
            case OP_PAYMENT:
                processPayment(conn, stat);
                break;
            case OP_ORDER_STATUS:
                processOrderStatus(conn, stat);
                break;
            case OP_DELIVERY:
                processDelivery(conn, stat);
                break;
            case OP_STOCK_LEVEL:
                processStockLevel(conn, stat);
                break;
            default:
                System.err.println("Unknown state");
                break;
        }
        nextOperationId++;
        if (nextOperationId >= deck.length) {
            state = INIT;
        } else {
            state = deck[nextOperationId];
        }
    }

    private void init() throws SQLException {

        warehouseId = random.getInt(1, TPCC_Benchmark.warehouses);

        int len = deck.length;
        for (int i = 0; i < len; i++) {
            int temp = deck[i];
            int j = random.getInt(0, len);
            deck[i] = deck[j];
            deck[j] = temp;
        }

        nextOperationId = 0;
    }

    private void processNewOrder(Connection conn, Statement stat)
            throws Exception {
        try {
            int dId = random.getInt(1, TPCC_Benchmark.districtsPerWarehouse);

            int cId = random.getNonUniform(1023, 1,
                    TPCC_Benchmark.customersPerDistrict);
            int olCnt = random.getInt(5, 15);
            boolean rollback = random.getInt(1, 100) == 1;
            int[] supplyId = new int[olCnt];
            int[] itemId = new int[olCnt];
            int[] quantity = new int[olCnt];
            int allLocal = 1;
            for (int i = 0; i < olCnt; i++) {
                int w;
                if (TPCC_Benchmark.warehouses > 1 && random.getInt(1, 100) == 1) {
                    do {
                        w = random.getInt(1, TPCC_Benchmark.warehouses);
                    } while (w != warehouseId);
                    allLocal = 0;
                } else {
                    w = warehouseId;
                }
                supplyId[i] = w;
                int item;
                if (rollback && i == olCnt - 1) {
                    // unused order number
                    item = -1;
                } else {
                    item = random.getNonUniform(8191, 1, TPCC_Benchmark.items);
                }
                itemId[i] = item;
                quantity[i] = random.getInt(1, 10);
            }
            char[] bg = new char[olCnt];
            int[] stock = new int[olCnt];
            BigDecimal[] amt = new BigDecimal[olCnt];
            Timestamp datetime = new Timestamp(System.currentTimeMillis());
            ResultSet rs;

            executeUpdate(conn, stat, "UPDATE DISTRICT" + TPCC_id
                    + " SET D_NEXT_O_ID=D_NEXT_O_ID+1 " + "WHERE D_ID=" + dId
                    + " AND D_W_ID=" + warehouseId);

            rs = executeQuery(stat, "SELECT D_NEXT_O_ID, D_TAX FROM DISTRICT"
                    + TPCC_id + " WHERE D_ID=" + dId + " AND D_W_ID="
                    + warehouseId);
            rs.next();
            int oId = rs.getInt(1) - 1;
            BigDecimal tax = rs.getBigDecimal(2);
            rs.close();
            rs = executeQuery(stat,
                    "SELECT C_DISCOUNT, C_LAST, C_CREDIT, W_TAX "
                            + "FROM CUSTOMER" + TPCC_id + ", WAREHOUSE"
                            + TPCC_id + " WHERE C_ID=" + cId + " AND W_ID="
                            + warehouseId + " AND C_W_ID=W_ID AND C_D_ID="
                            + dId);
            rs.next();
            BigDecimal discount = rs.getBigDecimal(1);
            // c_last
            rs.getString(2);
            // c_credit
            rs.getString(3);
            BigDecimal wTax = rs.getBigDecimal(4);
            rs.close();
            BigDecimal total = new BigDecimal("0");
            for (int number = 1; number <= olCnt; number++) {
                int olId = itemId[number - 1];
                int olSupplyId = supplyId[number - 1];
                int olQuantity = quantity[number - 1];
                rs = executeQuery(stat, "SELECT I_PRICE, I_NAME, I_DATA "
                        + "FROM ITEM" + TPCC_id + " WHERE I_ID=" + olId);
                if (!rs.next()) {
                    if (rollback) {
                        // item not found - correct behavior
                        rollback(conn);
                        return;
                    }
                    throw new SQLException("item not found: " + olId + " "
                            + olSupplyId);
                }
                BigDecimal price = rs.getBigDecimal(1);
                // i_name
                rs.getString(2);
                String data = rs.getString(3);
                rs.close();
                rs = executeQuery(
                        stat,
                        "SELECT S_QUANTITY, S_DATA, "
                                + "S_DIST_01, S_DIST_02, S_DIST_03, S_DIST_04, S_DIST_05, "
                                + "S_DIST_06, S_DIST_07, S_DIST_08, S_DIST_09, S_DIST_10 "
                                + "FROM STOCK" + TPCC_id + " WHERE S_I_ID="
                                + olId + " AND S_W_ID=" + olSupplyId);
                if (!rs.next()) {
                    if (rollback) {
                        // item not found - correct behavior
                        rollback(conn);
                        return;
                    }
                    throw new SQLException("item not found: " + olId + " "
                            + olSupplyId);
                }
                int sQuantity = rs.getInt(1);
                String sData = rs.getString(2);
                String[] dist = new String[10];
                for (int i = 0; i < 10; i++) {
                    dist[i] = rs.getString(3 + i);
                }
                rs.close();
                String distInfo = dist[dId - 1];
                stock[number - 1] = sQuantity;
                if ((data.indexOf("original") != -1)
                        && (sData.indexOf("original") != -1)) {
                    bg[number - 1] = 'B';
                } else {
                    bg[number - 1] = 'G';
                }
                if (sQuantity > olQuantity) {
                    sQuantity = sQuantity - olQuantity;
                } else {
                    sQuantity = sQuantity - olQuantity + 91;
                }
                executeUpdate(conn, stat, "UPDATE STOCK" + TPCC_id
                        + " SET S_QUANTITY=" + sQuantity + " WHERE S_W_ID="
                        + olSupplyId + " AND S_I_ID=" + olId);
                BigDecimal olAmount = new BigDecimal(olQuantity)
                        .multiply(price).multiply(ONE.add(wTax).add(tax))
                        .multiply(ONE.subtract(discount));
                olAmount = olAmount.setScale(2, BigDecimal.ROUND_HALF_UP);
                amt[number - 1] = olAmount;
                total = total.add(olAmount);
                executeUpdate(conn, stat, "INSERT INTO ORDER_LINE" + TPCC_id
                        + " (OL_O_ID, OL_D_ID, OL_W_ID, OL_NUMBER, "
                        + "OL_I_ID, OL_SUPPLY_W_ID, "
                        + "OL_QUANTITY, OL_AMOUNT, OL_DIST_INFO) " + "VALUES ("
                        + oId + "," + dId + "," + warehouseId + "," + number
                        + "," + olId + "," + olSupplyId + "," + olQuantity
                        + "," + olAmount + "," + "\'" + distInfo + "\')");
            }

            executeUpdate(conn, stat, "INSERT INTO ORDERS" + TPCC_id
                    + " (O_ID, O_D_ID, O_W_ID, O_C_ID, "
                    + "O_ENTRY_D, O_OL_CNT, O_ALL_LOCAL) " + "VALUES (" + oId
                    + "," + dId + "," + warehouseId + "," + cId + "," + "\'"
                    + datetime + "\'" + "," + olCnt + "," + allLocal + ")");
            executeUpdate(conn, stat, "INSERT INTO NEW_ORDER" + TPCC_id
                    + " (NO_O_ID, NO_D_ID, NO_W_ID) " + "VALUES (" + oId + ","
                    + dId + "," + warehouseId + ")");
            commit(conn);
        } catch (SQLException e) {
            rollback(conn);
        }
    }

    private void processPayment(Connection conn, Statement stat)
            throws Exception {

        try {
            int dId = random.getInt(1, TPCC_Benchmark.districtsPerWarehouse);
            int wId, cdId;
            if (TPCC_Benchmark.warehouses > 1 && random.getInt(1, 100) <= 15) {
                do {
                    wId = random.getInt(1, TPCC_Benchmark.warehouses);
                } while (wId != warehouseId);
                cdId = random.getInt(1, TPCC_Benchmark.districtsPerWarehouse);
            } else {
                wId = warehouseId;
                cdId = dId;
            }
            boolean byName;
            String last;
            int cId = 1;
            if (random.getInt(1, 100) <= 60) {
                byName = true;
                last = random.getLastname(random.getNonUniform(255, 0, 999));
            } else {
                byName = false;
                last = "";
                cId = random.getNonUniform(1023, 1,
                        TPCC_Benchmark.customersPerDistrict);
            }
            BigDecimal amount = random.getBigDecimal(
                    random.getInt(100, 500000), 2);
            Timestamp datetime = new Timestamp(System.currentTimeMillis());
            ResultSet rs;

            executeUpdate(conn, stat, "UPDATE DISTRICT" + TPCC_id
                    + " SET D_YTD = D_YTD+" + amount + " WHERE D_ID=" + dId
                    + " AND D_W_ID=" + warehouseId);
            executeUpdate(conn, stat, "UPDATE WAREHOUSE" + TPCC_id
                    + " SET W_YTD=W_YTD+" + amount + " WHERE W_ID="
                    + warehouseId);
            rs = executeQuery(stat,
                    "SELECT W_STREET_1, W_STREET_2, W_CITY, W_STATE, W_ZIP, W_NAME "
                            + "FROM WAREHOUSE" + TPCC_id + " WHERE W_ID="
                            + warehouseId);
            rs.next();
            // w_street_1
            rs.getString(1);
            // w_street_2
            rs.getString(2);
            // w_city
            rs.getString(3);
            // w_state
            rs.getString(4);
            // w_zip
            rs.getString(5);
            String wName = rs.getString(6);
            rs.close();
            rs = executeQuery(stat,
                    "SELECT D_STREET_1, D_STREET_2, D_CITY, D_STATE, D_ZIP, D_NAME "
                            + "FROM DISTRICT" + TPCC_id + " WHERE D_ID=" + dId
                            + " AND D_W_ID=" + warehouseId);
            rs.next();
            // d_street_1
            rs.getString(1);
            // d_street_2
            rs.getString(2);
            // d_city
            rs.getString(3);
            // d_state
            rs.getString(4);
            // d_zip
            rs.getString(5);
            String dName = rs.getString(6);
            rs.close();
            BigDecimal balance;
            String credit;
            if (byName) {
                rs = executeQuery(stat, "SELECT COUNT(C_ID) FROM CUSTOMER"
                        + TPCC_id + " WHERE C_LAST=" + "\'" + last + "\'"
                        + " AND C_D_ID=" + cdId + " AND C_W_ID=" + wId);
                rs.next();
                int namecnt = rs.getInt(1);
                rs.close();
                if (namecnt == 0) {
                    // TODO TPC-C: check if this can happen
                    rollback(conn);
                    return;
                }
                rs = executeQuery(stat, "SELECT C_FIRST, C_MIDDLE, C_ID, "
                        + "C_STREET_1, C_STREET_2, C_CITY, C_STATE, C_ZIP, "
                        + "C_PHONE, C_CREDIT, C_CREDIT_LIM, "
                        + "C_DISCOUNT, C_BALANCE, C_SINCE FROM CUSTOMER"
                        + TPCC_id + " WHERE C_LAST=" + "\'" + last + "\'"
                        + " AND C_D_ID=" + cdId + " AND C_W_ID=" + wId
                        + " ORDER BY C_FIRST");
                // locate midpoint customer
                if (namecnt % 2 != 0) {
                    namecnt++;
                }
                for (int n = 0; n < namecnt / 2; n++) {
                    rs.next();
                }
                // c_first
                rs.getString(1);
                // c_middle
                rs.getString(2);
                cId = rs.getInt(3);
                // c_street_1
                rs.getString(4);
                // c_street_2
                rs.getString(5);
                // c_city
                rs.getString(6);
                // c_state
                rs.getString(7);
                // c_zip
                rs.getString(8);
                // c_phone
                rs.getString(9);
                credit = rs.getString(10);
                // c_credit_lim
                rs.getString(11);
                // c_discount
                rs.getBigDecimal(12);
                balance = rs.getBigDecimal(13);
                // c_since
                rs.getTimestamp(14);
                rs.close();
            } else {
                rs = executeQuery(stat, "SELECT C_FIRST, C_MIDDLE, C_LAST, "
                        + "C_STREET_1, C_STREET_2, C_CITY, C_STATE, C_ZIP, "
                        + "C_PHONE, C_CREDIT, C_CREDIT_LIM, "
                        + "C_DISCOUNT, C_BALANCE, C_SINCE FROM CUSTOMER"
                        + TPCC_id + " WHERE C_ID=" + cId + " AND C_D_ID="
                        + cdId + " AND C_W_ID=" + wId);
                rs.next();
                // c_first
                rs.getString(1);
                // c_middle
                rs.getString(2);
                // c_last
                rs.getString(3);
                // c_street_1
                rs.getString(4);
                // c_street_2
                rs.getString(5);
                // c_city
                rs.getString(6);
                // c_state
                rs.getString(7);
                // c_zip
                rs.getString(8);
                // c_phone
                rs.getString(9);
                credit = rs.getString(10);
                // c_credit_lim
                rs.getString(11);
                // c_discount
                rs.getBigDecimal(12);
                balance = rs.getBigDecimal(13);
                // c_since
                rs.getTimestamp(14);
                rs.close();
            }
            balance = balance.add(amount);
            if (credit.equals("BC")) {
                rs = executeQuery(stat, "SELECT C_DATA INTO FROM CUSTOMER"
                        + TPCC_id + " WHERE C_ID=" + cId + " AND C_D_ID="
                        + cdId + " AND C_W_ID=" + wId);
                rs.next();
                String cData = rs.getString(1);
                rs.close();
                String cNewData = "| " + cId + " " + cdId + " " + wId + " "
                        + dId + " " + warehouseId + " " + amount + " " + cData;
                if (cNewData.length() > 500) {
                    cNewData = cNewData.substring(0, 500);
                }
                executeUpdate(conn, stat, "UPDATE CUSTOMER" + TPCC_id
                        + " SET C_BALANCE=" + balance + ", C_DATA=" + cNewData
                        + " WHERE C_ID=" + cId + " AND C_D_ID=" + cdId
                        + " AND C_W_ID=" + wId);
            } else {
                try {
                    executeUpdate(conn, stat, "UPDATE CUSTOMER" + TPCC_id
                            + " SET C_BALANCE=" + balance + " WHERE C_ID="
                            + cId + " AND C_D_ID=" + cdId + " AND C_W_ID="
                            + wId);
                } catch (SQLException e) {
                    rollback(conn);
                }
            }
            // MySQL bug?
            // String h_data = w_name + "    " + d_name;
            String hData = wName + " " + dName;
            executeUpdate(conn, stat, "INSERT INTO HISTORY" + TPCC_id
                    + " (H_C_D_ID, H_C_W_ID, H_C_ID, H_D_ID, "
                    + "H_W_ID, H_DATE, H_AMOUNT, H_DATA) " + "VALUES (" + cdId
                    + "," + wId + "," + cId + "," + dId + "," + warehouseId
                    + "," + "\'" + datetime + "\'" + "," + amount + "," + "\'"
                    + hData + "\'" + ")");
            commit(conn);
        } catch (SQLException e) {
            rollback(conn);
        }
    }

    private void processOrderStatus(Connection conn, Statement stat)
            throws Exception {
        int dId = random.getInt(1, TPCC_Benchmark.districtsPerWarehouse);
        boolean byName;
        String last = null;
        int cId = -1;
        if (random.getInt(1, 100) <= 60) {
            byName = true;
            last = random.getLastname(random.getNonUniform(255, 0, 999));
        } else {
            byName = false;
            cId = random.getNonUniform(1023, 1,
                    TPCC_Benchmark.customersPerDistrict);
        }
        ResultSet rs;

        executeUpdate(conn, stat, "UPDATE DISTRICT" + TPCC_id
                + " SET D_NEXT_O_ID=-1 WHERE D_ID=-1");
        if (byName) {
            rs = executeQuery(stat, "SELECT COUNT(C_ID) FROM CUSTOMER"
                    + TPCC_id + " WHERE C_LAST=" + "\'" + last + "\'"
                    + " AND C_D_ID=" + dId + " AND C_W_ID=" + warehouseId);
            rs.next();
            int namecnt = rs.getInt(1);
            rs.close();
            if (namecnt == 0) {
                // TODO TPC-C: check if this can happen
                rollback(conn);
                return;
            }
            rs = executeQuery(stat,
                    "SELECT C_BALANCE, C_FIRST, C_MIDDLE, C_ID "
                            + "FROM CUSTOMER" + TPCC_id + " WHERE C_LAST="
                            + "\'" + last + "\'" + " AND C_D_ID=" + dId
                            + " AND C_W_ID=" + warehouseId
                            + " ORDER BY C_FIRST");
            if (namecnt % 2 != 0) {
                namecnt++;
            }
            for (int n = 0; n < namecnt / 2; n++) {
                rs.next();
            }
            // c_balance
            rs.getBigDecimal(1);
            // c_first
            rs.getString(2);
            // c_middle
            rs.getString(3);
            rs.close();
        } else {
            rs = executeQuery(stat,
                    "SELECT C_BALANCE, C_FIRST, C_MIDDLE, C_LAST "
                            + "FROM CUSTOMER" + TPCC_id + " WHERE C_ID=" + cId
                            + " AND C_D_ID=" + dId + " AND C_W_ID="
                            + warehouseId);
            rs.next();
            // c_balance
            rs.getBigDecimal(1);
            // c_first
            rs.getString(2);
            // c_middle
            rs.getString(3);
            // c_last
            rs.getString(4);
            rs.close();
        }
        rs = executeQuery(stat, "SELECT MAX(O_ID) " + "FROM ORDERS" + TPCC_id
                + " WHERE O_C_ID=" + cId + " AND O_D_ID=" + dId
                + " AND O_W_ID=" + warehouseId);
        int oId = -1;
        if (rs.next()) {
            oId = rs.getInt(1);
            if (rs.wasNull()) {
                oId = -1;
            }
        }
        rs.close();
        if (oId != -1) {
            rs = executeQuery(stat, "SELECT O_ID, O_CARRIER_ID, O_ENTRY_D "
                    + "FROM ORDERS" + TPCC_id + " WHERE O_ID=" + oId);
            rs.next();
            oId = rs.getInt(1);
            // o_carrier_id
            rs.getInt(2);
            // o_entry_d
            rs.getTimestamp(3);
            rs.close();
            rs = executeQuery(stat,
                    "SELECT OL_I_ID, OL_SUPPLY_W_ID, OL_QUANTITY, "
                            + "OL_AMOUNT, OL_DELIVERY_D FROM ORDER_LINE"
                            + TPCC_id + " WHERE OL_O_ID=" + oId
                            + " AND OL_D_ID=" + dId + " AND OL_W_ID="
                            + warehouseId);
            while (rs.next()) {
                // o_i_id
                rs.getInt(1);
                // ol_supply_w_id
                rs.getInt(2);
                // ol_quantity
                rs.getInt(3);
                // ol_amount
                rs.getBigDecimal(4);
                // ol_delivery_d
                rs.getTimestamp(5);
            }
            rs.close();
        }
        commit(conn);
    }

    private void processDelivery(Connection conn, Statement stat)
            throws Exception {
        int carrierId = random.getInt(1, 10);
        Timestamp datetime = new Timestamp(System.currentTimeMillis());
        ResultSet rs;

        executeUpdate(conn, stat, "UPDATE DISTRICT" + TPCC_id
                + " SET D_NEXT_O_ID=-1 WHERE D_ID=-1");
        for (int dId = 1; dId <= TPCC_Benchmark.districtsPerWarehouse; dId++) {
            rs = executeQuery(stat, "SELECT MIN(NO_O_ID) FROM NEW_ORDER"
                    + TPCC_id + " WHERE NO_D_ID=" + dId + " AND NO_W_ID="
                    + warehouseId);

            int noId = -1;
            if (rs.next()) {
                noId = rs.getInt(1);
                if (rs.wasNull()) {
                    noId = -1;
                }
            }
            rs.close();
            if (noId != -1) {
                executeUpdate(conn, stat, "DELETE FROM NEW_ORDER" + TPCC_id
                        + " WHERE NO_O_ID=" + noId + " AND NO_D_ID=" + dId
                        + " AND NO_W_ID=" + warehouseId);
                rs = executeQuery(stat, "SELECT O_C_ID FROM ORDERS" + TPCC_id
                        + " WHERE O_ID=" + noId + " AND O_D_ID=" + dId
                        + " AND O_W_ID=" + warehouseId);
                rs.next();
                // o_c_id
                rs.getInt(1);
                rs.close();
                executeUpdate(conn, stat, "UPDATE ORDERS" + TPCC_id
                        + " SET O_CARRIER_ID=" + carrierId + " WHERE O_ID="
                        + noId + " AND O_D_ID=" + dId + " AND O_W_ID="
                        + warehouseId);
                executeUpdate(conn, stat, "UPDATE ORDER_LINE" + TPCC_id
                        + " SET OL_DELIVERY_D=" + "\'" + datetime + "\'"
                        + " WHERE OL_O_ID=" + noId + " AND OL_D_ID=" + dId
                        + " AND OL_W_ID=" + warehouseId);
                rs = executeQuery(stat, "SELECT SUM(OL_AMOUNT) FROM ORDER_LINE"
                        + TPCC_id + "  WHERE OL_O_ID=" + noId + " AND OL_D_ID="
                        + dId + " AND OL_W_ID=" + warehouseId);
                rs.next();
                BigDecimal amount = rs.getBigDecimal(1);
                rs.close();
                executeUpdate(conn, stat, "UPDATE CUSTOMER" + TPCC_id
                        + " SET C_BALANCE=C_BALANCE+" + amount + " WHERE C_ID="
                        + noId + " AND C_D_ID=" + dId + " AND C_W_ID="
                        + warehouseId);
            }
        }
        commit(conn);
    }

    private void processStockLevel(Connection conn, Statement stat)
            throws Exception {
        int dId = (terminalId % TPCC_Benchmark.districtsPerWarehouse) + 1;
        int threshold = random.getInt(10, 20);
        ResultSet rs;

        executeUpdate(conn, stat, "UPDATE DISTRICT" + TPCC_id
                + " SET D_NEXT_O_ID=-1 WHERE D_ID=-1");

        rs = executeQuery(stat, "SELECT D_NEXT_O_ID FROM DISTRICT" + TPCC_id
                + " WHERE D_ID=" + dId + " AND D_W_ID=" + warehouseId);
        rs.next();
        int oId = rs.getInt(1);
        rs.close();
        rs = executeQuery(stat, "SELECT COUNT(DISTINCT S_I_ID) "
                + "FROM ORDER_LINE"
                + TPCC_id
                + ", STOCK"
                + TPCC_id
                + " WHERE "
                + "OL_W_ID="
                + warehouseId
                + " AND "
                + "OL_D_ID="
                + dId
                + " AND "
                + "OL_O_ID<"
                + oId
                + " AND "
                + "OL_O_ID>="
                + oId
                + "-20 AND "
                + "S_W_ID="
                + warehouseId
                + " AND "
                + "S_I_ID=OL_I_ID AND "
                + "S_QUANTITY<" + threshold);
        rs.next();
        // stockCount
        rs.getInt(1);
        rs.close();
        commit(conn);
    }

    private void executeUpdate(Connection con, Statement stat, String sql)
            throws Exception {
        TPCC_Benchmark.nbUpdates.incrementAndGet();

        int nbRetry = 10;
        while (nbRetry > 0) {
            try {
                stat.executeUpdate(sql);
            } catch (SQLException e) {
                System.err.println("executeUpdate raised an exception");
                // e.printStackTrace();
                con.rollback();
                nbRetry--;
                if (nbRetry <= 0) {
                    throw new Exception(
                            "executeUpdate failed 10 times executing: " + sql);
                }
                continue;
            }
            break;
        }
    }

    private ResultSet executeQuery(Statement stat, String sql)
            throws SQLException {
        TPCC_Benchmark.nbSelects.incrementAndGet();
        return stat.executeQuery(sql);
    }

    private void commit(Connection conn) throws SQLException {
        conn.commit();
        TPCC_Benchmark.nbCommits.incrementAndGet();
    }

    private void rollback(Connection conn) throws SQLException {
        conn.rollback();
        TPCC_Benchmark.nbRollbacks.incrementAndGet();
    }
}
