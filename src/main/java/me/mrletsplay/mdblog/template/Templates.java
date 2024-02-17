package me.mrletsplay.mdblog.template;

import java.util.HashMap;
import java.util.Map;

public class Templates {

	private Templates defaults;
	private Map<Template, String> templates;

	public Templates(Templates defaults) {
		this.defaults = defaults;
		this.templates = new HashMap<>();
	}

	public String get(Template template) {
		return templates.getOrDefault(template, defaults == null ? null : defaults.get(template));
	}

	public void put(Template template, String templateContent) {
		templates.put(template, templateContent);
	}

	public String render(Template template, String... variables) {
		if(variables.length % 2 != 0) throw new IllegalArgumentException("Invalid number of arguments");

		String content = get(template);

		for(int i = 0; i < variables.length; i += 2) {
			String variable = variables[i];
			content = content.replace("{" + variable + "}", variables[i + 1]);
		}

		return content;
	}

}
