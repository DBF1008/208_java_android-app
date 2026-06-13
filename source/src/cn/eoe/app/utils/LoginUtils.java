package cn.eoe.app.utils;

/**
 * 登录状态相关的纯逻辑工具方法。
 *
 * <p>刻意不依赖任何 Android 类型，便于在没有 Android 运行环境的情况下做回归测试。
 * 详情页的本地评价状态（点赞/踩/收藏）需要按账号隔离，而“是否已登录”这一判断在
 * 读取、保存、点击三处都会用到。集中到这里，避免再次出现像
 * {@code mKey.equals(null) && mKey.equals("")} 这种永远不成立、导致空账号状态被写库的写法。
 */
public final class LoginUtils {

    private LoginUtils() {
    }

    /**
     * 判断登录 key 是否表示一个已登录账号。
     *
     * @param key 登录后保存在 SharedPreferences 中的 key，未登录时通常为 {@code null} 或空串
     * @return 仅当 key 非 {@code null} 且非空串时返回 {@code true}
     */
    public static boolean isLoggedIn(String key) {
        return key != null && !key.isEmpty();
    }
}
