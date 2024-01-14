package me.mrletsplay.mdblog.blog;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import me.mrletsplay.mdblog.markdown.MdParser;
import me.mrletsplay.mdblog.markdown.MdRenderer;
import me.mrletsplay.mrcore.misc.ByteUtils;
import me.mrletsplay.simplehttpserver.dom.html.HtmlDocument;

public class Post {

	private static final MessageDigest MD_5;
	private static final MdRenderer RENDERER = new MdRenderer();

	static {
		try {
			MD_5 = MessageDigest.getInstance("MD5");
		} catch (NoSuchAlgorithmException e) {
			throw new RuntimeException(e);
		}
	}

	private Path filePath;
	private String checksum;
	private PostMetadata metadata;
	private HtmlDocument content;

	public Post(Path filePath) throws IOException {
		this.filePath = filePath;
		this.checksum = checksum(filePath);
		load();
	}

	public Path getFilePath() {
		return filePath;
	}

	public PostMetadata getMetadata() {
		return metadata;
	}

	public HtmlDocument getContent() {
		return content;
	}

	private void load() throws IOException {
		String postData = Files.readString(filePath);
		String[] spl = postData.split("\n---\n", 2);
		if(spl.length != 2) throw new IOException("Invalid post file");

		this.metadata = PostMetadata.load(spl[0]);

		HtmlDocument document = new HtmlDocument();
		document.getBodyNode().appendChild(RENDERER.render(MdParser.parse(spl[1])));
		document.setTitle(metadata.title());
		document.setDescription(metadata.author());
		document.addStyleSheet("/style/base.css");
		document.addStyleSheet("/style/post.css");
		this.content = document;
	}

	public boolean update() {
		if(!Files.exists(filePath)) return false;

		try {
			String newChecksum = checksum(filePath);
			if(checksum.equals(newChecksum)) return true;
			this.checksum = newChecksum;
			load();
			return true;
		} catch (IOException e) {
			return false;
		}
	}

	private static String checksum(Path filePath) throws IOException {
		return ByteUtils.bytesToHex(MD_5.digest(Files.readAllBytes(filePath)));
	}

}
