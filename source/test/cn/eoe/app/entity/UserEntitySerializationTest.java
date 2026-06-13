package cn.eoe.app.entity;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;

/**
 * Regression tests verifying that the UserResponse entity graph implements
 * Serializable correctly — every class in the chain must be Serializable,
 * and the data must survive a full serialize/deserialize round-trip.
 *
 * This matters because Fragment.setArguments() uses Bundle.putSerializable()
 * under the hood to persist data across configuration changes.
 *
 * Pure JUnit — no Android runtime required.
 */
public class UserEntitySerializationTest {

	// ================================================================
	// 1. Every entity class in the UserResponse graph is Serializable
	// ================================================================

	public void testUserResponse_isSerializable() {
		assertSerializable(UserResponse.class);
	}

	public void testUserInfoItem_isSerializable() {
		assertSerializable(UserInfoItem.class);
	}

	public void testUserIcon_isSerializable() {
		assertSerializable(UserIcon.class);
	}

	public void testUserFavoriteList_isSerializable() {
		assertSerializable(UserFavoriteList.class);
	}

	public void testUserCollectionItem_isSerializable() {
		assertSerializable(UserCollectionItem.class);
	}

	private void assertSerializable(Class<?> clazz) {
		if (!Serializable.class.isAssignableFrom(clazz)) {
			throw new AssertionError(clazz.getName()
					+ " does not implement Serializable. "
					+ "Fragment arguments require all entity classes to be Serializable.");
		}
	}

	// ================================================================
	// 2. serialVersionUID is declared (prevents InvalidClassException
	//    when the class evolves)
	// ================================================================

	public void testUserResponse_hasSerialVersionUID() throws Exception {
		assertHasSerialVersionUID(UserResponse.class);
	}

	public void testUserInfoItem_hasSerialVersionUID() throws Exception {
		assertHasSerialVersionUID(UserInfoItem.class);
	}

	public void testUserIcon_hasSerialVersionUID() throws Exception {
		assertHasSerialVersionUID(UserIcon.class);
	}

	public void testUserFavoriteList_hasSerialVersionUID() throws Exception {
		assertHasSerialVersionUID(UserFavoriteList.class);
	}

	public void testUserCollectionItem_hasSerialVersionUID() throws Exception {
		assertHasSerialVersionUID(UserCollectionItem.class);
	}

	private void assertHasSerialVersionUID(Class<?> clazz) throws Exception {
		Field field = clazz.getDeclaredField("serialVersionUID");
		int mods = field.getModifiers();
		if (!Modifier.isStatic(mods) || !Modifier.isFinal(mods)) {
			throw new AssertionError(clazz.getName()
					+ ".serialVersionUID must be static final");
		}
	}

	// ================================================================
	// 3. Full round-trip serialization of each entity individually
	// ================================================================

	public void testUserIcon_roundTrip() throws Exception {
		UserIcon original = new UserIcon();
		original.setName("gold_medal");
		original.setImg("http://cdn.example.com/gold.png");

		UserIcon restored = roundTrip(original);
		assertEqual("name", "gold_medal", restored.getName());
		assertEqual("img", "http://cdn.example.com/gold.png",
				restored.getImg());
	}

	public void testUserCollectionItem_roundTrip() throws Exception {
		UserCollectionItem original = new UserCollectionItem();
		original.setTitle("Test Article");
		original.setShort_content("A short summary");
		original.setUrl("http://example.com/article/1");

		UserCollectionItem restored = roundTrip(original);
		assertEqual("title", "Test Article", restored.getTitle());
		assertEqual("short_content", "A short summary",
				restored.getShort_content());
		assertEqual("url", "http://example.com/article/1",
				restored.getUrl());
	}

	public void testUserFavoriteList_roundTrip() throws Exception {
		UserFavoriteList original = new UserFavoriteList();
		original.setName("Android Dev");

		List<UserCollectionItem> items = new ArrayList<UserCollectionItem>();
		UserCollectionItem item = new UserCollectionItem();
		item.setTitle("Fragment Guide");
		item.setShort_content("How fragments work");
		item.setUrl("http://example.com/fragments");
		items.add(item);
		original.setLists(items);

		UserFavoriteList restored = roundTrip(original);
		assertEqual("name", "Android Dev", restored.getName());
		assertEqual("list size", "1",
				String.valueOf(restored.getLists().size()));
		assertEqual("item title", "Fragment Guide",
				restored.getLists().get(0).getTitle());
	}

