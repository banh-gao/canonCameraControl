import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.net.URI;
import javax.mail.BodyPart;
import javax.mail.MessagingException;
import javax.mail.internet.MimeMultipart;
import javax.mail.util.ByteArrayDataSource;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.impl.client.HttpClients;
import org.w3c.dom.Document;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

public class MediaElement {

	private static final String DOWNLOAD_MEDIA_PATH = "/MobileConnectedCamera/ObjData";

	private final HttpClient client;
	private final URIBuilder uriBuilder;
	private final int id;
	private final String type;

	public MediaElement(String host, int id, String type) {
		this.id = id;
		this.type = type;
		client = HttpClients.createDefault();
		uriBuilder = new URIBuilder().setScheme(Camera.DEF_SCHEME).setHost(host).setPort(Camera.PORT);
	}

	public InputStream download() throws Exception {
		int offset = 0;
		uriBuilder.setPath(DOWNLOAD_MEDIA_PATH);
		uriBuilder.setParameter("ObjID", String.valueOf(id));
		uriBuilder.setParameter("ObjType", type);

		FileOutputStream o = new FileOutputStream("test.jpg");

		while (offset >= 0) {
			URI reqUri;
			uriBuilder.setParameter("Offset", String.valueOf(offset));
			reqUri = uriBuilder.build();

			HttpGet req = new HttpGet(reqUri);

			HttpResponse resp = client.execute(req);
			InputStream i = resp.getEntity().getContent();

			MimeMultipart parts = new MimeMultipart(new ByteArrayDataSource(i, resp.getEntity().getContentType().getValue()));

			InputStream image = (InputStream) parts.getBodyPart(1).getContent();

			int readed = 0;
			byte[] buf = new byte[2048];
			while ((readed = image.read(buf)) >= 0) {
				o.write(buf, 0, readed);
			}

			offset = detectNextOffset(parts.getBodyPart(0), offset);
		}

		o.close();

		return null;
	}

	private int detectNextOffset(BodyPart metaPart, int currentOffset) throws IOException, MessagingException, SAXException, ParserConfigurationException {
		Document d = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(new InputSource(new StringReader((String) metaPart.getContent())));
		int totalSize = Integer.parseInt(d.getElementsByTagName("TotalSize").item(0).getTextContent());
		int currentSize = Integer.parseInt(d.getElementsByTagName("DataSize").item(0).getTextContent());
		int nextOffset = currentOffset + currentSize;

		if (nextOffset < totalSize)
			return nextOffset;
		else
			return -1;
	}

	@Override
	public String toString() {
		return "MediaElement [id=" + id + ", type=" + type + "]";
	}

}
