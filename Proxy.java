import java.net.*;

public class Proxy {
	private static final int PORT_NO = 45678;

	public static void main(String[] args) throws Exception {
		//ServerSocket serverSocket = new ServerSocket(PORT_NO);
		ServerSocket serverSocket = new ServerSocket(Integer.parseInt(args[0]));

		while (true) {
			new ProxyThread(serverSocket.accept()).start();
		}
	}
}
