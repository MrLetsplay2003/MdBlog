package me.mrletsplay.mdblog;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import me.mrletsplay.mdblog.blog.Post;
import me.mrletsplay.mdblog.blog.PostMetadata;
import me.mrletsplay.mdblog.markdown.MdParser;
import me.mrletsplay.mdblog.markdown.MdRenderer;
import me.mrletsplay.mdblog.template.Template;
import me.mrletsplay.mdblog.template.Templates;
import me.mrletsplay.mdblog.util.PostPath;
import me.mrletsplay.mdblog.util.TimeFormatter;
import me.mrletsplay.mrcore.http.HttpUtils;
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
	private static Map<PostPath, Templates> indexTemplates;
	private static Map<PostPath, FileDocument> globalResources;

	private static Templates defaultTemplates;

	public static void main(String[] args) throws IOException {
		server = new HttpServer(HttpServer.newConfigurationBuilder()
			.hostBindAll()
			.port(3706)
			.create());

		server.getDocumentProvider().register(HttpRequestMethod.GET, "/", () -> createPostsIndex(PostPath.root()));
		server.getDocumentProvider().registerPattern(HttpRequestMethod.GET, "/{path...}", () -> handleRequest(HttpRequestContext.getCurrentContext()));

		defaultTemplates = new Templates(null);
		for(Template template : Template.values()) {
			defaultTemplates.put(template, Files.readString(extract("template/" + template.getName() + ".md")));
		}

		server.start();

		Files.createDirectories(FILES_PATH);
		Files.createDirectories(POSTS_PATH);

		watchedDirectories = new ArrayList<>();
		watchService = POSTS_PATH.getFileSystem().newWatchService();

		posts = new HashMap<>();
		indexTemplates = new HashMap<>();
		globalResources = new HashMap<>();

		extractAndRegister("style/base.css");
		extractAndRegister("style/index.css");
		extractAndRegister("style/post.css");

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
		String tag = HttpRequestContext.getCurrentContext().getRequestedPath().getQuery().getFirst("tag");

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

		String blogName = path.getName();

		Templates templates = indexTemplates.getOrDefault(path, defaultTemplates);

		HtmlDocument index = new HtmlDocument();
		index.setTitle("Index of " + blogName);
		index.setDescription("A blog hosted using MdBlog");
		index.addStyleSheet("_/style/base.css");
		index.addStyleSheet("_/style/index.css");

		String indexMd = templates.render(Template.INDEX,
			"name", blogName,
			"sub_blogs", directories.stream()
				.sorted(Comparator.comparing(p -> p.getName()))
				.map(p -> {
					HtmlElement name = new HtmlElement("a");
					name.setAttribute("href", p.toString());
					name.setText(p.getName());
					return templates.render(Template.INDEX_SUB_BLOG,
						"name", name.toString());
				})
				.collect(Collectors.joining("\n\n")),
			"posts", postsInDir.stream()
				.sorted(Comparator.<PostPath, Instant>comparing(p -> posts.get(path.concat(p)).getMetadata().date()).reversed())
				.map(p -> {
					Post post = posts.get(path.concat(p));
					PostMetadata meta = post.getMetadata();
					if(tag != null && !meta.tags().contains(tag)) return null;

					HtmlElement title = new HtmlElement("a");
					title.setAttribute("href", p.toString());
					title.setText(meta.title());

					return templates.render(Template.INDEX_POST,
						"title", title.toString(),
						"author", meta.author(),
						"date", TimeFormatter.toDateOnly(meta.date()),
						"date_time", TimeFormatter.toDateAndTime(meta.date()),
						"date_relative", TimeFormatter.toRelativeTime(meta.date()),
						"description", meta.description(),
						"tags", meta.tags().stream()
							.map(t -> {
								HtmlElement link = new HtmlElement("a");
								link.setText(t);
								link.setAttribute("href", "?tag=" + HttpUtils.urlEncode(t));
								return link.toString();
							})
							.collect(Collectors.joining(", ")));
				})
				.filter(Objects::nonNull)
				.collect(Collectors.joining("\n\n")));

		index.getBodyNode().appendChild(new MdRenderer().render(MdParser.parse(indexMd)));
		index.createContent();
	}

	private static void handleRequest(HttpRequestContext ctx) {
		String rawPath = ctx.getPathParameters().get("path");
		PostPath path = PostPath.parse(rawPath);
		int index = 0;
		while(index < path.length() - 1 && !path.getSegments()[index].equals("_")) index++;
		if(index < path.length() - 1) {
			PostPath resourcePath = path.subPath(index + 1);
			FileDocument resource = globalResources.get(resourcePath);
			if(resource != null) {
				resource.createContent();
			}else {
				server.getDocumentProvider().getNotFoundDocument().createContent();
			}
			return;
		}

		Post post = posts.get(path);

		if(post != null) {
			if(rawPath.endsWith("/")) {
				ctx.redirect("../" + path.subPath(path.length() - 1));
			}

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
	}

	private static Path extract(String path) throws IOException {
		Path filePath = FILES_PATH.resolve(path);
		if(!Files.exists(filePath)) {
			System.out.println("Extracting " + path);
			Files.createDirectories(filePath.getParent());
			Files.write(filePath, MdBlog.class.getResourceAsStream("/" + path).readAllBytes());
		}
		return filePath;
	}

	private static void extractAndRegister(String path) throws IOException {
		globalResources.put(PostPath.parse(path), new FileDocument(extract(path)));
	}

	private static void updateBlogs() throws IOException {
		System.out.println("Update");
		indexTemplates.clear();

		Files.walk(POSTS_PATH)
			.filter(Files::isRegularFile)
			.filter(f -> f.getFileName().toString().endsWith(Post.FILE_EXTENSION))
			.filter(f -> posts.values().stream().noneMatch(p -> p.getFilePath().equals(f)))
			.forEach(f -> {
				try {
					String fullPostName = f.getFileName().toString();
					String postName = fullPostName.substring(0, fullPostName.length() - Post.FILE_EXTENSION.length());
					PostPath path = PostPath.of(POSTS_PATH.relativize(f).getParent(), postName);

					Template template = Arrays.stream(Template.values())
						.filter(t -> t.getName().equals(postName))
						.findFirst().orElse(null);
					if(template != null) {
						// File is an index template, don't parse it as a post
						indexTemplates.computeIfAbsent(path.getParent(), p -> new Templates(defaultTemplates)).put(template, Files.readString(f, StandardCharsets.UTF_8));
						return;
					}

					posts.put(path, new Post(f));
				} catch (IOException e) {}
			});

		Iterator<Map.Entry<PostPath, Post>> it = posts.entrySet().iterator();
		while(it.hasNext()) {
			Map.Entry<PostPath, Post> en = it.next();
			PostPath path = en.getKey();
			Post p = en.getValue();

			Templates templates = indexTemplates.getOrDefault(path.getParent(), defaultTemplates);
			if(!p.update(templates)) it.remove();
		}
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
