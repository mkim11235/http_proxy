import java.net.*;
import java.util.*;
import java.io.*;

public class Thread {
	public static final int PORT_NO = 45678;
	public static final int BUFFER_SIZE = 32768;

	public static void main(String[] args) throws Exception {
		ServerSocket serverSocket = new ServerSocket(PORT_NO);
		Socket socket = serverSocket.accept();

		BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
		DataOutputStream out = new DataOutputStream(socket.getOutputStream());

		String line = in.readLine();
		System.out.println(line);

		URL url = new URL(getURLString(line));
		HttpURLConnection conn = (HttpURLConnection) url.openConnection();

		// parse request line
		int index = line.indexOf(' ');
		String key = line.substring(0, index);
		String value = line.substring(index + 1);
		//conn.setRequestProperty(key, value);
		line = in.readLine();
		while (line != null) {			// why have to check if line equals ""? checking for CRLF?
			if (line.equals("")) {
				System.out.println("reached CRLF");
				break;
			}
			if (line.equals("Connection: keep-alive")) {
				line = "Connection: closed";
			}

			index = line.indexOf(' ');
			key = line.substring(0, index - 1);
			value = line.substring(index + 1);

			conn.addRequestProperty(key, value);

			// process next line
			line = in.readLine();
		}

		// LEFT OFF HERE
		// getting error code 302

		int responseCode = conn.getResponseCode();
		System.out.println("resp code is " + responseCode);

		// handle 302
		if (responseCode == 302) {
			String redir = conn.getHeaderField("Location");
			String cookies = conn.getHeaderField("Set-Cookie");

			conn = (HttpURLConnection) new URL(redir).openConnection();
			conn.setRequestProperty("Cookie", cookies);
		}

		BufferedReader inServer = new BufferedReader(new InputStreamReader(conn.getInputStream()));
		String inputLine;
		StringBuffer response = new StringBuffer();

		while ((inputLine = inServer.readLine()) != null) {
			response.append(inputLine);
		}
		//System.out.println(response.toString());

		out.write(response.toString().getBytes());

		//---------------------------------

		System.out.println("reached end");
	}

	private static String getURLString(String line) {
		return line.split(" ")[1];
	}
}
