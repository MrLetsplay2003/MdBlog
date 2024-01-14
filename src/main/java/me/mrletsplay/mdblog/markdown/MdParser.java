package me.mrletsplay.mdblog.markdown;
import java.util.Arrays;

import org.commonmark.ext.gfm.strikethrough.StrikethroughExtension;
import org.commonmark.ext.gfm.tables.TablesExtension;
import org.commonmark.ext.ins.InsExtension;
import org.commonmark.ext.task.list.items.TaskListItemsExtension;
import org.commonmark.node.Node;
import org.commonmark.parser.Parser;

public class MdParser {

	private static Parser parser = new Parser.Builder()
		.extensions(Arrays.asList(TablesExtension.create(), StrikethroughExtension.create(), InsExtension.create(), TaskListItemsExtension.create()))
		.build();

	public static Node parse(String text) {
		Node n = parser.parse(text);
		return n;
	}

}
