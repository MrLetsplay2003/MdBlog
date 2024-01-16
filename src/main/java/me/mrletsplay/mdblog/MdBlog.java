package me.mrletsplay.mdblog;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import me.mrletsplay.mdblog.blog.Post;
import me.mrletsplay.simplehttpserver.dom.html.HtmlDocument;
import me.mrletsplay.simplehttpserver.dom.html.HtmlElement;
import me.mrletsplay.simplehttpserver.http.HttpRequestMethod;
import me.mrletsplay.simplehttpserver.http.HttpStatusCodes;
import me.mrletsplay.simplehttpserver.http.document.FileDocument;
import me.mrletsplay.simplehttpserver.http.request.HttpRequestContext;
import me.mrletsplay.simplehttpserver.http.response.TextResponse;
import me.mrletsplay.simplehttpserver.http.server.HttpServer;

public class MdBlog {

	private static final Path
		FILES_PATH = Path.of("files"),
		POSTS_PATH = FILES_PATH.resolve("posts");

	private static HttpServer server;
	private static WatchService watchService;
	private static List<WatchKey> watchedDirectories;
	private static Map<String, Post> posts;

	public static void main(String[] args) throws IOException {
		server = new HttpServer(HttpServer.newConfigurationBuilder()
			.hostBindAll()
			.port(3706)
			.create());

		server.getDocumentProvider().registerPattern(HttpRequestMethod.GET, "/posts", () -> {
			createPostsIndex(POSTS_PATH);
		});

		server.getDocumentProvider().registerPattern(HttpRequestMethod.GET, "/posts/{path...}", () -> {
			HttpRequestContext ctx = HttpRequestContext.getCurrentContext();
			String path = ctx.getPathParameters().get("path");
			Post post = posts.get(path);
			if(post != null) {
				post.getContent().createContent();
				return;
			}

			Path resolved = POSTS_PATH.resolve(path).normalize();
			if(!resolved.startsWith(POSTS_PATH)) {
				server.getDocumentProvider().getNotFoundDocument().createContent();
				return;
			}

			if(!Files.isRegularFile(resolved)) {
				if(!Files.isDirectory(resolved)) {
					server.getDocumentProvider().getNotFoundDocument().createContent();
					return;
				}

				createPostsIndex(resolved);

				return;
			}

			try {
				new FileDocument(resolved).createContent();
			} catch (IOException e) {
				e.printStackTrace();
			}
		});

		extractAndRegister("style/base.css");
		extractAndRegister("style/index.css");
		extractAndRegister("style/post.css");

		server.start();

		Files.createDirectories(FILES_PATH);
		Files.createDirectories(POSTS_PATH);

		watchedDirectories = new ArrayList<>();
		watchService = POSTS_PATH.getFileSystem().newWatchService();
		posts = new HashMap<>();
		updateBlogs();
		watchFolders();

		while(true) {
			try {
				WatchKey key = watchService.take();
				key.pollEvents();
				updateBlogs();
				watchFolders();
				if(!key.reset()) {
					key.cancel();
					watchedDirectories.remove(key);
					continue;
				}
			} catch (InterruptedException e) {
				break;
			}
		}
	}

	private static void createPostsIndex(Path directory) {
		try {
			// Generate posts index
			List<Path> inDir = Files.list(directory)
				.filter(p -> Files.isDirectory(p) || posts.values().stream().anyMatch(post -> post.getFilePath().equals(p)))
				.collect(Collectors.toList());
			Collections.sort(inDir);

			HtmlDocument index = new HtmlDocument();
			index.setTitle("Index of " + directory.getFileName());
			index.addStyleSheet("/style/index.css");

			HtmlElement ul = new HtmlElement("ul");
			for(Path p : inDir) {
				HtmlElement li = new HtmlElement("li");
				HtmlElement a = new HtmlElement("a");
				String relPath = directory.relativize(p).toString();
				if(Files.isRegularFile(p)) relPath = relPath.substring(0, relPath.length() - Post.FILE_EXTENSION.length());
				// TODO: only works with trailing /
				a.setAttribute("href", relPath);
				a.setText(relPath);
				li.appendChild(a);
				ul.appendChild(li);
			}

			index.getBodyNode().appendChild(ul);

			index.createContent();
		}catch(IOException e) {
			e.printStackTrace();
			HttpRequestContext ctx = HttpRequestContext.getCurrentContext();
			ctx.respond(HttpStatusCodes.INTERNAL_SERVER_ERROR_500, new TextResponse("Failed to create index"));
		}
	}

	private static void extractAndRegister(String path) throws IOException {
		Path filePath = FILES_PATH.resolve(path);
		if(!Files.exists(filePath)) {
			Files.createDirectories(filePath.getParent());
			Files.write(filePath, MdBlog.class.getResourceAsStream("/" + path).readAllBytes());
		}

		server.getDocumentProvider().register(HttpRequestMethod.GET, "/" + path, new FileDocument(filePath));
	}

	private static void updateBlogs() throws IOException {
		Iterator<Post> it = posts.values().iterator();
		while(it.hasNext()) {
			Post p = it.next();
			if(!p.update()) it.remove();
		}

		Files.walk(POSTS_PATH)
			.filter(Files::isRegularFile)
			.filter(f -> f.getFileName().toString().endsWith(Post.FILE_EXTENSION))
			.filter(f -> posts.values().stream().noneMatch(p -> p.getFilePath().equals(f)))
			.forEach(f -> {
				try {
					String path = POSTS_PATH.relativize(f).toString();
					path = path.substring(0, path.length() - Post.FILE_EXTENSION.length());
					posts.put(path, new Post(f));
				} catch (IOException e) {}
			});
	}

	private static void watchFolders() throws IOException {
		Files.walk(POSTS_PATH)
			.filter(Files::isDirectory)
			.filter(d -> watchedDirectories.stream().noneMatch(w -> w.watchable().equals(d)))
			.forEach(d -> {
				try {
					System.out.println("Watching " + d);
					watchedDirectories.add(d.register(watchService, StandardWatchEventKinds.ENTRY_CREATE, StandardWatchEventKinds.ENTRY_DELETE, StandardWatchEventKinds.ENTRY_MODIFY, StandardWatchEventKinds.OVERFLOW));
				} catch (IOException e) {}
			});
	}

}
