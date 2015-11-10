import java.io.*;
import java.net.*;
import java.util.*;

public class ProxyThread extends Thread {
	private Socket clientSocket;

	public ProxyThread(Socket serverSocket) {
		clientSocket = serverSocket;
	}

	public void run() {
		outside:
		try {
			BufferedReader fromClient = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
			DataOutputStream toClient = new DataOutputStream(clientSocket.getOutputStream());

		//	String host = null;
		//	int port = -1;

			// Read the request line
			String line = fromClient.readLine();
			String firstLine = line;
			// Prints first line
			String[] tokens = line.split(" ");
			System.out.println(tokens[0] + " " + tokens[1]);

			// HTTP CONNECT Tunneling
			// CONNECT www.google.com:443 HTTP/1.1
			if (tokens[0].equals("CONNECT")) {
				if (tokens[0].equals("CONNECT")) {
					System.out.println("Canno ahndle CONNECT");
					break outside;
				}
				String[] connectTokens = tokens[1].split(":");
				String connectHost = connectTokens[0];
				int connectPort = 443;
				if (connectTokens.length == 2) {
					connectPort = Integer.parseInt(connectTokens[1]);
				}

				Socket connectSocket = new Socket();
				connectSocket.connect(new InetSocketAddress(connectHost, connectPort));
				DataOutputStream toConnectServer = new DataOutputStream(
													connectSocket.getOutputStream());
				DataInputStream fromConnectServer = new DataInputStream(
													connectSocket.getInputStream());
				BufferedReader fromConnect = new BufferedReader(new InputStreamReader(
													connectSocket.getInputStream()));

				PrintWriter toConnect = new PrintWriter(connectSocket.getOutputStream(), true);

				System.out.println("Writing request to server");
				//toConnectServer.write(line.getBytes());
				toConnect.println("CONNECT www.google.com:443 HTTP/1.1");
				toConnect.println("User-Agent: Mozilla/5.0 (X11; Fedora; Linux x86_64; rv:42.0) Gecko/20100101 Firefox/42.0");
				toConnect.println("Proxy-Connection: keep-alive");
				toConnect.println("Connection: keep-alive");
				toConnect.println("Host: www.google.com:443");

				String connectResponse = "";
				while (true) {
					connectResponse = fromConnect.readLine();
					//System.out.println("Receieved response from server:");
					//System.out.println(connectResponse);
					if (connectResponse != null) {
						toClient.write(connectResponse.getBytes());
						break;
					}
				}

				/*
				while ((line = fromClient.readLine()) != null) {
					toConnectServer.write(line.getBytes());
				}
				*/
				break outside;
			}

			//--------------------------------------------------------------------------------------
			// Non-CONNECT HTTP requests
			// When the browser sends an HTTP request to your proxy, you need to forward it on to the origin web server. 
			// You determine the web server by recognizing the Host line in the HTTP header. 
			// You should be insensitive to the case of the keyword Host, 
			// and you should be tolerant of white space anywhere it might plausibly appear. 
			// In general, the host name may be given as hostname:port or ip:port. 
			// If no port is specified, you should look for one in the URI given on the request line (the first line of the header). 
			// If there is no port there either, you should use 80 if the transport on the request line is either missing or is (case-insensitive) 'http://' and 443 if the transport is 'https://'.

			// Process fromClient and form response
			// Extract host and port number
			StringBuffer request = new StringBuffer();
			String host = null;
			int port = 80;

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
						int index = firstLine.indexOf(':');
						index = firstLine.indexOf(':', index + 1);

						if (index != -1) {
							port = Integer.parseInt(firstLine.substring(index + 1, 
									firstLine.indexOf('/', index + 1))); 
						}
					}
				}

				request.append(line + "\n");
				// process next line
				line = fromClient.readLine();
			}
			request.append("\r\n\r\n");

			// Connect to server
			// Forward request
			Socket serverSocket = new Socket();
			serverSocket.connect(new InetSocketAddress(host, port));
			PrintWriter toServer = new PrintWriter(serverSocket.getOutputStream(), true);
			
			toServer.println(request.toString());

			// Get response from server
			// Forward to client
			//System.out.println("Forwarding response to client");
			BufferedReader fromServer = new BufferedReader(new InputStreamReader(
											serverSocket.getInputStream()));
			StringBuffer response = new StringBuffer();
			boolean reachedEnd = false;
			while (!reachedEnd && (line = fromServer.readLine()) != null) {
				if (line.endsWith("</HTML>")) {
					reachedEnd = true;
				}

				/* may not need to append CRLF
				if (response.equals("")) {
					response.append("\r\n\r\n");
				}
				*/
				response.append(line + "\n");
				//toClient.write((line + "\n").getBytes());
				//System.out.println(line);
			}
			toClient.write(response.toString().getBytes());
			//System.out.println("forwarded to client");
			serverSocket.close();

			// handle 302
			/*
			if (responseCode == 302) {
				String redir = conn.getHeaderField("Location");
				String cookies = conn.getHeaderField("Set-Cookie");

				conn = (HttpURLConnection) new URL(redir).openConnection();
				conn.setRequestProperty("Cookie", cookies);
			}
			*/

			//---------------------------------

			//System.out.println("reached end");
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	private static String getURLString(String line) {
		return line.split(" ")[1];
	}
}
