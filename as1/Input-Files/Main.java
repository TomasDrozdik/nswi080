import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.RemoteException;
import java.rmi.NotBoundException;
import java.util.Random;

public class Main {
	private static int graphNodes;
	private static int graphEdges;
	private static Random random;
	private static int transitiveStep;
	private static int transitiveEnd;
	private static boolean printHeader;

	// How many searches to perform
	private static final int SEARCHES = 40;

	private static Node[] localNodes;
	private static Node[] remoteNodes;


	/**
	 * Creates nodes of a graph.
	 *
	 * @param howMany number of nodes
	 */
	public static void createNodes(int howMany,
	                               NodeFactory localNodeFactory,
								   NodeFactory remoteNodeFactory) throws RemoteException {
		localNodes = new Node[howMany];
		remoteNodes = new Node[howMany];

		for (int i = 0; i < howMany; i++) {
			localNodes[i] = localNodeFactory.createNode();
			remoteNodes[i] = remoteNodeFactory.createNode();
		}
	}

	/**
	 * Creates a fully connected graph.
	 */
	public static void connectAllNodes() throws RemoteException {
		for (int idxFrom = 0; idxFrom < localNodes.length; idxFrom++) {
			for (int idxTo = idxFrom + 1; idxTo < localNodes.length; idxTo++) {
				localNodes[idxFrom].addNeighbor(localNodes[idxTo]);
				localNodes[idxTo].addNeighbor(localNodes[idxFrom]);

				remoteNodes[idxFrom].addNeighbor(remoteNodes[idxTo]);
				remoteNodes[idxTo].addNeighbor(remoteNodes[idxFrom]);
			}
		}
	}

	/**
	 * Creates a randomly connected graph.
	 *
	 * @param howMany number of edges
	 */
	public static void connectSomeNodes(int howMany) throws RemoteException {
		for (int i = 0; i < howMany; i++) {
			final int idxFrom = random.nextInt(localNodes.length);
			final int idxTo = random.nextInt(localNodes.length);

			localNodes[idxFrom].addNeighbor(localNodes[idxTo]);
			remoteNodes[idxFrom].addNeighbor(remoteNodes[idxTo]);
		}
	}

