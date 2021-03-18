import java.rmi.server.UnicastRemoteObject;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;

public class Server {
	public static void main(String args[]) {
		try {
			Registry registry = LocateRegistry.getRegistry();

			// Instantiate the remotely accessible object. The constructor
			// of the object automatically exports it for remote invocation.
			SearcherImpl searcherStub = new SearcherImpl();
			UnicastRemoteObject.exportObject(searcherStub, 0);
			registry.rebind("Searcher", searcherStub);

			ServerNodeFactoryImpl nodeFactoryStub = new ServerNodeFactoryImpl();
			registry.rebind("NodeFactory", nodeFactoryStub);

			// The virtual machine will not exit here because the export of
			// the remotely accessible object creates a new thread that
			// keeps the application active.
		} catch (Exception e) {
			System.out.println("Server Exception: " + e.getMessage());
			e.printStackTrace();
		}
	}
}
