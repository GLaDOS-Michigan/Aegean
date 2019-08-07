package request_player.remote;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.ArrayList;

import request_player.Request;
import request_player.RequestPlayerUtils;

/* A TransactionMessage contains an array of requests and a boolean
 * which indicates whether the transaction is a read or a write
 * transaction.
 */
public class TransactionMessage implements Externalizable
{
	private boolean isRead;
	private int nbElem;
	private int[] requestsStatementCodes;
	private String[] requestsArguments;
	private int[] requestsShoppingIDs;

	/* Empty constructor, needed for deserialization */
	public TransactionMessage()
	{
		isRead = false;
		nbElem = 0;
		requestsStatementCodes = null;
		requestsArguments = null;
		requestsShoppingIDs = null;
	}

	public TransactionMessage(ArrayList<Request> transaction)
	{
		String methodName = transaction.get(0).getMethod();
		isRead = RequestPlayerUtils.methodIsRead(RequestPlayerUtils
				.string2methodName(methodName));

		nbElem = transaction.size();
		requestsStatementCodes = new int[nbElem];
		requestsArguments = new String[nbElem];
		requestsShoppingIDs = new int[nbElem];
		for (int i = 0; i < nbElem; i++)
		{
			Request r = transaction.get(i);

			requestsStatementCodes[i] = r.getStatementCode();
			requestsArguments[i] = r.getArguments();
			requestsShoppingIDs[i] = r.getShoppingId();
		}
	}

	public boolean isRead()
	{
		return isRead;
	}

	public int getNbOfRequests()
	{
		return nbElem;
	}

	public int getStatementCode(int idx)
	{
		return requestsStatementCodes[idx];
	}

	public String getArguments(int idx)
	{
		return requestsArguments[idx];
	}

	public int getShoppingID(int idx)
	{
		return requestsShoppingIDs[idx];
	}

	@Override
	public void readExternal(ObjectInput arg0) throws IOException,
			ClassNotFoundException
	{
		isRead = arg0.readBoolean();
		nbElem = arg0.readInt();

		requestsStatementCodes = new int[nbElem];
		requestsArguments = new String[nbElem];
		requestsShoppingIDs = new int[nbElem];
		for (int i = 0; i < nbElem; i++)
		{
			requestsStatementCodes[i] = arg0.readInt();
			requestsArguments[i] = arg0.readUTF();
			requestsShoppingIDs[i] = arg0.readInt();
		}
	}

	@Override
	public void writeExternal(ObjectOutput arg0) throws IOException
	{
		arg0.writeBoolean(isRead);
		arg0.writeInt(nbElem);

		for (int i = 0; i < requestsStatementCodes.length; i++)
		{
			arg0.writeInt(requestsStatementCodes[i]);
			arg0.writeUTF(requestsArguments[i]);
			arg0.writeInt(requestsShoppingIDs[i]);
		}
	}
}
