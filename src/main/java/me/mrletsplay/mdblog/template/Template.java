package me.mrletsplay.mdblog.template;

public enum Template {

	INDEX("index"),
	INDEX_POST("index-post"),
	INDEX_SUB_BLOG("index-sub-blog"),
	POST("post"),
	;

	private final String name;

	private Template(String name) {
		this.name = name;
	}

	public String getName() {
		return name;
	}

}
