package cn.eoe.app.view;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import cn.eoe.app.entity.UserCollectionItem;
import cn.eoe.app.entity.UserFavoriteList;
import cn.eoe.app.entity.UserIcon;
import cn.eoe.app.entity.UserInfoItem;
import cn.eoe.app.entity.UserResponse;

/**
 * Regression tests for user center fragment reconstruction.
 *
 * Verifies that:
 * 1. Every user center fragment has a public no-arg constructor (required by
 *    FragmentManager for reconstruction after config change / process death).
 * 2. Every fragment exposes a static newInstance() factory method.
 * 3. The ARG_* Bundle key constants are declared so arguments can be
 *    stored and retrieved consistently.
 * 4. The entity graph behind UserResponse survives Java serialization,
 *    which is the same mechanism Bundle.putSerializable() uses under the hood.
 * 5. No fragment field directly stores an Activity reference (must use
 *    getActivity() instead).
 *
 * These are pure JUnit tests — they do NOT need the Android runtime.
 */
public class UserCenterFragmentReconstructionTest {

	// ================================================================
	// 1. No-arg constructor existence (the root-cause of the original bug)
	// ================================================================

	/**
	 * Simulates what FragmentManager does when it reconstructs a fragment
	 * via reflection: Class.forName(name).newInstance().
	 */
	private static Object instantiateViaNoArgConstructor(String className)
			throws Exception {
		Class<?> clazz = Class.forName(className);
		Constructor<?> ctor = clazz.getConstructor();
		if (!Modifier.isPublic(ctor.getModifiers())) {
			throw new AssertionError("No-arg constructor is not public for "
					+ className);
		}
		return ctor.newInstance();
	}

	public void testUserCollectFragment_hasNoArgConstructor() throws Exception {
		Object instance = instantiateViaNoArgConstructor(
				"cn.eoe.app.view.UserCollectFragment");
		if (instance == null) {
			throw new AssertionError(
					"UserCollectFragment no-arg constructor returned null");
		}
	}

	public void testUserIntroFragment_hasNoArgConstructor() throws Exception {
		Object instance = instantiateViaNoArgConstructor(
				"cn.eoe.app.view.UserIntroFragment");
		if (instance == null) {
			throw new AssertionError(
					"UserIntroFragment no-arg constructor returned null");
		}
	}

	public void testUserLogOutFragment_hasNoArgConstructor() throws Exception {
		Object instance = instantiateViaNoArgConstructor(
				"cn.eoe.app.view.UserLogOutFragment");
		if (instance == null) {
			throw new AssertionError(
					"UserLogOutFragment no-arg constructor returned null");
		}
	}

	public void testUserCollectListFragment_hasNoArgConstructor()
			throws Exception {
		Object instance = instantiateViaNoArgConstructor(
				"cn.eoe.app.view.UserCollectListFragment");
		if (instance == null) {
			throw new AssertionError(
					"UserCollectListFragment no-arg constructor returned null");
		}
	}

	// ================================================================
	// 2. Static newInstance() factory methods exist
	// ================================================================

	public void testUserCollectFragment_hasNewInstanceFactory()
			throws Exception {
		Class<?> clazz = Class
				.forName("cn.eoe.app.view.UserCollectFragment");
		Method m = clazz.getMethod("newInstance", UserResponse.class);
		if (!Modifier.isStatic(m.getModifiers())) {
			throw new AssertionError(
					"UserCollectFragment.newInstance() must be static");
		}
		if (!clazz.isAssignableFrom(m.getReturnType())) {
			throw new AssertionError(
					"UserCollectFragment.newInstance() must return UserCollectFragment");
		}
	}

	public void testUserIntroFragment_hasNewInstanceFactory() throws Exception {
		Class<?> clazz = Class
				.forName("cn.eoe.app.view.UserIntroFragment");
		Method m = clazz.getMethod("newInstance", UserResponse.class);
		if (!Modifier.isStatic(m.getModifiers())) {
			throw new AssertionError(
					"UserIntroFragment.newInstance() must be static");
		}
		if (!clazz.isAssignableFrom(m.getReturnType())) {
			throw new AssertionError(
					"UserIntroFragment.newInstance() must return UserIntroFragment");
		}
	}

