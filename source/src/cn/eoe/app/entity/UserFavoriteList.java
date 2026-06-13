package cn.eoe.app.entity;

import java.io.Serializable;
import java.util.List;

public class UserFavoriteList implements Serializable {

	private static final long serialVersionUID = 1L;

	private String name;
	private List<UserCollectionItem> lists;

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public List<UserCollectionItem> getLists() {
		return lists;
	}

	public void setLists(List<UserCollectionItem> lists) {
		this.lists = lists;
	}

}

