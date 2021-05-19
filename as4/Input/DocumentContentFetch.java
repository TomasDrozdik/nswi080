/**
 * Generator which can generate the documents by their name.
 */
public class DocumentContentFetch {
	static String fetchContent(String documentName) {
		// This method simulates a long running computation generating some document
		try {
			System.out.println("Fetching document " + documentName);
			Thread.sleep(3000);
		}
		catch (InterruptedException ie) {
			// exception ignored
		}
		return "==================================\n" +
		       "This is the document named " + documentName + "\n" +
		       "==================================";
	}
}