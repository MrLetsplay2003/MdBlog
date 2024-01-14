package me.mrletsplay.mdblog.markdown;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import org.commonmark.ext.gfm.strikethrough.Strikethrough;
import org.commonmark.ext.gfm.tables.TableBlock;
import org.commonmark.ext.gfm.tables.TableBody;
import org.commonmark.ext.gfm.tables.TableCell;
import org.commonmark.ext.gfm.tables.TableHead;
import org.commonmark.ext.gfm.tables.TableRow;
import org.commonmark.ext.ins.Ins;
import org.commonmark.ext.task.list.items.TaskListItemMarker;
import org.commonmark.node.BlockQuote;
import org.commonmark.node.BulletList;
import org.commonmark.node.Code;
import org.commonmark.node.Document;
import org.commonmark.node.Emphasis;
import org.commonmark.node.FencedCodeBlock;
import org.commonmark.node.HardLineBreak;
import org.commonmark.node.Heading;
import org.commonmark.node.HtmlBlock;
import org.commonmark.node.HtmlInline;
import org.commonmark.node.Image;
import org.commonmark.node.IndentedCodeBlock;
import org.commonmark.node.Link;
import org.commonmark.node.LinkReferenceDefinition;
import org.commonmark.node.ListItem;
import org.commonmark.node.Node;
import org.commonmark.node.OrderedList;
import org.commonmark.node.Paragraph;
import org.commonmark.node.SoftLineBreak;
import org.commonmark.node.StrongEmphasis;
import org.commonmark.node.Text;
import org.commonmark.node.ThematicBreak;

import me.mrletsplay.simplehttpserver.dom.html.HtmlElement;

public class MdRenderer {

	public HtmlElement render(Node node) {
		MdRenderContext ctx = new MdRenderContext(this);

		HtmlElement element = renderSingleNode(ctx, node);
		if(element == null) return null;
		Node ch = node.getFirstChild();
		if(ch == null) return element;
		do {
			HtmlElement chEl = render(ch);
			if(chEl == null) continue;
			element.appendChild(chEl);
		}while((ch = ch.getNext()) != null);
		return element;
	}

	private HtmlElement renderSingleNode(MdRenderContext ctx, Node node) {
		try {
			Method m = MdRenderer.class.getDeclaredMethod("render", MdRenderContext.class, node.getClass());
			return (HtmlElement) m.invoke(this, ctx, node);
		}catch(NoSuchMethodException e) {
			System.err.println("Warning: No render() method defined for " + node.getClass().getName());
			return null;
		} catch (SecurityException | IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
			throw new RuntimeException(e);
		}
	}

	public HtmlElement render(MdRenderContext ctx, Document node) {
		HtmlElement el = new HtmlElement("div");
		el.setAttribute("md-document");
		return el;
	}

	public HtmlElement render(MdRenderContext ctx, Heading node) {
		return new HtmlElement("h" + Math.min(6, node.getLevel()));
	}

	public HtmlElement render(MdRenderContext ctx, Paragraph node) {
		return new HtmlElement("p");
	}

	public HtmlElement render(MdRenderContext ctx, BlockQuote node) {
		return new HtmlElement("blockquote");
	}

	public HtmlElement render(MdRenderContext ctx, BulletList node) {
		return new HtmlElement("ul");
	}

	public HtmlElement render(MdRenderContext ctx, FencedCodeBlock node) {
		HtmlElement el = new HtmlElement("pre");
		HtmlElement code = new HtmlElement("code");
		code.setText(node.getLiteral());
		el.appendChild(code);
		return el;
	}

	public HtmlElement render(MdRenderContext ctx, HtmlBlock node) {
		return new RawHtmlElement(node.getLiteral());
	}

	public HtmlElement render(MdRenderContext ctx, ThematicBreak node) {
		HtmlElement el = new HtmlElement("hr");
		el.setSelfClosing(true);
		return el;
	}

	public HtmlElement render(MdRenderContext ctx, IndentedCodeBlock node) {
		HtmlElement el = new HtmlElement("pre");
		HtmlElement code = new HtmlElement("code");
		code.setText(node.getLiteral());
		el.appendChild(code);
		return el;
	}

	public HtmlElement render(MdRenderContext ctx, Link node) {
		HtmlElement el = new HtmlElement("a");
		el.setAttribute("href", node.getDestination());
		el.setAttribute("title", node.getTitle());
		return el;
	}

	public HtmlElement render(MdRenderContext ctx, ListItem node) {
		return new HtmlElement("li");
	}

	public HtmlElement render(MdRenderContext ctx, OrderedList node) {
		HtmlElement el = new HtmlElement("ol");
		el.setAttribute("start", String.valueOf(node.getStartNumber()));
		return el;
	}

	public HtmlElement render(MdRenderContext ctx, Image node) {
		return HtmlElement.img(node.getDestination(), node.getTitle());
	}

	public HtmlElement render(MdRenderContext ctx, Emphasis node) {
		return new HtmlElement("em");
	}

	public HtmlElement render(MdRenderContext ctx, StrongEmphasis node) {
		return new HtmlElement("strong");
	}

	public HtmlElement render(MdRenderContext ctx, Text node) {
		HtmlElement el = new HtmlElement("span");
		el.setText(node.getLiteral());
		return el;
	}

	public HtmlElement render(MdRenderContext ctx, Code node) {
		HtmlElement el = new HtmlElement("code");
		el.setText(node.getLiteral());
		return el;
	}

	public HtmlElement render(MdRenderContext ctx, HtmlInline node) {
		return new RawHtmlElement(node.getLiteral());
	}

	public HtmlElement render(MdRenderContext ctx, SoftLineBreak node) {
		return new RawHtmlElement(" ");
	}

	public HtmlElement render(MdRenderContext ctx, HardLineBreak node) {
		return HtmlElement.br();
	}

	public HtmlElement render(MdRenderContext ctx, LinkReferenceDefinition node) {
		return null;
	}

	public HtmlElement render(MdRenderContext ctx, Strikethrough node) {
		return new HtmlElement("del");
	}

	public HtmlElement render(MdRenderContext ctx, TableBlock node) {
		return new HtmlElement("table");
	}

	public HtmlElement render(MdRenderContext ctx, TableHead node) {
		return new HtmlElement("thead");
	}

	public HtmlElement render(MdRenderContext ctx, TableBody node) {
		return new HtmlElement("tbody");
	}

	public HtmlElement render(MdRenderContext ctx, TableRow node) {
		return new HtmlElement("tr");
	}

	public HtmlElement render(MdRenderContext ctx, TableCell node) {
		return new HtmlElement("td");
	}

	public HtmlElement render(MdRenderContext ctx, Ins node) {
		return new HtmlElement("ins");
	}

	public HtmlElement render(MdRenderContext ctx, TaskListItemMarker node) {
		HtmlElement el = new HtmlElement("input");
		el.setAttribute("type", "checkbox");
		el.setAttribute("disabled");
		if(node.isChecked()) el.setAttribute("checked");
		return el;
	}

}