	public void testUserInfoItem_roundTrip() throws Exception {
		UserInfoItem original = new UserInfoItem();
		original.setName("johndoe");
		original.setLevel("10");
		original.setPoints("5000");
		original.setEoe_p("100");
		original.setEoe_m("200");
		original.setReg_at("2023-06-01");
		original.setHead_image_url("http://cdn.example.com/avatar.jpg");

		List<UserIcon> icons = new ArrayList<UserIcon>();
		UserIcon icon = new UserIcon();
		icon.setName("contributor");
		icon.setImg("http://cdn.example.com/contributor.png");
		icons.add(icon);
		original.setIcon(icons);

		UserInfoItem restored = roundTrip(original);
		assertEqual("name", "johndoe", restored.getName());
		assertEqual("level", "10", restored.getLevel());
		assertEqual("points", "5000", restored.getPoints());
		assertEqual("eoe_p", "100", restored.getEoe_p());
		assertEqual("eoe_m", "200", restored.getEoe_m());
		assertEqual("reg_at", "2023-06-01", restored.getReg_at());
		assertEqual("head_image_url",
				"http://cdn.example.com/avatar.jpg",
				restored.getHead_image_url());
		assertEqual("icon count", "1",
				String.valueOf(restored.getIcon().size()));
		assertEqual("icon[0].name", "contributor",
				restored.getIcon().get(0).getName());
	}

	public void testUserResponse_fullGraph_roundTrip() throws Exception {
		UserResponse original = buildFullUserResponse();

		UserResponse restored = roundTrip(original);

		// Info block
		assertEqual("info.name", "alice",
				restored.getInfo().getName());
		assertEqual("info.level", "7",
				restored.getInfo().getLevel());
		assertEqual("info.eoe_m", "500",
				restored.getInfo().getEoe_m());
		assertEqual("info.eoe_p", "75",
				restored.getInfo().getEoe_p());
		assertEqual("info.reg_at", "2024-03-20",
				restored.getInfo().getReg_at());
		assertEqual("info.head_image_url",
				"http://cdn.example.com/alice=small.jpg",
				restored.getInfo().getHead_image_url());

		// Icons
		assertEqual("icon count", "2",
				String.valueOf(restored.getInfo().getIcon().size()));
		assertEqual("icon[0].name", "early_adopter",
				restored.getInfo().getIcon().get(0).getName());
		assertEqual("icon[1].name", "top_writer",
				restored.getInfo().getIcon().get(1).getName());

		// Favorites
		assertEqual("favorite count", "2",
				String.valueOf(restored.getFavorite().size()));
		assertEqual("fav[0].name", "Kotlin",
				restored.getFavorite().get(0).getName());
		assertEqual("fav[0] items", "2",
				String.valueOf(restored.getFavorite().get(0).getLists()
						.size()));
		assertEqual("fav[0].item[0].title", "Coroutines Deep Dive",
				restored.getFavorite().get(0).getLists().get(0)
						.getTitle());
		assertEqual("fav[1].name", "Architecture",
				restored.getFavorite().get(1).getName());
		assertEqual("fav[1] items", "1",
				String.valueOf(restored.getFavorite().get(1).getLists()
						.size()));
	}

	// ================================================================
	// 4. Null-safety: null fields should survive serialization
	// ================================================================

	public void testUserResponse_nullFields_roundTrip() throws Exception {
		UserResponse original = new UserResponse();
		// Both info and favorite are null

		UserResponse restored = roundTrip(original);
		if (restored.getInfo() != null) {
			throw new AssertionError(
					"Expected null info but got: " + restored.getInfo());
		}
		if (restored.getFavorite() != null) {
			throw new AssertionError(
					"Expected null favorite but got: " + restored.getFavorite());
		}
	}

	public void testUserFavoriteList_nullLists_roundTrip() throws Exception {
		UserFavoriteList original = new UserFavoriteList();
		original.setName("empty-list");
		// lists field is null

		UserFavoriteList restored = roundTrip(original);
		assertEqual("name", "empty-list", restored.getName());
		if (restored.getLists() != null) {
			throw new AssertionError(
					"Expected null lists but got: " + restored.getLists());
		}
	}

	// ================================================================
	// 5. Double serialization (simulates save → restore → save)
	// ================================================================

	public void testDoubleSerialization_roundTrip() throws Exception {
		UserResponse original = buildFullUserResponse();

		// Serialize once (simulates saving to Bundle)
		UserResponse first = roundTrip(original);

		// Serialize again (simulates another config change)
		UserResponse second = roundTrip(first);

		assertEqual("double-round-trip name", "alice",
				second.getInfo().getName());
		assertEqual("double-round-trip fav count", "2",
				String.valueOf(second.getFavorite().size()));
		assertEqual("double-round-trip item",
				"Coroutines Deep Dive",
				second.getFavorite().get(0).getLists().get(0)
						.getTitle());
	}

