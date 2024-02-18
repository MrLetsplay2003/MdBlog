package me.mrletsplay.mdblog.rss;

import java.io.ByteArrayOutputStream;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.TransformerFactoryConfigurationError;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.w3c.dom.Document;

import me.mrletsplay.simplehttpserver.http.response.HttpResponse;
import me.mrletsplay.simplehttpserver.http.util.MimeType;

public class RSSResponse implements HttpResponse {

	private RSSFeed feed;

	public RSSResponse(RSSFeed feed) {
		this.feed = feed;
	}

	@Override
	public byte[] getContent() {
		try {
			Document doc = feed.toXML();
			Transformer transform = TransformerFactory.newInstance().newTransformer();
			transform.setOutputProperty(OutputKeys.INDENT, "yes");
			transform.setOutputProperty(OutputKeys.METHOD, "xml");
			transform.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
			transform.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "4");
			ByteArrayOutputStream bOut = new ByteArrayOutputStream();
			transform.transform(new DOMSource(doc), new StreamResult(bOut));
			return bOut.toByteArray();
		} catch (TransformerException | TransformerFactoryConfigurationError | ParserConfigurationException e) {
			throw new RuntimeException(e);
		}
	}

	@Override
	public MimeType getContentType() {
		return MimeType.of("application/rss+xml");
	}

}
