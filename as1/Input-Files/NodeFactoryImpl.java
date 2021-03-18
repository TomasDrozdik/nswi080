import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;

class ServerNodeFactoryImpl extends UnicastRemoteObject implements NodeFactory {
	public ServerNodeFactoryImpl() throws RemoteException {
		super();
	}

	@Override
	public Node createNode() throws RemoteException {
		Node node = new NodeImpl();
		UnicastRemoteObject.exportObject(node, 1234);
		return node;
	}
}

class ClientNodeFactoryImpl implements NodeFactory {
	@Override
	public Node createNode() throws RemoteException {
		return new NodeImpl();
	}
}