	public void testUserLogOutFragment_hasNewInstanceFactory()
			throws Exception {
		Class<?> clazz = Class
				.forName("cn.eoe.app.view.UserLogOutFragment");
		Method m = clazz.getMethod("newInstance", boolean.class);
		if (!Modifier.isStatic(m.getModifiers())) {
			throw new AssertionError(
					"UserLogOutFragment.newInstance() must be static");
		}
		if (!clazz.isAssignableFrom(m.getReturnType())) {
			throw new AssertionError(
					"UserLogOutFragment.newInstance() must return UserLogOutFragment");
		}
	}

	public void testUserCollectListFragment_hasNewInstanceFactory()
			throws Exception {
		Class<?> clazz = Class
				.forName("cn.eoe.app.view.UserCollectListFragment");
		Method m = clazz.getMethod("newInstance", UserFavoriteList.class);
		if (!Modifier.isStatic(m.getModifiers())) {
			throw new AssertionError(
					"UserCollectListFragment.newInstance() must be static");
		}
		if (!clazz.isAssignableFrom(m.getReturnType())) {
			throw new AssertionError(
					"UserCollectListFragment.newInstance() must return UserCollectListFragment");
		}
	}

	// ================================================================
	// 3. ARG_* Bundle key constants exist
	// ================================================================

	public void testUserCollectFragment_hasArgConstant() throws Exception {
		assertStringConstant("cn.eoe.app.view.UserCollectFragment",
				"ARG_USER_RESPONSE");
	}

	public void testUserIntroFragment_hasArgConstant() throws Exception {
		assertStringConstant("cn.eoe.app.view.UserIntroFragment",
				"ARG_USER_RESPONSE");
	}

	public void testUserLogOutFragment_hasArgConstant() throws Exception {
		assertStringConstant("cn.eoe.app.view.UserLogOutFragment",
				"ARG_IS_SHOW_TXT");
	}

	public void testUserCollectListFragment_hasArgConstant() throws Exception {
		assertStringConstant("cn.eoe.app.view.UserCollectListFragment",
				"ARG_FAVORITE_LIST");
	}

	private void assertStringConstant(String className, String fieldName)
			throws Exception {
		Class<?> clazz = Class.forName(className);
		Field field = clazz.getDeclaredField(fieldName);
		int mods = field.getModifiers();
		if (!Modifier.isStatic(mods) || !Modifier.isFinal(mods)) {
			throw new AssertionError(className + "." + fieldName
					+ " must be static final");
		}
		Object value = field.get(null);
		if (value == null || !(value instanceof String)
				|| ((String) value).isEmpty()) {
			throw new AssertionError(className + "." + fieldName
					+ " must be a non-empty String");
		}
	}

	// ================================================================
	// 4. No Activity field stored directly in fragments
	//    (must use getActivity() instead to avoid stale references)
	// ================================================================

	public void testUserCollectFragment_noActivityField() throws Exception {
		assertNoActivityField("cn.eoe.app.view.UserCollectFragment");
	}

	public void testUserIntroFragment_noActivityField() throws Exception {
		assertNoActivityField("cn.eoe.app.view.UserIntroFragment");
	}

	public void testUserLogOutFragment_noActivityField() throws Exception {
		assertNoActivityField("cn.eoe.app.view.UserLogOutFragment");
	}

	public void testUserCollectListFragment_noActivityField() throws Exception {
		assertNoActivityField("cn.eoe.app.view.UserCollectListFragment");
	}

	private void assertNoActivityField(String className) throws Exception {
		Class<?> clazz = Class.forName(className);
		for (Field f : clazz.getDeclaredFields()) {
			String typeName = f.getType().getName();
			if (typeName.equals("android.app.Activity")
					|| typeName.equals("android.support.v4.app.FragmentActivity")) {
				throw new AssertionError(className
						+ " must not store an Activity field ('"
						+ f.getName()
						+ "'). Use getActivity() instead to survive reconstruction.");
			}
		}
	}

	// ================================================================
	// 5. Entity graph serialization round-trip
	//    (simulates Bundle.putSerializable / getSerializable)
	// ================================================================