	// ================================================================
	// Helpers
	// ================================================================

	@SuppressWarnings("unchecked")
	private <T extends Serializable> T roundTrip(T obj) throws Exception {
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		ObjectOutputStream oos = new ObjectOutputStream(baos);
		oos.writeObject(obj);
		oos.close();

		ByteArrayInputStream bais = new ByteArrayInputStream(
				baos.toByteArray());
		ObjectInputStream ois = new ObjectInputStream(bais);
		T result = (T) ois.readObject();
		ois.close();
		return result;
	}

	private static void assertEqual(String label, String expected,
			String actual) {
		if (expected == null && actual == null) {
			return;
		}
		if (expected == null || !expected.equals(actual)) {
			throw new AssertionError(label + ": expected '" + expected
					+ "' but got '" + actual + "'");
		}
	}

	private static UserResponse buildFullUserResponse() {
		UserResponse response = new UserResponse();

		UserInfoItem info = new UserInfoItem();
		info.setName("alice");
		info.setLevel("7");
		info.setPoints("3200");
		info.setEoe_p("75");
		info.setEoe_m("500");
		info.setReg_at("2024-03-20");
		info.setHead_image_url("http://cdn.example.com/alice=small.jpg");

		List<UserIcon> icons = new ArrayList<UserIcon>();
		UserIcon icon1 = new UserIcon();
		icon1.setName("early_adopter");
		icon1.setImg("http://cdn.example.com/early.png");
		icons.add(icon1);
		UserIcon icon2 = new UserIcon();
		icon2.setName("top_writer");
		icon2.setImg("http://cdn.example.com/writer.png");
		icons.add(icon2);
		info.setIcon(icons);

		response.setInfo(info);

		List<UserFavoriteList> favorites = new ArrayList<UserFavoriteList>();

		UserFavoriteList fav1 = new UserFavoriteList();
		fav1.setName("Kotlin");
		List<UserCollectionItem> items1 = new ArrayList<UserCollectionItem>();
		UserCollectionItem i1 = new UserCollectionItem();
		i1.setTitle("Coroutines Deep Dive");
		i1.setShort_content("Advanced coroutine patterns");
		i1.setUrl("http://example.com/coroutines");
		items1.add(i1);
		UserCollectionItem i2 = new UserCollectionItem();
		i2.setTitle("Flow Basics");
		i2.setShort_content("Kotlin Flow introduction");
		i2.setUrl("http://example.com/flow");
		items1.add(i2);
		fav1.setLists(items1);
		favorites.add(fav1);

		UserFavoriteList fav2 = new UserFavoriteList();
		fav2.setName("Architecture");
		List<UserCollectionItem> items2 = new ArrayList<UserCollectionItem>();
		UserCollectionItem i3 = new UserCollectionItem();
		i3.setTitle("MVVM Guide");
		i3.setShort_content("Model-View-ViewModel pattern");
		i3.setUrl("http://example.com/mvvm");
		items2.add(i3);
		fav2.setLists(items2);
		favorites.add(fav2);

		response.setFavorite(favorites);

		return response;
	}

	// ================================================================
	// Test runner (for environments without a JUnit runner)
	// ================================================================

	public static void main(String[] args) throws Exception {
		UserEntitySerializationTest test = new UserEntitySerializationTest();
		int passed = 0;
		int failed = 0;
		List<String> failures = new ArrayList<String>();

		java.lang.reflect.Method[] methods = UserEntitySerializationTest.class
				.getDeclaredMethods();
		for (java.lang.reflect.Method m : methods) {
			if (m.getName().startsWith("test")
					&& m.getParameterTypes().length == 0
					&& Modifier.isPublic(m.getModifiers())) {
				try {
					m.invoke(test);
					passed++;
					System.out.println("  PASS: " + m.getName());
				} catch (Throwable t) {
					Throwable cause = t.getCause() != null ? t.getCause() : t;
					failed++;
					failures.add(m.getName() + ": " + cause.getMessage());
					System.out.println("  FAIL: " + m.getName() + " — "
							+ cause.getMessage());
				}
			}
		}

		System.out.println();
		System.out.println("Results: " + passed + " passed, " + failed
				+ " failed");
		if (!failures.isEmpty()) {
			System.out.println("Failures:");
			for (String f : failures) {
				System.out.println("  - " + f);
			}
			System.exit(1);
		}
	}
}
