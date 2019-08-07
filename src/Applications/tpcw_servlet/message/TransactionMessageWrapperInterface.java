package Applications.tpcw_servlet.message;

import java.io.Externalizable;

public interface TransactionMessageWrapperInterface extends Externalizable {
    String getTransactionType();

    Object getMessageData();
}
