package cn.eoe.app.entity;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * Regression test for the user-center fragment state model.
 *
 * <p>
 * Background: {@code UserCenterActivity} used to build {@code UserCollectFragment},
 * {@code UserIntroFragment} and {@code UserLogOutFragment} through custom
 * constructors that stashed an {@code Activity} / {@code UserResponse} directly in
 * fields. After a configuration change (rotation, language switch) or a process
 * death, the {@code FragmentManager} re-creates fragments via reflection using the
 * required <em>no-argument</em> constructor and re-supplies their {@code arguments}
 * {@link android.os.Bundle}. The old fragments had no no-arg constructor and kept
 * no state in arguments, so they failed to instantiate or came back with null
 * references and lost the user's data.
 * </p>
 *
 * <p>
 * The fix moves all fragment state into the arguments {@code Bundle} via
 * {@code Bundle.putSerializable(...)}. For that to actually preserve the user's
 * data across a process death, the entire {@link UserResponse} object graph must
 * be {@link Serializable} and must round-trip without loss — the framework
 * serializes the arguments Bundle and restores it later. This test exercises that
 * contract with plain Java serialization (no Android SDK required), which is the
 * same mechanism a Bundle uses for {@code Serializable} values.
 * </p>
 *
 * <p>
 * Run it with {@code tests/run-tests.sh}. It is intentionally JUnit-free and
 * Android-free so it can be compiled and executed with a bare JDK.
 * </p>
 */
public class UserResponseStateRestorationTest {

	private static int failures = 0;

	public static void main(String[] args) {
		testEntitiesAreSerializable();
		testUserResponseSurvivesBundleRoundTrip();
		testSelectedFavoriteListSurvivesRoundTrip();

		if (failures == 0) {
			System.out.println("ALL TESTS PASSED");
		} else {
			System.out.println(failures + " TEST(S) FAILED");
			System.exit(1);
		}
	}

	/**
	 * The arguments Bundle can only carry the user data if every reachable type
	 * implements {@link Serializable}. Guard each class explicitly so a future
	 * edit that drops {@code implements Serializable} fails loudly here instead of
	 * crashing on a device after a rotation.
	 */
	private static void testEntitiesAreSerializable() {
		assertSerializable(UserResponse.class);
		assertSerializable(UserInfoItem.class);
		assertSerializable(UserIcon.class);
		assertSerializable(UserFavoriteList.class);
		assertSerializable(UserCollectionItem.class);
	}

	/**
	 * A fully populated {@link UserResponse} (what {@code UserCollectFragment} and
	 * {@code UserIntroFragment} store in their arguments) must come back from a
	 * serialization round-trip with every field intact.
	 */
	private static void testUserResponseSurvivesBundleRoundTrip() {
		UserResponse original = newSampleResponse();

		UserResponse restored = (UserResponse) roundTrip(original);
		if (restored == null) {
			return; // roundTrip already recorded the failure
		}

		check("restored response is a distinct instance", restored != original);

		// info
		UserInfoItem info = restored.getInfo();
		check("info is restored", info != null);
		if (info != null) {
			check("info.name preserved", "张三".equals(info.getName()));
			check("info.level preserved", "5".equals(info.getLevel()));
			check("info.points preserved", "1200".equals(info.getPoints()));
			check("info.eoe_p preserved", "88".equals(info.getEoe_p()));
			check("info.eoe_m preserved", "42".equals(info.getEoe_m()));
			check("info.reg_at preserved", "2014-01-01".equals(info.getReg_at()));
			check("info.head_image_url preserved",
					"http://img/avatar=small.png".equals(info.getHead_image_url()));

			List<UserIcon> icons = info.getIcon();
			check("icons restored", icons != null && icons.size() == 2);
			if (icons != null && icons.size() == 2) {
				check("icon[0].img preserved",
						"http://img/gold.png".equals(icons.get(0).getImg()));
				check("icon[1].name preserved",
						"medal-silver".equals(icons.get(1).getName()));
			}
		}

		// favorite lists
		List<UserFavoriteList> favorite = restored.getFavorite();
		check("favorite list restored", favorite != null && favorite.size() == 2);
		if (favorite != null && favorite.size() == 2) {
			UserFavoriteList first = favorite.get(0);
			check("favorite[0].name preserved", "技术".equals(first.getName()));
			check("favorite[0].lists size preserved",
					first.getLists() != null && first.getLists().size() == 2);
			if (first.getLists() != null && first.getLists().size() == 2) {
				UserCollectionItem item = first.getLists().get(0);
				check("collection item title preserved",
						"收藏标题1".equals(item.getTitle()));
				check("collection item url preserved",
						"http://a/1".equals(item.getUrl()));
				check("collection item short_content preserved",
						"摘要1".equals(item.getShort_content()));
			}
			check("favorite[1].name preserved",
					"设计".equals(favorite.get(1).getName()));
		}
	}

