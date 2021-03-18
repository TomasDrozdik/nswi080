import java.rmi.Remote;
import java.rmi.RemoteException;

public interface NodeFactory extends Remote {
	/**
	 * Create a new node.
	 */
	public Node createNode() throws RemoteException;
}
