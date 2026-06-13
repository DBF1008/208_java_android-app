package cn.eoe.app.utils;

/**
 * 判断一次网络响应是否“有效”——即是否足以用来刷新本地/内存缓存。
 *
 * <p>
 * 之所以单独抽成一个不依赖 Android 的工具类，是为了：
 * <ul>
 * <li>给请求缓存提供<b>唯一</b>的有效性判定，保证“只有拿到有效响应才刷新缓存”的一致语义；</li>
 * <li>让这段核心判定逻辑可以脱离 Android 框架被单元测试覆盖（见 ResponseValidatorTest）。</li>
 * </ul>
 *
 * <p>
 * 历史缺陷：调用方曾用 {@code result.equals(null) && result.equals("")} 做校验，
 * 这是一个永远为 {@code false} 的无效判断（{@code Object.equals(null)} 恒为 false，
 * 且一个值不可能同时既是 null 又是空串），并且当 {@code result} 为 null 时还会先抛出
 * 空指针异常。结果是网络失败/空响应也会去覆盖本地缓存，把最后一份可用缓存冲掉。
 */
public final class ResponseValidator {

	private ResponseValidator() {
		// 工具类，禁止实例化
	}

	/**
	 * 判断响应内容是否有效。
	 *
	 * <p>
	 * 仅当响应<b>非 null</b> 且<b>去掉首尾空白后仍有内容</b>时才认为有效。
	 * 因此以下情形都会被判定为无效，调用方据此<b>不应</b>刷新缓存：
	 * <ul>
	 * <li>网络失败 / 底层返回 {@code null}；</li>
	 * <li>服务端返回空字符串 {@code ""}；</li>
	 * <li>服务端只返回空白字符（空格、换行、制表符等）。</li>
	 * </ul>
	 *
	 * <p>
	 * 注意：对于“非空但被截断的半截内容”（例如截断的 JSON），由于缓存层并不感知具体
	 * 内容格式，这里无法在通用层面可靠识别，需由上层按内容类型自行校验。本方法只负责
	 * 拦截 null / 空 / 纯空白这类一定无效的响应。
	 *
	 * @param response 待校验的响应内容，允许为 {@code null}
	 * @return {@code true} 表示有效、可用于刷新缓存；{@code false} 表示无效、应保留旧缓存
	 */
	public static boolean isValidResponse(String response) {
		return response != null && response.trim().length() > 0;
	}
}
