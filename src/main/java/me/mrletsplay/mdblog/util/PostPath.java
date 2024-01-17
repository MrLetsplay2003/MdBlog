package me.mrletsplay.mdblog.util;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;

public record PostPath(String[] segments) {

	public PostPath {
		if(segments.length == 0) throw new IllegalArgumentException("Number of segments must be greater than 0");
	}

	public PostPath getParent() {
		if(segments.length == 1) return null;
		return new PostPath(Arrays.copyOfRange(segments, 0, segments.length - 1));
	}

	public PostPath concat(PostPath other) {
		String[] newPath = Arrays.copyOf(segments, segments.length + other.length());
		System.arraycopy(other.segments, 0, newPath, segments.length, other.length());
		return new PostPath(newPath);
	}

	public boolean startsWith(PostPath other) {
		if(other.segments.length > segments.length) return false;
		for(int i = 0; i < other.segments.length; i++) {
			if(!segments[i].equals(other.segments[i])) return false;
		}
		return true;
	}

	public PostPath subPath(int fromIndex) throws IllegalArgumentException {
		if(fromIndex < 0 || fromIndex >= segments.length) throw new  IllegalArgumentException("fromIndex must be less than path length");
		return new PostPath(Arrays.copyOfRange(segments, fromIndex, segments.length));
	}

	public PostPath subPath(int fromIndex, int toIndex) throws IllegalArgumentException {
		if(fromIndex < 0 || fromIndex >= segments.length) throw new  IllegalArgumentException("fromIndex must be less than path length");
		if(toIndex <= fromIndex || toIndex >= segments.length) throw new  IllegalArgumentException("fromIndex must be less than toIndex and path length");
		return new PostPath(Arrays.copyOfRange(segments, fromIndex, toIndex));
	}

	public String getName() {
		return segments[segments.length - 1];
	}

	public int length() {
		return segments.length;
	}

	public Path toNioPath() {
		return Paths.get(segments[0], Arrays.copyOfRange(segments, 1, segments.length));
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + Arrays.hashCode(segments);
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		PostPath other = (PostPath) obj;
		return Arrays.equals(segments, other.segments);
	}

	@Override
	public String toString() {
		return String.join("/", segments);
	}

	public static PostPath parse(String path) {
		if(path == null || path.isEmpty()) throw new IllegalArgumentException("Path must not be null or empty");
		return new PostPath(path.split("/"));
	}

	public static PostPath of(Path path) throws IllegalArgumentException {
		if(path.getNameCount() == 0) throw new IllegalArgumentException("Path must not be a root path");
		String[] names = new String[path.getNameCount()];
		for(int i = 0; i < path.getNameCount(); i++) {
			names[i] = path.getName(i).toString();
		}
		return new PostPath(names);
	}

	public static PostPath of(Path path, String name) {
		if(path == null) return new PostPath(new String[] {name});
		String[] names = new String[path.getNameCount() + 1];
		for(int i = 0; i < path.getNameCount(); i++) {
			names[i] = path.getName(i).toString();
		}
		names[names.length - 1] = name;
		return new PostPath(names);
	}

}
