import java.io.*;
import java.net.*;
import java.util.*;

public class ProxyThread extends Thread {
	private Socket clientSocket;

	public ProxyThread(Socket serverSocket) {
		this.clientSocket = serverSocket;
	}

	public void run() {
		outside:
		try {
			BufferedReader fromClient = new BufferedReader(new InputStreamReader(
									clientSocket.getInputStream()));
			DataOutputStream toClient = new DataOutputStream(clientSocket.getOutputStream());

			// Read the request line
			String line = fromClient.readLine();
			String[] tokens = line.split(" ");
			String firstLine = line;
			// Prints first line
			System.out.println(tokens[0] + " " + tokens[1]);

			// HTTP CONNECT Tunneling
			// CONNECT www.google.com:443 HTTP/1.1
			if (tokens[0].equals("CONNECT")) {
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


				System.out.println("Writing request to server");
				System.out.println(line);
				toConnectServer.write(line.getBytes());

				String connectResponse = "";
				while (true) {
					connectResponse = fromConnect.readLine();
					System.out.println("Receieved response from server:");
					System.out.println(connectResponse);
					if (connectResponse != null) {
						toClient.write(connectResponse.getBytes());
						break;
					}
				}
				System.out.println("wrote to client");

				//System.out.println("Connection established");
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
			System.out.println("Forwarding response to client");
			BufferedReader fromServer = new BufferedReader(new InputStreamReader(
											serverSocket.getInputStream()));
			StringBuffer response = new StringBuffer();
			boolean reachedEnd = false;
			while (!reachedEnd && (line = fromServer.readLine()) != null) {
				if (response.equals("")) {
					response.append("\r\n\r\n");
				}
				if (line.endsWith("</HTML>")) {
					reachedEnd = true;
				}

				response.append(line + "\n");
			}
			System.out.println(response);
			toClient.write(response.toString().getBytes());
			System.out.println("forwarded to client");
			serverSocket.close();

			// getting error code 302
			//System.out.println("resp code is " + responseCode);

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
