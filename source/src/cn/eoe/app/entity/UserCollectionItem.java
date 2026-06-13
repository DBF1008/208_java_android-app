package cn.eoe.app.entity;

import java.io.Serializable;

public class UserCollectionItem implements Serializable {

	private static final long serialVersionUID = 1L;
	private String title;
	private String short_content;
	private String url;

	public String getTitle() {
		return title;
	}

	public void setTitle(String title) {
		this.title = title;
	}

	public String getUrl() {
		return url;
	}

	public void setUrl(String url) {
		this.url = url;
	}

	public String getShort_content() {
		return short_content;
	}

	public void setShort_content(String short_content) {
		this.short_content = short_content;
	}
}
