package me.mrletsplay.mdblog.blog;

import java.time.DateTimeException;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Collectors;

public record PostMetadata(Instant date, String title, String author, Set<String> tags, String description) {

	public static PostMetadata load(String metadataString) {
		Instant date = Instant.EPOCH;
		String title = "Untitled Post";
		String author = "Unknown Author";
		String description = "No description";
		Set<String> tags = Collections.singleton("untagged");
		for(String line : metadataString.split("\n")) {
			if(line.isBlank()) continue;
			String[] spl = line.split(":", 2);
			if(spl.length != 2) {
				System.err.println("Invalid metadata line: " + line);
				continue;
			}

			String key = spl[0].toLowerCase().trim();
			String value = spl[1].trim();

			switch(key) {
				case "date" -> {
					try {
						date = Instant.parse(value);
					}catch(DateTimeException e) {}
				}
				case "title" -> title = value;
				case "author" -> author = value;
				case "tags" -> {
					Set<String> t = Arrays.stream(value.split(","))
						.map(String::trim)
						.collect(Collectors.toCollection(LinkedHashSet::new));

					tags = Collections.unmodifiableSet(t);
				}
			}
		}

		return new PostMetadata(date, title, author, tags, description);
	}

}
