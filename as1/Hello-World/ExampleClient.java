import java.rmi.Naming;

public class ExampleClient
{
	static Example oServer = null;

	public static void main (String args [])
	{
		try
		{
			// Use the registry on this host to find the server.
			// The host name must be changed if the server uses
			// another computer than the client!
			oServer = (Example) Naming.lookup ("//localhost/HelloServer");

			// Query local and remote time
			long iLocalTime = System.nanoTime ();
			long iRemoteTime = oServer.getTime ();

			// Display both values locally and remotely
			System.out.println ("Local time:  " + iLocalTime);
			System.out.println ("Remote time: " + iRemoteTime);
			oServer.putString ("Local time:  " + iLocalTime);
			oServer.putString ("Remote time: " + iRemoteTime);
		}
		catch (Exception e)
		{
			System.out.println ("Client Exception: " + e.getMessage ());
			e.printStackTrace ();
		}
	}
}
