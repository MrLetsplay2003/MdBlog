package me.mrletsplay.mdblog;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
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
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import me.mrletsplay.mdblog.blog.Post;
import me.mrletsplay.mdblog.blog.PostMetadata;
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

	private static final String
		INDEX_NAME = "index",
		INDEX_POST_NAME = "index-post",
		INDEX_SUB_BLOG_NAME = "index-sub-blog";

	private static HttpServer server;
	private static WatchService watchService;
	private static List<WatchKey> watchedDirectories;
	private static Map<PostPath, Post> posts;
	private static Map<PostPath, String> indexTemplates;

	private static String
		defaultIndexTemplate,
		defaultIndexSubBlogTemplate,
		defaultIndexPostTemplate;

	public static void main(String[] args) throws IOException {
		server = new HttpServer(HttpServer.newConfigurationBuilder()
			.hostBindAll()
			.port(3706)
			.create());

		server.getDocumentProvider().register(HttpRequestMethod.GET, "/", () -> {
			createPostsIndex(null);
		});

		server.getDocumentProvider().registerPattern(HttpRequestMethod.GET, "/{path...}", () -> {
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
					ctx.redirect(path.subPath(path.length() - 1).toString() + "/");
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

		defaultIndexTemplate = Files.readString(extract("template/index.md"));
		defaultIndexPostTemplate = Files.readString(extract("template/index-post.md"));
		defaultIndexSubBlogTemplate = Files.readString(extract("template/index-sub-blog.md"));

		server.start();

		Files.createDirectories(FILES_PATH);
		Files.createDirectories(POSTS_PATH);

		watchedDirectories = new ArrayList<>();
		watchService = POSTS_PATH.getFileSystem().newWatchService();

		posts = new HashMap<>();
		indexTemplates = new HashMap<>();
		updateBlogs();
		watchFolders();

		while(true) {
			try {
				WatchKey key = watchService.poll(5, TimeUnit.MINUTES);
				if(key != null) key.pollEvents();

				updateBlogs();
				watchFolders();

				if(key != null && !key.reset()) {
					key.cancel();
					watchedDirectories.remove(key);
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

		String blogName = path == null ? "/" : path.getName();

		String indexTemplate;
		String indexSubBlogTemplate;
		String indexPostTemplate;

		if(path != null) {
			indexTemplate = indexTemplates.getOrDefault(path.concat(PostPath.parse(INDEX_NAME)), defaultIndexTemplate);
			indexSubBlogTemplate = indexTemplates.getOrDefault(path.concat(PostPath.parse(INDEX_SUB_BLOG_NAME)), defaultIndexSubBlogTemplate);
			indexPostTemplate = indexTemplates.getOrDefault(path.concat(PostPath.parse(INDEX_POST_NAME)), defaultIndexPostTemplate);
		}else {
			indexTemplate = defaultIndexTemplate;
			indexSubBlogTemplate = defaultIndexSubBlogTemplate;
			indexPostTemplate = defaultIndexPostTemplate;
		}

		HtmlDocument index = new HtmlDocument();
		index.setTitle("Index of " + blogName);
		index.addStyleSheet("/_/style/base.css");
		index.addStyleSheet("/_/style/index.css");

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
				Post post = posts.get(path == null ? p : path.concat(p));
				PostMetadata meta = post.getMetadata();

				HtmlElement title = new HtmlElement("a");
				title.setAttribute("href", p.toString());
				title.setText(meta.title());
				postMd = postMd.replace("{title}", title.toString());

				postMd = postMd.replace("{author}", meta.author());
				postMd = postMd.replace("{date}", meta.date().toString());
				postMd = postMd.replace("{tags}", meta.tags().stream().collect(Collectors.joining(", ")));
				postMd = postMd.replace("{description}", meta.description());
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
		server.getDocumentProvider().register(HttpRequestMethod.GET, "/_/" + path, new FileDocument(extract(path)));
	}

	private static void updateBlogs() throws IOException {
		Iterator<Post> it = posts.values().iterator();
		while(it.hasNext()) {
			Post p = it.next();
			if(!p.update()) it.remove();
		}

		indexTemplates.clear();

		Files.walk(POSTS_PATH)
			.filter(Files::isRegularFile)
			.filter(f -> f.getFileName().toString().endsWith(Post.FILE_EXTENSION))
			.filter(f -> posts.values().stream().noneMatch(p -> p.getFilePath().equals(f)))
			.forEach(f -> {
				try {
					String postName = f.getFileName().toString();
					postName = postName.substring(0, postName.length() - Post.FILE_EXTENSION.length());
					PostPath path = PostPath.of(POSTS_PATH.relativize(f).getParent(), postName);

					if(INDEX_NAME.equals(postName) || INDEX_SUB_BLOG_NAME.equals(postName) || INDEX_POST_NAME.equals(postName)) {
						// File is an index template, don't parse it as a post
						indexTemplates.put(path, Files.readString(f, StandardCharsets.UTF_8));
						return;
					}

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
