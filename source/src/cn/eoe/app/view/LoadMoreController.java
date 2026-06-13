package cn.eoe.app.view;

/**
 * 列表分页（"加载更多"）的生命周期闸门。
 *
 * <p>背景：列表页的分页请求在后台线程发起，请求返回后需要把结果回写到 UI（追加到
 * Adapter）。如果在请求返回之前 Fragment 已经失效（用户快速切 tab、返回上级页面、
 * 旋转屏幕导致 view 被销毁，或者同一个 Fragment 被复用到了新的分类实例），晚到的
 * 结果就不能再写回，否则会把旧分页塞进新页面，甚至触碰已销毁的 view 造成崩溃 / 泄漏。
 *
 * <p>每个列表 Fragment 持有一个本对象。一次分页请求通过 {@link #begin()} 开始，它
 * 返回一个代表"当前会话"的令牌（token）。后台线程拿到结果后，必须先用
 * {@link #shouldDeliver(int)} 判断该令牌是否仍然有效，再决定是否回写。只有同时满足
 * 以下两点结果才会被投递：
 * <ul>
 *   <li>闸门仍然处于激活状态（Fragment 的 view 还活着）；</li>
 *   <li>令牌仍然等于当前会话号（期间没有开始更新的请求、view 没有被重建到新的
 *       分类实例）。</li>
 * </ul>
 *
 * <p>{@link #invalidate()} 用于在 view 被销毁 / Fragment detach 时关闭闸门，并推进
 * 会话号，使所有在途结果立即失效——这就是我们的"取消"点：不依赖中断线程，而是让
 * 晚到的结果在投递前被自然丢弃。{@link #reset()} 在 view 重新创建时调用，重新激活
 * 闸门并开启一个新的会话，从而拒绝上一轮 view 残留的结果。
 *
 * <p>本类不依赖任何 Android API，所有方法都是同步的，可在后台线程与主线程之间安全
 * 共享，并可独立进行单元测试。
 */
public final class LoadMoreController {

    /** {@link #begin()} 在不允许发起新请求时返回的令牌，永远不会被投递。 */
    public static final int INVALID_TOKEN = -1;

    /** 闸门是否激活（view 是否还活着）。 */
    private boolean active = true;
    /** 当前是否已有一个分页请求在途，用于防止重复发起。 */
    private boolean loading = false;
    /** 当前会话号；每次 reset()/invalidate() 都会推进，旧会话的结果一律作废。 */
    private int session = 0;

    /**
     * 发起一次分页请求。
     *
     * @return 当前会话令牌；若闸门已关闭或已有请求在途，则返回 {@link #INVALID_TOKEN}，
     *         调用方此时不应启动后台线程。
     */
    public synchronized int begin() {
        if (!active || loading) {
            return INVALID_TOKEN;
        }
        loading = true;
        return session;
    }

    /**
     * 判断携带 {@code token} 的结果是否仍可回写到存活的 UI。
     *
     * @param token {@link #begin()} 返回的令牌
     * @return 仅当闸门仍激活且令牌仍等于当前会话号时返回 {@code true}
     */
    public synchronized boolean shouldDeliver(int token) {
        return active && token != INVALID_TOKEN && token == session;
    }

    /** 标记当前请求结束（无论结果是被投递还是被丢弃），允许后续再次 {@link #begin()}。 */
    public synchronized void finishLoad() {
        loading = false;
    }

    /** 是否有分页请求正在进行。 */
    public synchronized boolean isLoading() {
        return loading;
    }

    /** 闸门是否仍然激活。 */
    public synchronized boolean isActive() {
        return active;
    }

    /**
     * 重新激活闸门并开启新会话，用于 view 被（重新）创建时。会推进会话号，使上一轮
     * view 残留的在途结果失效。
     *
     * @return 新的会话号
     */
    public synchronized int reset() {
        active = true;
        loading = false;
        session++;
        return session;
    }

    /**
     * 永久关闭当前闸门并取消在途请求，用于 view 被销毁 / Fragment detach 时。推进会话
     * 号使所有在途结果立即失效。重新激活需调用 {@link #reset()}。
     */
    public synchronized void invalidate() {
        active = false;
        loading = false;
        session++;
    }
}