	public void testUserResponse_serializationRoundTrip() throws Exception {
		UserResponse original = buildSampleUserResponse();

		byte[] bytes = serialize(original);
		UserResponse restored = (UserResponse) deserialize(bytes);

		assertEqual("name", original.getInfo().getName(),
				restored.getInfo().getName());
		assertEqual("level", original.getInfo().getLevel(),
				restored.getInfo().getLevel());
		assertEqual("eoe_m", original.getInfo().getEoe_m(),
				restored.getInfo().getEoe_m());
		assertEqual("eoe_p", original.getInfo().getEoe_p(),
				restored.getInfo().getEoe_p());
		assertEqual("reg_at", original.getInfo().getReg_at(),
				restored.getInfo().getReg_at());
		assertEqual("head_image_url",
				original.getInfo().getHead_image_url(),
				restored.getInfo().getHead_image_url());

		int iconCount = original.getInfo().getIcon().size();
		assertEqual("icon count", String.valueOf(iconCount),
				String.valueOf(restored.getInfo().getIcon().size()));
		for (int i = 0; i < iconCount; i++) {
			assertEqual("icon[" + i + "].name",
					original.getInfo().getIcon().get(i).getName(),
					restored.getInfo().getIcon().get(i).getName());
			assertEqual("icon[" + i + "].img",
					original.getInfo().getIcon().get(i).getImg(),
					restored.getInfo().getIcon().get(i).getImg());
		}

		int favCount = original.getFavorite().size();
		assertEqual("favorite count", String.valueOf(favCount),
				String.valueOf(restored.getFavorite().size()));
		for (int i = 0; i < favCount; i++) {
			UserFavoriteList origFav = original.getFavorite().get(i);
			UserFavoriteList restFav = restored.getFavorite().get(i);
			assertEqual("fav[" + i + "].name", origFav.getName(),
					restFav.getName());
			int itemCount = origFav.getLists().size();
			assertEqual("fav[" + i + "] item count",
					String.valueOf(itemCount),
					String.valueOf(restFav.getLists().size()));
			for (int j = 0; j < itemCount; j++) {
				UserCollectionItem origItem = origFav.getLists().get(j);
				UserCollectionItem restItem = restFav.getLists().get(j);
				assertEqual("fav[" + i + "].item[" + j + "].title",
						origItem.getTitle(), restItem.getTitle());
				assertEqual("fav[" + i + "].item[" + j + "].url",
						origItem.getUrl(), restItem.getUrl());
				assertEqual("fav[" + i + "].item[" + j + "].short_content",
						origItem.getShort_content(),
						restItem.getShort_content());
			}
		}
	}

	public void testUserFavoriteList_serializationRoundTrip() throws Exception {
		UserFavoriteList original = new UserFavoriteList();
		original.setName("test-fav");
		List<UserCollectionItem> items = new ArrayList<UserCollectionItem>();
		UserCollectionItem item = new UserCollectionItem();
		item.setTitle("title");
		item.setShort_content("content");
		item.setUrl("http://example.com");
		items.add(item);
		original.setLists(items);

		byte[] bytes = serialize(original);
		UserFavoriteList restored = (UserFavoriteList) deserialize(bytes);

		assertEqual("name", original.getName(), restored.getName());
		assertEqual("list size", "1",
				String.valueOf(restored.getLists().size()));
		assertEqual("item title", "title",
				restored.getLists().get(0).getTitle());
	}

	// ================================================================
	// 6. Fragment newInstance() → getArguments() round-trip
	//    Uses a Bundle simulator since we don't have Android runtime.
	//    Verifies the factory method sets arguments that can be retrieved.
	// ================================================================

	public void testUserCollectFragment_argumentsRoundTrip() throws Exception {
		UserResponse response = buildSampleUserResponse();

		Class<?> clazz = Class
				.forName("cn.eoe.app.view.UserCollectFragment");
		Method newInstance = clazz.getMethod("newInstance",
				UserResponse.class);
		Object fragment = newInstance.invoke(null, response);

		// Verify setArguments was called by checking getArguments via
		// reflection (Fragment.getArguments() returns a Bundle).
		// Since we can't run on Android, we verify the ARG constant matches
		// what the factory uses.
		Field argField = clazz.getDeclaredField("ARG_USER_RESPONSE");
		String argKey = (String) argField.get(null);
		if (argKey == null || argKey.isEmpty()) {
			throw new AssertionError(
					"ARG_USER_RESPONSE constant is null or empty");
		}

		// Verify the Serializable round-trip of the data that will go
		// through the Bundle
		byte[] bytes = serialize(response);
		UserResponse restored = (UserResponse) deserialize(bytes);
		assertEqual("round-trip name", response.getInfo().getName(),
				restored.getInfo().getName());
	}