	/**
	 * Runs a quick measurement on the graph.
	 *
	 * @param howMany number of measurements
	 */
	public static void searchBenchmark(int howMany, Registry registry) throws RemoteException, NotBoundException {
		Searcher localSearcher = new SearcherImpl();
		Searcher remoteSearcher = (Searcher) registry.lookup("Searcher");

		// Display measurement header.
		if (printHeader) {
			System.out.printf("%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s%n",
				"attempt", "nodes", "edges", "n", "distance",
				"local_searcher-local_nodes", "local_searcher-remote_nodes", "remote_searcher-local_nodes", "remote_searcher-remote_nodes",
				"local_searcher-local_nodes-transitive", "local_searcher-remote_nodes-transitive", "remote_searcher-local_nodes-transitive", "remote_searcher-remote_nodes-transitive"
			);
		}

		// Calculate distance using either localSearcher or RemoteSeacher in combination wiht either local or remote
		// nodes. Variables are prefixed with l (local) or r (remote) where first stands for locality of searcher
		// second for locality of nodes. E.g. prefix 'll' stands for local searcher, local nodes.
		// Do the same for transitive variant of the algorithm.
		// Measure execution time.
		for (int i = 0; i < howMany; i++) {
			// Select two random nodes.
			final int idxFrom = random.nextInt(localNodes.length);
			final int idxTo = random.nextInt(localNodes.length);

			// LocalSearcher + localNodes - ll
			final long llStartTimeNs = System.nanoTime();
			final int llDistance = localSearcher.getDistance(localNodes[idxFrom], localNodes[idxTo]);
			final long llDurationNs = System.nanoTime() - llStartTimeNs;

			// LocalSearcher + remoteNodes - lr
			final long lrStartTimeNs = System.nanoTime();
			final int lrDistance = localSearcher.getDistance(remoteNodes[idxFrom], remoteNodes[idxTo]);
			final long lrDurationNs = System.nanoTime() - lrStartTimeNs;

			// RemoteSearcher + localNodes - rl
			final long rlStartTimeNs = System.nanoTime();
			final int rlDistance = remoteSearcher.getDistance(localNodes[idxFrom], localNodes[idxTo]);
			final long rlDurationNs = System.nanoTime() - rlStartTimeNs;

			// RemoteSearcher + remoteNodes - rr
			final long rrStartTimeNs = System.nanoTime();
			final int rrDistance = remoteSearcher.getDistance(remoteNodes[idxFrom], remoteNodes[idxTo]);
			final long rrDurationNs = System.nanoTime() - rrStartTimeNs;

			// Calculate transitive distance, measure operation time, try different parameters of n based on number of
			// wanted values.
			for (int n = transitiveStep; n <= transitiveEnd && n < graphNodes; n += transitiveStep) {
				// LocalSearcher + localNodes - ll transitive
				final long llStartTimeTransitiveNs = System.nanoTime();
				final int llDistanceTransitive = localSearcher.getDistanceTransitive(n, localNodes[idxFrom], localNodes[idxTo]);
				final long llDurationTransitiveNs = System.nanoTime() - llStartTimeTransitiveNs;

				// LocalSearcher + remoteNodes - lr transitive
				final long lrStartTimeTransitiveNs = System.nanoTime();
				final int lrDistanceTransitive = localSearcher.getDistanceTransitive(n, remoteNodes[idxFrom], remoteNodes[idxTo]);
				final long lrDurationTransitiveNs = System.nanoTime() - lrStartTimeTransitiveNs;

				// RemoteSearcher + localNodes - rl transitive
				final long rlStartTimeTransitiveNs = System.nanoTime();
				final int rlDistanceTransitive = remoteSearcher.getDistanceTransitive(n, localNodes[idxFrom], localNodes[idxTo]);
				final long rlDurationTransitiveNs = System.nanoTime() - rlStartTimeTransitiveNs;

				// RemoteSearcher + remoteNodes - rr transitive
				final long rrStartTimeTransitiveNs = System.nanoTime();
				final int rrDistanceTransitive = remoteSearcher.getDistanceTransitive(n, remoteNodes[idxFrom], remoteNodes[idxTo]);
				final long rrDurationTransitiveNs = System.nanoTime() - rrStartTimeTransitiveNs;

				if (llDistance != lrDistance ||
					lrDistance != rlDistance ||
					rlDistance != rrDistance) {
					System.err.printf("Inconsistent distances ll(%d), lr(%d), rl(%d), rr(%d), llT(%d), lrT(%d), rlT(%d), rrT(%d)%n",
						llDistance, lrDistance, rlDistance, rrDistance,
						llDistanceTransitive, lrDistanceTransitive, rlDistanceTransitive, rrDistanceTransitive
					);
				} else {
					// Print the measurement result.
					System.out.printf("%d,%d,%d,%d,%d,%d,%d,%d,%d,%d,%d,%d,%d%n",
						i, graphNodes, graphEdges, n, llDistance,
						llDurationNs / 1000, lrDurationNs / 1000, rlDurationNs / 1000, rrDurationNs / 1000,
						llDurationTransitiveNs / 1000, lrDurationTransitiveNs / 1000, rlDurationTransitiveNs / 1000, rrDurationTransitiveNs / 1000
					);
				}
			}
		}
	}

	public static void main(String[] args) {
		String host = (args.length < 1) ? null : args[0];
		long seed = (args.length < 2) ? System.currentTimeMillis() : Long.parseLong(args[1]);
		graphNodes = (args.length < 3) ? 100 : Integer.parseInt(args[2]);
		graphEdges = (args.length < 4) ? 50 : Integer.parseInt(args[3]);
		transitiveStep = (args.length < 5) ? 4 : Integer.parseInt(args[4]);
		transitiveEnd = (args.length < 6) ? transitiveStep * 4 + 1 : Integer.parseInt(args[5]); // inclusive
		printHeader = (args.length < 7) ? true : Boolean.parseBoolean(args[6]);

		random = new Random(seed);
		try {
			Registry registry = LocateRegistry.getRegistry(host);
			NodeFactory localNodeFactory = new ClientNodeFactoryImpl();
			NodeFactory remoteNodeFactory = (NodeFactory) registry.lookup("NodeFactory");

			// Create a randomly connected graph and do a quick measurement.
			// Consider replacing connectSomeNodes with connectAllNodes to verify that all distances are equal to one.
			createNodes(graphNodes, localNodeFactory, remoteNodeFactory);
			connectSomeNodes(graphEdges);
			//connectAllNodes();
			searchBenchmark(SEARCHES, registry);
		} catch (Exception e) {
			System.out.println ("Client Exception: " + e.getMessage ());
			e.printStackTrace ();
		}
	}
}
