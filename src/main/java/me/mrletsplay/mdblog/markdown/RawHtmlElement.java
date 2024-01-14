package me.mrletsplay.mdblog.markdown;

import me.mrletsplay.simplehttpserver.dom.html.HtmlElement;

public class RawHtmlElement extends HtmlElement {

	private String raw;

	public RawHtmlElement(String raw) {
		super("raw");
		this.raw = raw;
	}

	@Override
	public String toString() {
		return raw;
	}

}
