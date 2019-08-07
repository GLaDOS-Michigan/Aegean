package Applications.tpcw_new.request_player;


/**
 * A request contains: -date -called method -browsing session id -interaction id
 * -transaction id -statement -arguments
 */
public class Request {
    private String date;
    private String calledMethod;
    private int bsid;
    private int iid;
    private int tid;
    private int shopping_id;
    private long execTime; // execution time of the request, in ?
    private String statement;
    private int statementCode;
    private String arguments;

    /**
     * Create a new Request from a string. Set the statementCode using
     * DBStatements
     *
     * @param req the string from which the Request is created
     */
    public Request(String req, DBStatements dbStatement) {
        String[] header = null;

        // System.out.println("The request is: " + req);

        int posStartArgs = req.indexOf(RequestPlayerUtils.START_PARAM_TAG);
        if (posStartArgs == -1) {
            header = req.split("\t");
            arguments = "";
        } else {
            header = (req.substring(0, posStartArgs - 1)).split("\t");
            arguments = req.substring(posStartArgs);
        }

        // pattern for reqPattern0: HH:MM:SS,mmm method_name browsing_session_id
        // interaction_id transaction_id statement arguments
        if (RequestPlayerUtils.requestPatternVersion == RequestPlayerUtils.reqPattern0) {
            date = header[0].trim();
            execTime = 0;
            calledMethod = header[1].trim();
            bsid = Integer.parseInt(header[2].trim());
            iid = Integer.parseInt(header[3].trim());
            tid = Integer.parseInt(header[4].trim());
            shopping_id = -1;
            statement = header[5].trim();
        }

        // pattern for reqPattern1: HH:MM:SS,mmm method_name browsing_session_id
        // interaction_id transaction_id SHOPPING_ID statement arguments
        else if (RequestPlayerUtils.requestPatternVersion == RequestPlayerUtils.reqPattern1) {
            date = header[0].trim();
            execTime = 0;
            calledMethod = header[1].trim();
            bsid = Integer.parseInt(header[2].trim());
            iid = Integer.parseInt(header[3].trim());
            tid = Integer.parseInt(header[4].trim());
            shopping_id = Integer.parseInt(header[5].trim());
            statement = header[6].trim();
        }

        // pattern for reqPattern2: HH:MM:SS,mmm execTime_ns method_name browsing_session_id
        // interaction_id transaction_id SHOPPING_ID statement arguments
        else if (RequestPlayerUtils.requestPatternVersion == RequestPlayerUtils.reqPattern2) {
            date = header[0].trim();
            execTime = Long.parseLong(header[1].trim());
            calledMethod = header[2].trim();
            bsid = Integer.parseInt(header[3].trim());
            iid = Integer.parseInt(header[4].trim());
            tid = Integer.parseInt(header[5].trim());
            shopping_id = Integer.parseInt(header[6].trim());
            statement = header[7].trim();
        }
        //System.out.println(statement);
        statementCode = dbStatement.convertStatementToCode(statement);

        // System.out.println(this.toString());
    }

    /**
     * Create a new Request from a string.
     *
     * @param req the string from which the Request is created
     */
    public Request(String req) {
        String[] header = null;

        // System.out.println("The request is: " + req);

        // pattern: HH:MM:SS,mmm method_name browsing_session_id interaction_id
        // transaction_id statement arguments
        int posStartArgs = req.indexOf(RequestPlayerUtils.START_PARAM_TAG);
        if (posStartArgs == -1) {
            header = req.split("\t");
            arguments = "";
        } else {
            header = (req.substring(0, posStartArgs - 1)).split("\t");
            arguments = req.substring(posStartArgs);
        }

        date = header[0].trim();
        calledMethod = header[1].trim();
        bsid = Integer.parseInt(header[2].trim());
        iid = Integer.parseInt(header[3].trim());
        tid = Integer.parseInt(header[4].trim());
        statement = header[5].trim();
        statementCode = -1;

        // System.out.println(this.toString());
    }

    public String getDate() {
        return date;
    }

    public String getMethod() {
        return calledMethod;
    }

    public String getStatement() {
        return statement;
    }

    public String getArguments() {
        return arguments;
    }

    public int getBrowsingSessionId() {
        return bsid;
    }

    public int getInteractionId() {
        return iid;
    }

    public int getTransactionId() {
        return tid;
    }

    public int getStatementCode() {
        return statementCode;
    }

    public int getShoppingId() {
        return shopping_id;
    }

    public long getExecTime() {
        return execTime;
    }

    public String toString() {
        String result;

        result = "date: " + date + ", exec time: " + execTime + "s, called method: " + calledMethod
                + ", browsing session id: " + bsid + ", interaction id: " + iid
                + ", transaction id: " + tid + ", shopping id:" + shopping_id
                + ", statement code: " + statementCode + ", statement: "
                + statement + ", arguments: " + arguments;

        return result;
    }

}