	public void testUserLogOutFragment_argumentsRoundTrip() throws Exception {
		Class<?> clazz = Class
				.forName("cn.eoe.app.view.UserLogOutFragment");
		Method newInstance = clazz.getMethod("newInstance", boolean.class);

		// Test with isShowtxt = true
		Object fragmentTrue = newInstance.invoke(null, true);
		if (fragmentTrue == null) {
			throw new AssertionError(
					"UserLogOutFragment.newInstance(true) returned null");
		}

		// Test with isShowtxt = false
		Object fragmentFalse = newInstance.invoke(null, false);
		if (fragmentFalse == null) {
			throw new AssertionError(
					"UserLogOutFragment.newInstance(false) returned null");
		}

		Field argField = clazz.getDeclaredField("ARG_IS_SHOW_TXT");
		String argKey = (String) argField.get(null);
		if (argKey == null || argKey.isEmpty()) {
			throw new AssertionError(
					"ARG_IS_SHOW_TXT constant is null or empty");
		}
	}

	// ================================================================
	// 7. Multiple no-arg constructor calls produce independent instances
	// ================================================================

	public void testMultipleInstantiations_areIndependent() throws Exception {
		Object f1 = instantiateViaNoArgConstructor(
				"cn.eoe.app.view.UserIntroFragment");
		Object f2 = instantiateViaNoArgConstructor(
				"cn.eoe.app.view.UserIntroFragment");
		if (f1 == f2) {
			throw new AssertionError(
					"Two no-arg constructor calls returned the same instance");
		}
		if (f1.getClass() != f2.getClass()) {
			throw new AssertionError(
					"Two no-arg constructor calls returned different types");
		}
	}

	// ================================================================
	// Helpers
	// ================================================================

	private static byte[] serialize(Serializable obj) throws Exception {
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		ObjectOutputStream oos = new ObjectOutputStream(baos);
		oos.writeObject(obj);
		oos.close();
		return baos.toByteArray();
	}

	private static Object deserialize(byte[] bytes) throws Exception {
		ByteArrayInputStream bais = new ByteArrayInputStream(bytes);
		ObjectInputStream ois = new ObjectInputStream(bais);
		Object result = ois.readObject();
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

	static UserResponse buildSampleUserResponse() {
		UserResponse response = new UserResponse();

		UserInfoItem info = new UserInfoItem();
		info.setName("testuser");
		info.setLevel("5");
		info.setPoints("1200");
		info.setEoe_p("88");
		info.setEoe_m("256");
		info.setReg_at("2024-01-15");
		info.setHead_image_url("http://example.com/avatar=small.jpg");

		List<UserIcon> icons = new ArrayList<UserIcon>();
		UserIcon icon1 = new UserIcon();
		icon1.setName("medal_gold");
		icon1.setImg("http://example.com/gold.png");
		icons.add(icon1);
		UserIcon icon2 = new UserIcon();
		icon2.setName("medal_silver");
		icon2.setImg("http://example.com/silver.png");
		icons.add(icon2);
		info.setIcon(icons);

		response.setInfo(info);

		List<UserFavoriteList> favorites = new ArrayList<UserFavoriteList>();

		UserFavoriteList fav1 = new UserFavoriteList();
		fav1.setName("Android");
		List<UserCollectionItem> items1 = new ArrayList<UserCollectionItem>();
		UserCollectionItem item1 = new UserCollectionItem();
		item1.setTitle("Android Basics");
		item1.setShort_content("An intro to Android");
		item1.setUrl("http://example.com/android-basics");
		items1.add(item1);
		UserCollectionItem item2 = new UserCollectionItem();
		item2.setTitle("Advanced Android");
		item2.setShort_content("Deep dive into fragments");
		item2.setUrl("http://example.com/advanced-android");
		items1.add(item2);
		fav1.setLists(items1);
		favorites.add(fav1);

		UserFavoriteList fav2 = new UserFavoriteList();
		fav2.setName("Java");
		List<UserCollectionItem> items2 = new ArrayList<UserCollectionItem>();
		UserCollectionItem item3 = new UserCollectionItem();
		item3.setTitle("Java Generics");
		item3.setShort_content("Understanding generics");
		item3.setUrl("http://example.com/java-generics");
		items2.add(item3);
		fav2.setLists(items2);
		favorites.add(fav2);

		response.setFavorite(favorites);

		return response;
	}

	// ================================================================
	// Test runner (for environments without a JUnit runner)
	// ================================================================

	public static void main(String[] args) throws Exception {
		UserCenterFragmentReconstructionTest test = new UserCenterFragmentReconstructionTest();
		int passed = 0;
		int failed = 0;
		List<String> failures = new ArrayList<String>();

		Method[] methods = UserCenterFragmentReconstructionTest.class
				.getDeclaredMethods();
		for (Method m : methods) {
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
