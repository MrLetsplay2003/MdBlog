package me.mrletsplay.mdblog;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import me.mrletsplay.mdblog.blog.Post;
import me.mrletsplay.mdblog.markdown.MdParser;
import me.mrletsplay.mdblog.markdown.MdRenderer;
import me.mrletsplay.mdblog.util.PostPath;
import me.mrletsplay.simplehttpserver.dom.html.HtmlDocument;
import me.mrletsplay.simplehttpserver.dom.html.HtmlElement;
import me.mrletsplay.simplehttpserver.http.HttpRequestMethod;
import me.mrletsplay.simplehttpserver.http.document.FileDocument;
import me.mrletsplay.simplehttpserver.http.request.HttpRequestContext;
import me.mrletsplay.simplehttpserver.http.server.HttpServer;

public class MdBlog {

	private static final Path
		FILES_PATH = Path.of("files"),
		POSTS_PATH = FILES_PATH.resolve("posts");

	private static HttpServer server;
	private static WatchService watchService;
	private static List<WatchKey> watchedDirectories;
	private static Map<PostPath, Post> posts;

	private static String
		indexTemplate,
		indexSubBlogTemplate,
		indexPostTemplate;

	public static void main(String[] args) throws IOException {
		server = new HttpServer(HttpServer.newConfigurationBuilder()
			.hostBindAll()
			.port(3706)
			.create());

		server.getDocumentProvider().register(HttpRequestMethod.GET, "/posts", () -> {
			HttpRequestContext ctx = HttpRequestContext.getCurrentContext();
			ctx.redirect("/posts/");
		});

		server.getDocumentProvider().register(HttpRequestMethod.GET, "/posts/", () -> {
			createPostsIndex(null);
		});

		server.getDocumentProvider().registerPattern(HttpRequestMethod.GET, "/posts/{path...}", () -> {
			HttpRequestContext ctx = HttpRequestContext.getCurrentContext();
			String rawPath = ctx.getPathParameters().get("path");
			PostPath path = PostPath.parse(rawPath);
			Post post = posts.get(path);
			if(post != null) {
				post.getContent().createContent();
				return;
			}

			if(posts.keySet().stream().anyMatch(p -> p.startsWith(path))) {
				if(!rawPath.endsWith("/")) {
					ctx.redirect("/posts/" + rawPath + "/");
					return;
				}

				createPostsIndex(path);
				return;
			}

			Path resolved = POSTS_PATH.resolve(path.toNioPath()).normalize();
			if(!resolved.startsWith(POSTS_PATH)) {
				server.getDocumentProvider().getNotFoundDocument().createContent();
				return;
			}

			if(!Files.isRegularFile(resolved) || !Files.isReadable(resolved)) {
				server.getDocumentProvider().getNotFoundDocument().createContent();
				return;
			}

			try {
				new FileDocument(resolved).createContent();
			} catch (IOException e) {
				e.printStackTrace();
				ctx.setException(e);
				server.getDocumentProvider().getErrorDocument().createContent();
			}
		});

		extractAndRegister("style/base.css");
		extractAndRegister("style/index.css");
		extractAndRegister("style/post.css");

		indexTemplate = Files.readString(extract("template/index.md"));
		indexPostTemplate = Files.readString(extract("template/index-post.md"));
		indexSubBlogTemplate = Files.readString(extract("template/index-sub-blog.md"));

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

	private static void createPostsIndex(PostPath path) {
		// Generate posts index
		List<PostPath> allPaths = posts.keySet().stream()
			.filter(p -> path == null || (p.startsWith(path) && !p.equals(path)))
			.map(p -> path == null ? p : p.subPath(path.length()))
			.toList();

		List<PostPath> directories = allPaths.stream()
			.filter(p -> p.length() > 1)
			.map(p -> p.subPath(0, 1))
			.distinct()
			.toList();

		List<PostPath> postsInDir = allPaths.stream()
			.filter(p -> p.length() == 1)
			.toList();

//			List<Path> inDir = Files.list(directory)
//				.filter(p -> Files.isDirectory(p) || posts.values().stream().anyMatch(post -> post.getFilePath().equals(p)))
//				.collect(Collectors.toList());
//			Collections.sort(inDir);

		String blogName = path == null ? "/" : path.getName();

		HtmlDocument index = new HtmlDocument();
		index.setTitle("Index of " + blogName);
		index.addStyleSheet("/style/base.css");
		index.addStyleSheet("/style/index.css");

		String indexMd = indexTemplate;
		indexMd = indexMd.replace("{name}", blogName);
		indexMd = indexMd.replace("{sub_blogs}", directories.stream()
			.map(p -> {
				String subBlogMd = indexSubBlogTemplate;

				HtmlElement name = new HtmlElement("a");
				name.setAttribute("href", p.toString());
				name.setText(p.getName());
				subBlogMd = subBlogMd.replace("{name}", name.toString());
				return subBlogMd;
			})
			.collect(Collectors.joining("\n\n")));
		indexMd = indexMd.replace("{posts}", postsInDir.stream()
			.map(p -> {
				String postMd = indexPostTemplate;
				Post post = posts.get(path.concat(p));

				HtmlElement title = new HtmlElement("a");
				title.setAttribute("href", p.toString());
				title.setText(post.getName());
				postMd = postMd.replace("{title}", title.toString());

				postMd = postMd.replace("{author}", post.getMetadata().author());
				postMd = postMd.replace("{date}", post.getMetadata().date().toString());
				postMd = postMd.replace("{tags}", post.getMetadata().tags().stream().collect(Collectors.joining(", ")));
				postMd = postMd.replace("{description}", post.getMetadata().description());
				return postMd;
			})
			.collect(Collectors.joining("\n\n")));

		index.getBodyNode().appendChild(new MdRenderer().render(MdParser.parse(indexMd)));
		index.createContent();
	}

	private static Path extract(String path) throws IOException {
		Path filePath = FILES_PATH.resolve(path);
		if(!Files.exists(filePath)) {
			Files.createDirectories(filePath.getParent());
			Files.write(filePath, MdBlog.class.getResourceAsStream("/" + path).readAllBytes());
		}
		return filePath;
	}

	private static void extractAndRegister(String path) throws IOException {
		server.getDocumentProvider().register(HttpRequestMethod.GET, "/" + path, new FileDocument(extract(path)));
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
					String postName = f.getFileName().toString();
					postName = postName.substring(0, postName.length() - Post.FILE_EXTENSION.length());
					posts.put(PostPath.of(POSTS_PATH.relativize(f).getParent(), postName), new Post(f));
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
