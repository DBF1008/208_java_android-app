package cn.eoe.app.utils;

/**
 * {@link ResponseValidator} 的回归测试，锁定缺陷
 * “网络失败 / 服务端返回空内容后把已有请求缓存污染掉” 的修复。
 *
 * <p>
 * 修复前 {@code RequestCacheUtil.getStringFromWeb()} 用
 * {@code if (result.equals(null) && result.equals(""))} 做校验：
 * <ul>
 * <li>该条件恒为 {@code false}（{@code Object.equals(null)} 恒 false，
 * 且一个值不可能同时是 null 又是空串），所以空/半截响应也会去刷新缓存；</li>
 * <li>当 {@code result} 为 null 时还会先抛 NPE。</li>
 * </ul>
 * 结果是一次坏响应就会覆盖最后一份可用缓存，导致离线再也读不回旧内容。
 *
 * <p>
 * 本测试有意做成<b>零第三方依赖</b>，仅用 JDK 即可编译运行（项目本身未引入 JUnit）：
 *
 * <pre>
 * cd source
 * javac -d /tmp/rcv-out src/cn/eoe/app/utils/ResponseValidator.java \
 *                       test/cn/eoe/app/utils/ResponseValidatorTest.java
 * java -cp /tmp/rcv-out cn.eoe.app.utils.ResponseValidatorTest
 * </pre>
 *
 * 任意用例失败时进程以非 0 退出码结束，可直接接入 CI。
 */
public class ResponseValidatorTest {

	private static int passed = 0;
	private static int failed = 0;

	public static void main(String[] args) {
		// 1) 直接锁定被修复的有效性判定（旧表达式对这些输入要么判错、要么 NPE）
		testNullIsInvalid();
		testEmptyIsInvalid();
		testWhitespaceOnlyIsInvalid();
		testRealContentIsValid();
		testContentWithSurroundingWhitespaceIsValid();

		// 2) 用真实判定模拟缓存刷新决策，锁定“坏响应不刷新缓存”的端到端语义
		testBadResponseDoesNotPolluteCache();
		testGoodResponseRefreshesCache();

		System.out.println("----------------------------------------");
		System.out.println("通过: " + passed + "  失败: " + failed);
		if (failed > 0) {
			System.exit(1);
		}
	}

	// ---------------------------------------------------------------------
	// 有效性判定本身
	// ---------------------------------------------------------------------

	/** 网络失败 / 底层返回 null：旧代码会在此抛 NPE，新逻辑必须判为无效且不抛异常。 */
	private static void testNullIsInvalid() {
		check("null 视为无效", false, ResponseValidator.isValidResponse(null));
	}

	/** 服务端返回空字符串：旧的 &&-条件让它“漏网”从而污染缓存。 */
	private static void testEmptyIsInvalid() {
		check("空字符串视为无效", false, ResponseValidator.isValidResponse(""));
	}

	/** 只返回空白字符也应视为无效，避免用空白覆盖旧缓存。 */
	private static void testWhitespaceOnlyIsInvalid() {
		check("纯空格视为无效", false, ResponseValidator.isValidResponse("   "));
		check("换行/制表符等空白视为无效", false,
				ResponseValidator.isValidResponse("\n\t \r"));
	}

	/** 正常响应必须判为有效，保证有效数据仍会被缓存。 */
	private static void testRealContentIsValid() {
		check("正常 JSON 视为有效", true,
				ResponseValidator.isValidResponse("{\"response\":{}}"));
	}

	/** 带首尾空白的正常内容仍然有效（trim 只用于判空，不丢弃真实内容）。 */
	private static void testContentWithSurroundingWhitespaceIsValid() {
		check("带首尾空白的正常内容仍有效", true,
				ResponseValidator.isValidResponse("  {\"a\":1}\n"));
	}

	// ---------------------------------------------------------------------
	// 端到端语义：坏响应不得污染缓存
	// ---------------------------------------------------------------------

	/**
	 * 复刻 {@code getStringFromWeb} 的关键决策——仅当响应有效时才刷新缓存——
	 * 断言 null / 空 / 纯空白响应都不会冲掉已有缓存，也不会刷新时间戳。
	 */
	private static void testBadResponseDoesNotPolluteCache() {
		String lastGood = "{\"response\":\"old-but-good\"}";
		String[] badResponses = { null, "", "   " };
		for (String bad : badResponses) {
			FakeCache cache = new FakeCache(lastGood);
			cache.onWebResponse(bad);
			check("坏响应不污染缓存内容(" + describe(bad) + ")", lastGood, cache.value);
			check("坏响应不刷新时间戳(" + describe(bad) + ")", false, cache.refreshed);
		}
	}

	/** 有效响应必须正常刷新缓存内容与时间戳。 */
	private static void testGoodResponseRefreshesCache() {
		String lastGood = "{\"response\":\"old-but-good\"}";
		String fresh = "{\"response\":\"fresh\"}";
		FakeCache cache = new FakeCache(lastGood);
		cache.onWebResponse(fresh);
		check("有效响应刷新缓存内容", fresh, cache.value);
		check("有效响应刷新时间戳", true, cache.refreshed);
	}

	/**
	 * {@code getStringFromWeb} “只有有效响应才刷新缓存”这段决策的不依赖 Android 的镜像。
	 * 判定本身直接复用生产代码 {@link ResponseValidator#isValidResponse(String)}，
	 * {@code value} 模拟本地文件+内存软缓存中现存的内容，{@code refreshed} 模拟是否更新了
	 * DB 时间戳并覆盖了缓存。
	 */
	private static final class FakeCache {
		String value;
		boolean refreshed;

		FakeCache(String existing) {
			this.value = existing;
			this.refreshed = false;
		}

		void onWebResponse(String webResult) {
			if (!ResponseValidator.isValidResponse(webResult)) {
				return; // 无效响应：保留旧缓存
			}
			this.value = webResult;
			this.refreshed = true;
		}
	}

	// ---------------------------------------------------------------------
	// 极简断言工具（零依赖）
	// ---------------------------------------------------------------------

	private static void check(String name, boolean expected, boolean actual) {
		if (expected == actual) {
			pass(name);
		} else {
			fail(name, String.valueOf(expected), String.valueOf(actual));
		}
	}

	private static void check(String name, String expected, String actual) {
		boolean equal = (expected == null) ? (actual == null) : expected
				.equals(actual);
		if (equal) {
			pass(name);
		} else {
			fail(name, describe(expected), describe(actual));
		}
	}

	private static void pass(String name) {
		passed++;
		System.out.println("[PASS] " + name);
	}

	private static void fail(String name, String expected, String actual) {
		failed++;
		System.out.println("[FAIL] " + name + "  期望=" + expected + "  实际="
				+ actual);
	}

	private static String describe(String s) {
		if (s == null) {
			return "null";
		}
		if (s.length() == 0) {
			return "\"\"(空串)";
		}
		return "\"" + s + "\"";
	}
}