	/**
	 * {@code UserCollectListFragment} keeps the currently selected
	 * {@link UserFavoriteList} in its arguments so the user's choice — not just
	 * the default first category — is restored. Verify a single favorite list
	 * round-trips on its own.
	 */
	private static void testSelectedFavoriteListSurvivesRoundTrip() {
		UserFavoriteList original = newSampleResponse().getFavorite().get(1);

		UserFavoriteList restored = (UserFavoriteList) roundTrip(original);
		if (restored == null) {
			return;
		}
		check("selected favorite name preserved", "设计".equals(restored.getName()));
		check("selected favorite lists preserved",
				restored.getLists() != null && restored.getLists().size() == 1);
	}

	// ----- helpers -------------------------------------------------------

	private static UserResponse newSampleResponse() {
		UserIcon gold = new UserIcon();
		gold.setName("medal-gold");
		gold.setImg("http://img/gold.png");
		UserIcon silver = new UserIcon();
		silver.setName("medal-silver");
		silver.setImg("http://img/silver.png");

		UserInfoItem info = new UserInfoItem();
		info.setName("张三");
		info.setLevel("5");
		info.setPoints("1200");
		info.setEoe_p("88");
		info.setEoe_m("42");
		info.setReg_at("2014-01-01");
		info.setHead_image_url("http://img/avatar=small.png");
		List<UserIcon> icons = new ArrayList<UserIcon>();
		icons.add(gold);
		icons.add(silver);
		info.setIcon(icons);

		UserCollectionItem c1 = new UserCollectionItem();
		c1.setTitle("收藏标题1");
		c1.setUrl("http://a/1");
		c1.setShort_content("摘要1");
		UserCollectionItem c2 = new UserCollectionItem();
		c2.setTitle("收藏标题2");
		c2.setUrl("http://a/2");
		c2.setShort_content("摘要2");

		UserFavoriteList tech = new UserFavoriteList();
		tech.setName("技术");
		List<UserCollectionItem> techItems = new ArrayList<UserCollectionItem>();
		techItems.add(c1);
		techItems.add(c2);
		tech.setLists(techItems);

		UserFavoriteList design = new UserFavoriteList();
		design.setName("设计");
		List<UserCollectionItem> designItems = new ArrayList<UserCollectionItem>();
		designItems.add(c1);
		design.setLists(designItems);

		UserResponse response = new UserResponse();
		response.setInfo(info);
		List<UserFavoriteList> favorite = new ArrayList<UserFavoriteList>();
		favorite.add(tech);
		favorite.add(design);
		response.setFavorite(favorite);
		return response;
	}

	/** Serialize then deserialize, mirroring how a Bundle stores a Serializable. */
	private static Object roundTrip(Object value) {
		try {
			ByteArrayOutputStream bos = new ByteArrayOutputStream();
			ObjectOutputStream oos = new ObjectOutputStream(bos);
			oos.writeObject(value);
			oos.close();
			ObjectInputStream ois = new ObjectInputStream(
					new ByteArrayInputStream(bos.toByteArray()));
			Object restored = ois.readObject();
			ois.close();
			return restored;
		} catch (Exception e) {
			check("round-trip of " + value.getClass().getSimpleName()
					+ " did not throw (" + e + ")", false);
			return null;
		}
	}

	private static void assertSerializable(Class<?> type) {
		check(type.getSimpleName() + " implements Serializable",
				Serializable.class.isAssignableFrom(type));
	}

	private static void check(String message, boolean condition) {
		if (condition) {
			System.out.println("  [PASS] " + message);
		} else {
			failures++;
			System.out.println("  [FAIL] " + message);
		}
	}
}
