import java.io.*;
import java.net.*;
import java.util.*;
import java.nio.charset.StandardCharsets;
import java.util.zip.GZIPInputStream;

public class ProxyThread extends Thread {
	private Socket client;

	public ProxyThread(Socket socket) {
		client = socket;
	}

	public void run() {
		outside:
		try {
			BufferedReader fromClient = new BufferedReader(new InputStreamReader(client.getInputStream()));
			DataOutputStream toClient = new DataOutputStream(client.getOutputStream());

			Socket server = new Socket();

			String host = null;
			int port = -1;

			// Read the request line
			String line = fromClient.readLine();
			String firstLine = line;

			if (line == null) {
				break outside;
			}

			// Prints first line
			String[] tokens = line.split(" ");
			System.out.println(">>> " + tokens[0] + " " + tokens[1]);

			// HTTP CONNECT Tunneling
			if (tokens[0].equals("CONNECT")) {
				// Extract host and port
				while (line != null && !line.equals("")) {
					if (line.contains("keep-alive")) {
						line = line.replaceAll("keep-alive", "close");
					}
					if (line.contains("HTTP/1.1")) {
						line = line.replaceAll("HTTP/1.1", "HTTP/1.0");
					}

					if (line.toLowerCase().startsWith("host")) {
						String[] hostTokens = line.split(":");
						host = hostTokens[1].trim();
						if (hostTokens.length > 2) {
							port = Integer.parseInt(hostTokens[2]);
						} else {
							URI uri = new URI(firstLine.split(" ")[1]);
							port = uri.getPort();
							if (port == -1) {
								port = 443;
							}
						}
					}
					line = fromClient.readLine();
				}

				// Connect to server
				try {
					server.connect(new InetSocketAddress(host, port));
				} catch (Exception e) { 
					// Fails to connect to server
					toClient.write("HTTP/1.0 502 Bad Gateway".getBytes());
					toClient.write("\r\n\r\n".getBytes());
					e.printStackTrace();
				}

				// Successfully connected to server
				// Maybe change the message to OK
				try {
					toClient.write("HTTP/1.0 200 OK".getBytes());
					toClient.write("\r\n\r\n".getBytes());
				} catch (Exception e) {}

				final byte[] request = new byte[4096];
				byte[] response = new byte[4096];

				final InputStream from_c = client.getInputStream();
				final OutputStream to_c = client.getOutputStream();

				final InputStream from_s = server.getInputStream();
				final OutputStream to_s = server.getOutputStream();

				Thread clientThread = new Thread() {
					public void run() {
						int bytesRead;
						try {
							while ((bytesRead = from_c.read(request)) != -1) {
								to_s.write(request, 0, bytesRead);
								to_s.flush();
							}
						} catch (Exception e) {}
					}
				};
				clientThread.start();

				int bytesRead2;
				try {
					while ((bytesRead2 = from_s.read(response)) != -1) {
						to_c.write(response, 0, bytesRead2);
						to_c.flush();
					}
				} catch (Exception e) {}
				server.close();
				client.close();

				break outside;
			}

			//--------------------------------------------------------------------------------------
			// Non-CONNECT HTTP requests

			// Process fromClient and form response
			// Extract host and port number
			StringBuffer request = new StringBuffer();

			while (line != null && !line.equals("")) {
				if (line.contains("keep-alive")) {
					line = line.replaceAll("keep-alive", "close");
				}
				if (line.contains("HTTP/1.1")) {
					line = line.replaceAll("HTTP/1.1", "HTTP/1.0");
				}

				// Extract host and port
				if (line.toLowerCase().startsWith("host")) {
					String[] hostTokens = line.split(":");
					host = hostTokens[1].trim();
					if (hostTokens.length > 2) {
						port = Integer.parseInt(hostTokens[2]);
					} else {
						URI uri = new URI(firstLine.split(" ")[1]);
						port = uri.getPort();
					}
				}

				request.append(line + "\n");
				// process next line
				line = fromClient.readLine();
			}
			request.append("\r\n\r\n");

			if (port == -1) {
				port = 80;
			}

			// Forward request
			server.connect(new InetSocketAddress(host, port));
			PrintWriter toServer = new PrintWriter(server.getOutputStream(), true);
			toServer.println(request.toString());

			// Get response from server
			// Forward to client
			BufferedReader fromServer = new BufferedReader(new InputStreamReader(server.getInputStream()));
			boolean reachedEnd = false;
			while (!reachedEnd && (line = fromServer.readLine()) != null) {
				if (line.endsWith("</HTML>")) {
					reachedEnd = true;
				}

				// Ignore Broken Pipe Exceptions
				// Browser no longer wants to hear back from server
				try {
					//System.out.println(line);
					toClient.write((line + "\n").getBytes());
					toClient.flush();
				} catch (Exception e) {}
			}
			server.close();
			client.close();

			//---------------------------------

		} catch (Exception e) {
			e.printStackTrace();
		}
	}
}
