package me.mrletsplay.mdblog.rss;

import java.time.Instant;

public record RSSItem(Instant date, String title, String author, String link, String description) {

}
