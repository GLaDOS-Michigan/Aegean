package Applications.tpcw_new.request_player;

/* Manage statistics about the DB:
 * number of transactions/commits/rollback/errors
 */
public class DatabaseStats {
    private static int nbTransactions = 0;
    private static int nbCommits = 0;
    private static int nbRollback = 0;
    private static int nbErrors = 0;
    private static int nbReads = 0;
    private static int nbWrites = 0;

    public synchronized static void addTransaction() {
        nbTransactions++;
    }

    public synchronized static void addCommit() {
        nbCommits++;
    }

    public synchronized static void addRollback() {
        nbRollback++;
    }

    public synchronized static void addErrors() {
        nbErrors++;
    }

    public synchronized static void addWrite() {
        nbWrites++;
    }

    public synchronized static void addRead() {
        nbReads++;
    }

    public synchronized static int getTransaction() {
        return nbTransactions;
    }

    public synchronized static int getCommit() {
        return nbCommits;
    }

    public synchronized static int getRollback() {
        return nbRollback;
    }

    public synchronized static int getErrors() {
        return nbErrors;
    }

    public synchronized static int getWrites() {
        return nbWrites;
    }

    public synchronized static int getReads() {
        return nbReads;
    }
}