import java.io.IOException;
import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import javax.xml.parsers.DocumentBuilderFactory;
import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.HttpClients;
import org.w3c.dom.Document;

public class Camera {

	static final String DEF_SCHEME = "http";

	static final int PORT = 8615;

	private static final Charset MSG_CHARSET = Charset.forName("US-ASCII");

	private static final String CONTROL_PATH = "/MobileConnectedCamera/UsecaseStatus";

	private static final String CONNECT_MSG = "<?xml version=\"1.0\"?><ParamSet xmlns=\"urn:schemas-canon-com:service:MobileConnectedCameraService:1\"><Status>Run</Status></ParamSet>";
	private static final String DISCONNECT_MSG = "<?xml version=\"1.0\"?><ParamSet xmlns=\"urn:schemas-canon-com:service:MobileConnectedCameraService:1\"><Status>Stop</Status></ParamSet>";

	private static final String MEDIA_LIST_PATH = "/MobileConnectedCamera/ObjIDList";

	private final HttpClient client;
	private final URIBuilder uriBuilder;

	public Camera(InetAddress addr) {
		client = HttpClients.createDefault();
		uriBuilder = new URIBuilder().setScheme(DEF_SCHEME).setHost(addr.getHostAddress()).setPort(PORT);
	}

	public void connect() throws ClientProtocolException, IOException {
		sendControlMsg(CONNECT_MSG);
	}

	public void disconnect() throws ClientProtocolException, IOException {
		sendControlMsg(DISCONNECT_MSG);
	}

	private void sendControlMsg(String message) throws ClientProtocolException, IOException {
		URI reqUri;
		try {
			uriBuilder.setPath(CONTROL_PATH);
			uriBuilder.setParameter("Name", "ObjectPull");
			uriBuilder.setParameter("MajorVersion", "1");
			uriBuilder.setParameter("MinorVersion", "0");
			reqUri = uriBuilder.build();
		} catch (URISyntaxException e) {
			throw new RuntimeException(e);
		}

		HttpPost req = new HttpPost(reqUri);

		req.addHeader("Content-type", "text/xml");

		req.setEntity(new StringEntity(message, MSG_CHARSET));

		client.execute(req);
	}

	public List<MediaElement> getMediaList() throws Exception {

		int startIndex = 1;
		int totalNum = startIndex + 1;

		uriBuilder.setPath(MEDIA_LIST_PATH);
		// TODO: what is this magic num???
		uriBuilder.setParameter("MaxNum", "2147483647");
		uriBuilder.setParameter("ObjType", "ALL");

		List<MediaElement> out = new ArrayList<MediaElement>();

		while (startIndex < totalNum) {
			URI reqUri;

			uriBuilder.setParameter("StartIndex", String.valueOf(startIndex));

			try {
				reqUri = uriBuilder.build();
			} catch (URISyntaxException e) {
				throw new RuntimeException(e);
			}

			HttpGet req = new HttpGet(reqUri);
			HttpResponse resp = client.execute(req);

			Document d = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(resp.getEntity().getContent());

			totalNum = Integer.parseInt(d.getElementsByTagName("TotalNum").item(0).getTextContent());
			int answerCount = Integer.parseInt(d.getElementsByTagName("ListCount").item(0).getTextContent());

			for (int i = 1; i <= answerCount; i++) {
				int id = Integer.parseInt(d.getElementsByTagName(String.format("ObjIDList-%d", i)).item(0).getTextContent());
				String type = d.getElementsByTagName(String.format("ObjTypeList-%d", i)).item(0).getTextContent();
				out.add(new MediaElement(uriBuilder.getHost(), id, type));
			}

			startIndex += answerCount;
		}
		return out;

	}

	public static void main(String[] args) throws Exception {
		DiscoveryService srv = new DiscoveryService();
		srv.start();
		// Camera c = new Camera(InetAddress.getByName("cam.local"));
		// c.connect();
		// c.getMediaList().get(0).download();
		// c.disconnect();
	}
}
