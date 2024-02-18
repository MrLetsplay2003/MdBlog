package me.mrletsplay.mdblog.rss;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.w3c.dom.Document;
import org.w3c.dom.Element;

public class RSSFeed {

	private String title;
	private String link;
	private String description;
	private List<RSSItem> items;

	public RSSFeed(String title, String link, String description) {
		this.title = title;
		this.link = link;
		this.description = description;
		this.items = new ArrayList<>();
	}

	public void addItem(RSSItem item) {
		items.add(item);
	}

	public Document toXML() throws ParserConfigurationException {
		DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
		DocumentBuilder builder = dbf.newDocumentBuilder();
		Document doc = builder.newDocument();

		Element rss = doc.createElement("rss");
		doc.appendChild(rss);

		Element channel = doc.createElement("channel");
		rss.appendChild(channel);

		Element title = doc.createElement("title");
		title.setTextContent(this.title);
		channel.appendChild(title);

		Element link = doc.createElement("link");
		link.setTextContent(this.link);
		channel.appendChild(link);

		Element description = doc.createElement("description");
		description.setTextContent(this.description);
		channel.appendChild(description);

		for(RSSItem item : items) {
			Element itemEl = doc.createElement("item");
			channel.appendChild(itemEl);

			Element itDate = doc.createElement("pubDate");
			itDate.setTextContent(DateTimeFormatter.RFC_1123_DATE_TIME.format(item.date().atZone(ZoneId.systemDefault())));
			itemEl.appendChild(itDate);

			Element itTitle = doc.createElement("title");
			itTitle.setTextContent(item.title());
			itemEl.appendChild(itTitle);

			Element itAuthor = doc.createElement("author");
			itAuthor.setTextContent(item.author());
			itemEl.appendChild(itAuthor);

			Element itLink = doc.createElement("link");
			itLink.setTextContent(item.link());
			itemEl.appendChild(itLink);

			Element itDescription = doc.createElement("description");
			itDescription.setTextContent(item.description());
			itemEl.appendChild(itDescription);
		}

		return doc;
	}

}
