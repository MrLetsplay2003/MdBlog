package me.mrletsplay.mdblog.util;

import java.time.Instant;
import java.time.LocalDate;
import java.time.Period;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;

public class TimeFormatter {

	private static final DateTimeFormatter
		DATE_ONLY = DateTimeFormatter.ISO_LOCAL_DATE,
		DATE_AND_TIME = new DateTimeFormatterBuilder()
			.append(DateTimeFormatter.ISO_LOCAL_DATE)
			.appendLiteral(" ")
			.append(DateTimeFormatter.ISO_LOCAL_TIME)
			.toFormatter();

	public static String toDateOnly(Instant instant) {
		return DATE_ONLY.format(instant.atZone(ZoneId.systemDefault()));
	}

	public static String toDateAndTime(Instant instant) {
		return DATE_AND_TIME.format(instant.atZone(ZoneId.systemDefault()));
	}

	public static String toRelativeTime(Instant instant) {
		// TODO: potentially use ChronoUnit#between instead to support more units
		Period p = Period.between(LocalDate.now(), instant.atZone(ZoneId.systemDefault()).toLocalDate());

		if(p.isZero()) return "today";
		boolean negative = p.isNegative();
		if(negative) p = p.negated();

		StringBuilder b = new StringBuilder();

		if(!negative) b.append("in ");

		if(p.getYears() > 0) {
			appendTime(b, p.getYears(), "year");
		}else if(p.getMonths() > 0) {
			appendTime(b, p.getMonths(), "month");
		}else if(p.getDays() > 0) {
			appendTime(b, p.getDays(), "day");
		}

		if(negative) b.append(" ago");

		return b.toString();
	}

	private static void appendTime(StringBuilder builder, int x, String unit) {
		builder.append(x).append(" ").append(unit);
		if(x > 1) builder.append("s");
	}

}
