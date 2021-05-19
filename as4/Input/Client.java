import com.hazelcast.client.HazelcastClient;
import com.hazelcast.client.config.ClientConfig;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.core.IExecutorService;
import com.hazelcast.map.IMap;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.LineNumberReader;
import java.util.concurrent.Future;

public class Client {
	// Reader for user input
	private LineNumberReader in = new LineNumberReader(new InputStreamReader(System.in));
	// Connection to the cluster
	private HazelcastInstance hazelcast;
	// The name of the user
	private String userName;
	// Do not keep any other state here - all data should be in the cluster
	private IExecutorService executor;

	private IMap<String, ClientProfile> clientProfiles;
	private IMap<String, Document> documentMetadata;

	/**
	 * Create a client for the specified user.
	 * @param userName user name used to identify the user
	 */
	public Client(String userName) {
		this.userName = userName;
		// Connect to the Hazelcast cluster
		ClientConfig config = new ClientConfig();
		hazelcast = HazelcastClient.newHazelcastClient(config);
		executor = hazelcast.getExecutorService("exec");

		this.clientProfiles = hazelcast.getMap("clientProfiles");
		this.documentMetadata = hazelcast.getMap("documentMetadata");

		System.out.print("Logging in as\"" + userName + "\"");
		clientProfiles.putIfAbsent(userName, new ClientProfile(userName));
		System.out.print(" ... done!");
	}

	/**
	 * Disconnect from the Hazelcast cluster.
	 */
	public void disconnect() {
		// Disconnect from the Hazelcast cluster
		hazelcast.shutdown();
	}

	private Document loadDocument(String documentName) {
		Future<Document> future = executor.submit(new DocumentGetTask(userName, documentName));
		System.out.print("Loading document \"" + documentName + "\" ");
		try {
			while (!future.isDone()) {
				System.out.print(".");
				Thread.sleep(300);
			}
			System.out.println(" done!");
			return future.get();
		} catch (Exception e) {
			e.printStackTrace();
			return null;
		}
	}

	/**
	 * Read a name of a document,
	 * select it as the current document of the user
	 * and show the document content.
	 */
	private void showCommand() throws Exception {
		System.out.println("Enter document name:");
		String documentName = in.readLine();

		Document document = loadDocument(documentName);
		if (document == null) {
			System.out.println("Document failed to load.");
			return;
		}

		// Show the document content
		System.out.println("The document is:");
		System.out.println(document.getContent());
	}

	/**
	 * Show the next document in the list of favorites of the user.
	 * Select the next document, so that running this command repeatedly
	 * will cyclically show all favorite documents of the user.
	 */
	private void nextFavoriteCommand() {
		String documentName = clientProfiles.executeOnKey(userName, new FavoriteNextEntryProcessor());
		if (documentName == null) {
			System.out.println("There is no favorite document.");
			return;
		}

		Document document = loadDocument(documentName);
		if (document == null) {
			System.out.println("Document failed to load.");
			return;
		}

		// Show the document content
		System.out.println("The document is:");
		System.out.println(document.getContent());
	}

	/**
	 * Add the current selected document name to the list of favorite documents of the user.
	 * If the list already contains the document name, do nothing.
	 */
	private void addFavoriteCommand() {
		String selectedDocument = clientProfiles.executeOnKey(userName, new FavoriteAddEntryProcessor());
		if (selectedDocument == null) {
			System.out.println("There is no selectedDocument.");
			return;
		}
		System.out.printf("Added %s to favorites%n", selectedDocument);
	}

	/**
	 * Remove the current selected document name from the list of favorite documents of the user.
	 * If the list does not contain the document name, do nothing.
	 */
	private void removeFavoriteCommand(){
		String selectedDocument = clientProfiles.executeOnKey(userName, new FavoriteRemoveEntryProcessor());
		if (selectedDocument == null) {
			System.out.println("There is no selectedDocument.");
			return;
		}
		System.out.printf("Removed %s from favorites%n", selectedDocument);
	}

	/**
	 * Add the current selected document name to the list of favorite documents of the user.
	 * If the list already contains the document name, do nothing.
	 */
	private void listFavoritesCommand() {
		ClientProfile profile = clientProfiles.get(userName);
		// Print the list of favorite documents
		System.out.println("Your list of favorite documents:");
		for(String favoriteDocumentName: profile.getFavorites()) {
			System.out.println(favoriteDocumentName);
		}
	}

	/**
	 * Show the view count and comments of the current selected document.
	 */
	private void infoCommand(){
		ClientProfile profile = clientProfiles.get(userName);
		String selectedDocument = profile.getSelectedDocument();
		if (selectedDocument == null) {
		    System.out.println("There is no selectedDocument.");
			return;
		}

		DocumentInfo documentInfo = documentMetadata.get(selectedDocument);

		System.out.printf("Info about %s:%n", documentInfo.getName());
		System.out.printf("Viewed %d times.%n", documentInfo.getViewCount());
		System.out.printf("Comments (%d):%n", documentInfo.getComments().size());
		for(String comment: documentInfo.getComments()) {
			System.out.println(comment);
		}
	}

	/**
	 * Add a comment about the current selected document.
	 */
	private void commentCommand() throws IOException{
		System.out.println("Enter comment text:");
		String commentText = in.readLine();
		String selectedDocument = clientProfiles.get(userName).getSelectedDocument();
		if (selectedDocument == null) {
			System.out.println("There is no selectedDocument.");
			return;
		}

		documentMetadata.executeOnKey(selectedDocument, new DocumentCommentEntryProcessor(commentText));
		System.out.printf("Added a comment about %s.%n", selectedDocument);
	}

	/*
	 * Main interactive user loop
	 */
	public void run() throws Exception {
		loop:
		while (true) {
			System.out.println("\nAvailable commands (type and press enter):");
			System.out.println(" s - select and show document");
			System.out.println(" i - show document view count and comments");
			System.out.println(" c - add comment");
			System.out.println(" a - add to favorites");
			System.out.println(" r - remove from favorites");
			System.out.println(" n - show next favorite");
			System.out.println(" l - list all favorites");
			System.out.println(" q - quit");
			// read first character
			int c = in.read();
			// throw away rest of the buffered line
			while (in.ready())
				in.read();
			switch (c) {
				case 'q': // Quit the application
					break loop;
				case 's': // Select and show a document
					showCommand();
					break;
				case 'i': // Show view count and comments of the selected document
					infoCommand();
					break;
				case 'c': // Add a comment to the selected document
					commentCommand();
					break;
				case 'a': // Add the selected document to favorites
					addFavoriteCommand();
					break;
				case 'r': // Remove the selected document from favorites
					removeFavoriteCommand();
					break;
				case 'n': // Select and show the next document in the list of favorites
					nextFavoriteCommand();
					break;
				case 'l': // Show the list of favorite documents
					listFavoritesCommand();
					break;
				case '\n':
				default:
					break;
			}
		}
	}

	/*
	 * Main method, creates a client instance and runs its loop
	 */
	public static void main(String[] args) {
		if (args.length != 1) {
			System.err.println("Usage: ./client <userName>");
			return;
		}

		try {
			Client client = new Client(args[0]);
			try {
				client.run();
			}
			finally {
				client.disconnect();
			}
		}
		catch (Exception e){
			e.printStackTrace();
		}
	}
}
