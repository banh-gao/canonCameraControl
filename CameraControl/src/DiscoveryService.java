import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.MulticastSocket;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.Enumeration;
import java.util.Timer;
import java.util.TimerTask;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class DiscoveryService extends Thread {

	final InetAddress presentationAddress;

	private static final int ADV_PERIOD = 2000;

	public DiscoveryService() throws SocketException {
		NetworkInterface netIf = NetworkInterface.getNetworkInterfaces().nextElement();
		Enumeration<InetAddress> addrs = netIf.getInetAddresses();
		addrs.nextElement();
		this.presentationAddress = addrs.nextElement();
	}

	public void run() {

		try {
			startPresentationServer();
		} catch (Exception e1) {
			e1.printStackTrace();
		}

		new Timer().schedule(new TimerTask() {

			@Override
			public void run() {
				try {
					sendNotify(new InetSocketAddress(InetAddress.getByName("239.255.255.250"), 1900));
				} catch (UnknownHostException e) {
					e.printStackTrace();
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		}, 0, ADV_PERIOD);

		MulticastSocket clientSocket;
		try {
			clientSocket = new MulticastSocket(1900);
			clientSocket.joinGroup(InetAddress.getByName("239.255.255.250"));
		} catch (Exception e) {
			e.printStackTrace();
			return;
		}

		while (!isInterrupted()) {
			byte[] buf = new byte[1024];
			DatagramPacket dp = new DatagramPacket(buf, buf.length);

			try {
				clientSocket.receive(dp);
			} catch (IOException e) {
				e.printStackTrace();
				interrupt();
			}
			final String msg = new String(dp.getData(), 0, dp.getLength());

			if (msg.contains("M-SEARCH")) {
				try {
					sendNotify((InetSocketAddress) dp.getSocketAddress());
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		}

		clientSocket.close();
		try {
			sendNotify(new InetSocketAddress(InetAddress.getByName("239.255.255.250"), 1900));
		} catch (UnknownHostException e1) {
			e1.printStackTrace();
		} catch (Exception e1) {
			e1.printStackTrace();
		}
	}

	public void sendNotify(InetSocketAddress dest) throws Exception {
		DatagramSocket resSocket = new DatagramSocket(null);

		String req = "NOTIFY * HTTP/1.1\r\n";
		req += "Host: 239.255.255.250:1900 \r\n";
		req += "Location: http://" + presentationAddress.getHostAddress() + ":8080/MobileDevDesc.xml\r\n";
		req += "NT: upnp:rootdevice\r\n";
		// req +=
		// "NT: urn:schemas-canon-com:service:CameraConnectedMobileService:1\r\n";
		req += "NTS: ssdp:alive\r\n";
		req += "Server: TestApp\r\n";
		req += "USN: uuid:00000000-0000-0000-0000-3A3CC318CDF4::upnp:rootdevice\r\n\r\n";
		byte[] sendData = req.getBytes();

		DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length, dest.getAddress(), dest.getPort());
		resSocket.send(sendPacket);
		resSocket.close();
	}

	private void startPresentationServer() throws Exception {
		final ServerSocket srvSocket = new ServerSocket(8080);

		new Thread(new Runnable() {

			public void run() {

				while (!isInterrupted()) {
					try {
						Socket s = srvSocket.accept();

						BufferedReader reader = new BufferedReader(new InputStreamReader(s.getInputStream()));

						String req = reader.readLine();

						Matcher m = Pattern.compile("GET /(.*) HTTP(.*)").matcher(req);
						m.find();

						System.out.println("Requesting file " + m.group(1));

						String confXML = getXML(m.group(1));

						PrintWriter out = new PrintWriter(s.getOutputStream());
						out.write("HTTP/1.1 200 OK\r\n");
						out.write("Content-Type: text/xml\r\n");
						out.write("Date: Fri, 31 Dec 1999 23:59:59 GMT\r\n");
						out.write("Server: Apache/0.8.4\r\n");
						out.write("Expires: Sat, 01 Jan 2015 00:59:59 GMT\r\n");
						out.write("Last-modified: Fri, 09 Aug 2015 14:21:40 GMT\r\n");
						out.write("Content-Length: " + confXML.length() + "\r\n");
						out.write("\r\n");

						out.write(confXML);

						out.close();
					} catch (Exception e) {
						e.printStackTrace();
					}
				}

				// try {
				// srvSocket.close();
				// } catch (IOException e) {
				// e.printStackTrace();
				// }
			}
		}).start();
	}

	private String getXML(String filename) {

		String confXML = "";

		try {
			StringBuilder b = new StringBuilder();
			BufferedReader reader = new BufferedReader(new FileReader(filename));
			String l = "";
			while ((l = reader.readLine()) != null) {
				b.append(l);
			}
			reader.close();
			confXML = b.toString();
		} catch (Exception e) {
			e.printStackTrace();
			return confXML;
		}
		return confXML;
	}

}
