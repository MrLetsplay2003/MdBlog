package me.mrletsplay.mdblog.rss;

public record FeedConfig(String title, String description, String link) {

	public static FeedConfig load(String configString) {
		String title = "Untitled Blog";
		String description = "No description";
		String link = "http://localhost";

		for(String line : configString.split("\n")) {
			if(line.isBlank()) continue;
			String[] spl = line.split(":", 2);
			if(spl.length != 2) {
				System.err.println("Invalid config line: " + line);
				continue;
			}

			String key = spl[0].toLowerCase().trim();
			String value = spl[1].trim();

			switch(key) {
				case "title" -> title = value;
				case "description" -> description = value;
				case "link" -> link = value;
			}
		}

		return new FeedConfig(title, description, link);
	}

}
