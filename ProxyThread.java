import java.io.*;
import java.net.*;
import java.util.*;
import java.nio.charset.StandardCharsets;
import java.util.zip.GZIPInputStream;

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

			// Read the request line
			String line = fromClient.readLine();
			String firstLine = line;
			// Prints first line
			if (line == null) {
				System.out.println("Null line");
				line = fromClient.readLine();
				break outside;
			}
			String[] tokens = line.split(" ");
			System.out.println(">>> " + tokens[0] + " " + tokens[1]);

			// HTTP CONNECT Tunneling
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

				PrintWriter toConnect = new PrintWriter(connectSocket.getOutputStream(), true);

				toClient.write("HTTP/1.0 200 Connection established".getBytes());
				toClient.write("\r\n\r\n".getBytes());

				int bytesRead;
				final byte[] request = new byte[4096];
				byte[] response = new byte[4096];

				final InputStream from_c = clientSocket.getInputStream();
				final OutputStream to_c = clientSocket.getOutputStream();

				final InputStream from_s = connectSocket.getInputStream();
				final OutputStream to_s = connectSocket.getOutputStream();

				Thread clientThread = new Thread() {
					public void run() {
						int bytesRead;
						try {
							while ((bytesRead = from_c.read(request)) != -1) {
								//System.out.println("request " + new String(request));
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
						//System.out.println("response "  + new String(response));
						to_c.write(response, 0, bytesRead2);
						to_c.flush();
					}
				} catch (Exception e) {}

				System.out.println("server done responding");
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
			//System.out.println(request);

			// Connect to server
			// Forward request
			Socket serverSocket = new Socket();
			serverSocket.connect(new InetSocketAddress(host, port));
			PrintWriter toServer = new PrintWriter(serverSocket.getOutputStream(), true);
			
			toServer.println(request.toString());

			// Get response from server
			// Forward to client
			BufferedReader fromServer = new BufferedReader(new InputStreamReader(serverSocket.getInputStream()));
			boolean reachedEnd = false;
			boolean isGZIP = false;
			boolean inBody = false;
			int count = 0;
			while (!reachedEnd && (line = fromServer.readLine()) != null) {
				if (count > 21) {
					break outside;
				}
				count++;

				StringBuilder sb = new StringBuilder();
				if (isGZIP && inBody) {
					// LEFT OFF HERE
					// PROBLEM NOT GZIP
					byte[] bytes = line.getBytes(StandardCharsets.UTF_8);
					ByteArrayInputStream bis = new ByteArrayInputStream(bytes);
					GZIPInputStream gis = new GZIPInputStream(bis);
					BufferedReader br = new BufferedReader(new InputStreamReader(gis, "UTF-8"));
					String gzipLine = null;
					while ((gzipLine = br.readLine()) != null) {
						sb.append(gzipLine);
					}
					toClient.write((sb.toString() + "\n").getBytes());
					toClient.flush();
					continue;
				}

				if (line.endsWith("</HTML>")) {
					reachedEnd = true;
				}

				if (line.contains("gzip")) {
					isGZIP = true;
				}

				if (line.equals("")) {
					inBody = true;
				}

				// Ignore Broken Pipe Exceptions
				// Browser no longer wants to hear back from server
				try {
					//System.out.println(line);
					toClient.write((line + "\n").getBytes());
					toClient.flush();
				} catch (Exception e) {}
			}
			serverSocket.close();

			//---------------------------------

		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	private static String getURLString(String line) {
		return line.split(" ")[1];
	}
}
