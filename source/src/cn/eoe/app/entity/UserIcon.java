package cn.eoe.app.entity;

import java.io.Serializable;

public class UserIcon implements Serializable {
		private static final long serialVersionUID = 1L;

		private String name;
		private String img;

		public String getName() {
			return name;
		}

		public void setName(String name) {
			this.name = name;
		}

		public String getImg() {
			return img;
		}

		public void setImg(String img) {
			this.img = img;
		}

}
