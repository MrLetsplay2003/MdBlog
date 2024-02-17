package me.mrletsplay.mdblog.blog;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Collectors;

import me.mrletsplay.mdblog.markdown.MdParser;
import me.mrletsplay.mdblog.markdown.MdRenderer;
import me.mrletsplay.mdblog.template.Template;
import me.mrletsplay.mdblog.template.Templates;
import me.mrletsplay.mdblog.util.TimeFormatter;
import me.mrletsplay.mrcore.http.HttpUtils;
import me.mrletsplay.simplehttpserver.dom.html.HtmlDocument;
import me.mrletsplay.simplehttpserver.dom.html.HtmlElement;

public class Post {

	public static final String FILE_EXTENSION = ".md";

	//private static final MessageDigest MD_5;
	private static final MdRenderer RENDERER = new MdRenderer();

//	static {
//		try {
//			MD_5 = MessageDigest.getInstance("MD5");
//		} catch (NoSuchAlgorithmException e) {
//			throw new RuntimeException(e);
//		}
//	}

	private Path filePath;
//	private String checksum; TODO currently unused, post also needs to update when templates change
	private PostMetadata metadata;
	private HtmlDocument content;

	public Post(Path filePath) throws IOException {
		this.filePath = filePath;
	}

	public Path getFilePath() {
		return filePath;
	}

	public String getName() {
		String fileName = filePath.getFileName().toString();
		return fileName.substring(0, fileName.length() - FILE_EXTENSION.length());
	}

	public PostMetadata getMetadata() {
		return metadata;
	}

	public HtmlDocument getContent() {
		return content;
	}

	private void load(Templates templates) throws IOException {
		String postData = Files.readString(filePath);
		String[] spl = postData.split("\n---\n", 2);
		if(spl.length != 2) throw new IOException("Invalid post file");

		this.metadata = PostMetadata.load(spl[0]);

		String postMd = templates.render(Template.POST,
			"content", spl[1],
			"title", metadata.title(),
			"author", metadata.author(),
			"description", metadata.description(),
			"date", TimeFormatter.toDateOnly(metadata.date()),
			"date_time", TimeFormatter.toDateAndTime(metadata.date()),
			"date_relative", TimeFormatter.toRelativeTime(metadata.date()),
			"tags", metadata.tags().stream()
			.map(t -> {
				HtmlElement link = new HtmlElement("a");
				link.setText(t);
				link.setAttribute("href", "./?tag=" + HttpUtils.urlEncode(t));
				return link.toString();
			})
			.collect(Collectors.joining(", ")));

		HtmlDocument document = new HtmlDocument();
		document.getBodyNode().appendChild(RENDERER.render(MdParser.parse(postMd)));
		document.setTitle(metadata.title());
		document.setDescription(metadata.description());
		document.addStyleSheet("_/style/base.css");
		document.addStyleSheet("_/style/post.css");
		this.content = document;
	}

	public boolean update(Templates templates) {
		if(!Files.exists(filePath)) return false;

		try {
			//String newChecksum = checksum(filePath);
			//if(checksum != null && checksum.equals(newChecksum)) return true;
			//this.checksum = newChecksum;
			load(templates);
			return true;
		} catch (IOException e) {
			return false;
		}
	}

//	private static String checksum(Path filePath) throws IOException {
//		return ByteUtils.bytesToHex(MD_5.digest(Files.readAllBytes(filePath)));
//	}

}
