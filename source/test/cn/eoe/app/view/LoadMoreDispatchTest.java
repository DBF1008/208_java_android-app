package cn.eoe.app.view;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.Test;

/**
 * 分页结果分发的回归测试 —— 复现并锁定本次缺陷的修复行为。
 *
 * <p>缺陷：列表页 {@code onLoadMore()} 直接起后台线程取下一页，结果回来后无条件回写
 * 到 Adapter，既不取消、也不检查 Fragment 是否还存活、当前是否还是同一分类实例。
 * 用户快速切 tab、返回上级页面、旋转屏幕后，晚到的线程仍会把旧分页塞进新页面甚至
 * 触碰已销毁的 view。
 *
 * <p>本测试用 {@link FakePage} 忠实复刻 {@link BaseListFragment} 的分发链路（基于
 * 真实的 {@link LoadMoreController}），逐一断言各生命周期场景下的投递结果。各方法
 * 与产线代码的对应关系见 {@link FakePage} 的注释。
 */
public class LoadMoreDispatchTest {

    /**
     * 不依赖 Android 的列表页替身，原样复刻 {@link BaseListFragment} 中与生命周期相关
     * 的"加载更多"分发决策：
     * <ul>
     *   <li>{@link #onCreateView()} ↔ {@code BaseListFragment.onCreateView}（reset 会话）</li>
     *   <li>{@link #beginLoad()} ↔ {@code BaseListFragment.onLoadMore}（begin 部分）</li>
     *   <li>{@link #backgroundShouldPost()} ↔ {@code LoadMoreTask.run}（回投前的存活检查）</li>
     *   <li>{@link #deliverMorePage(int, String)} ↔ {@code BaseListFragment.deliverMorePage}</li>
     *   <li>{@link #onDestroyView()} ↔ {@code BaseListFragment.onDestroyView}（invalidate）</li>
     * </ul>
     */
    static final class FakePage {
        final LoadMoreController controller = new LoadMoreController();
        /** 相当于 Adapter 里被追加的数据。 */
        final List<String> applied = new ArrayList<String>();
        /** 相当于 onLoad() 复位底部 footer 的次数。 */
        int footerResets = 0;

        FakePage() {
            onCreateView();
        }

        void onCreateView() {
            controller.reset();
        }

        int beginLoad() {
            return controller.begin();
        }

        /** 后台线程拿到结果后，是否还值得回投到主线程。 */
        boolean backgroundShouldPost() {
            if (!controller.isActive()) {
                controller.finishLoad();
                return false;
            }
            return true;
        }

        /** 主线程：最终分发。page 为 null 表示请求失败。 */
        void deliverMorePage(int token, String page) {
            controller.finishLoad();
            if (controller.shouldDeliver(token)) {
                if (page != null) {
                    applied.add(page);
                }
                footerResets++;
            }
        }

        void onDestroyView() {
            controller.invalidate();
        }

        /** 完整跑一遍 begin -> 后台 -> 投递 的正常链路。 */
        void loadAndDeliver(String page) {
            int token = beginLoad();
            assertTrue("应允许发起加载", token != LoadMoreController.INVALID_TOKEN);
            assertTrue("存活状态后台应回投", backgroundShouldPost());
            deliverMorePage(token, page);
        }
    }

    @Test
    public void aliveFragment_appendsPageAndResetsFooter() {
        FakePage page = new FakePage();
        page.loadAndDeliver("page-1");

        assertEquals(1, page.applied.size());
        assertEquals("page-1", page.applied.get(0));
        assertEquals(1, page.footerResets);
    }

    /** 场景一：快速切 tab —— 结果回来前 view 已销毁，后台不应回投，结果被丢弃。 */
    @Test
    public void tabSwitchedAwayBeforeResult_resultDropped() {
        FakePage page = new FakePage();
        int token = page.beginLoad();

        page.onDestroyView(); // 用户切到别的 tab，ViewPager 销毁了本页 view

        assertFalse("view 已销毁，后台不应再回投", page.backgroundShouldPost());
        // 即便后台仍尝试投递，也必须被丢弃。
        page.deliverMorePage(token, "stale-page");
        assertTrue("不得把旧分页塞进已销毁的页面", page.applied.isEmpty());
        assertEquals(0, page.footerResets);
    }

    /** 场景二：返回上级页面 —— 同样在结果回来前失效，旧结果必须丢弃。 */
    @Test
    public void navigatedBackBeforeResult_resultDropped() {
        FakePage page = new FakePage();
        int token = page.beginLoad();

        page.onDestroyView(); // 返回上级，Fragment 的 view 被销毁

        page.deliverMorePage(token, "stale-page");
        assertTrue(page.applied.isEmpty());
    }

    /** 场景三：旋转屏幕 —— 旧 view 销毁、新 view 重建；旧结果作废，新结果正常投递。 */
    @Test
    public void rotation_oldResultDropped_newResultDelivered() {
        FakePage page = new FakePage();
        int oldToken = page.beginLoad();

        page.onDestroyView();  // 旋转：旧 view 销毁
        page.onCreateView();   // 旋转：新 view 重建（新会话）

        // 旧线程晚到，必须被新会话拒绝。
        page.deliverMorePage(oldToken, "old-page");
        assertTrue("旋转后旧分页不得回写", page.applied.isEmpty());

        // 新 view 上正常加载应当成功。
        page.loadAndDeliver("new-page");
        assertEquals(1, page.applied.size());
        assertEquals("new-page", page.applied.get(0));
    }

    /**
     * 场景四：同一 Fragment 被复用到新的分类实例（view 未销毁，仅重置会话）。
     * 旧分类的在途结果不得回写到新分类。
     */
    @Test
    public void reboundToNewCategory_oldSessionResultDropped() {
        FakePage page = new FakePage();
        int oldToken = page.beginLoad();

        page.onCreateView(); // 重新绑定到新分类 -> 新会话

        page.deliverMorePage(oldToken, "old-category-page");
        assertTrue("切到新分类后，旧分类的结果不得回写", page.applied.isEmpty());

        page.loadAndDeliver("new-category-page");
        assertEquals(1, page.applied.size());
        assertEquals("new-category-page", page.applied.get(0));
    }

    /**
     * 竞态变体：后台已判定可以回投（view 当时还活着），但消息尚未在主线程处理前
     * view 就被销毁了 —— 主线程分发时必须丢弃。
     */
    @Test
    public void destroyedBetweenPostAndDelivery_resultDropped() {
        FakePage page = new FakePage();
        int token = page.beginLoad();
        assertTrue(page.backgroundShouldPost()); // 后台决定回投（此刻仍存活）

        page.onDestroyView(); // 主线程处理消息之前，view 被销毁

        page.deliverMorePage(token, "stale-page");
        assertTrue(page.applied.isEmpty());
        assertEquals(0, page.footerResets);
    }

    /** 请求失败（page == null）：不追加数据，但仍复位 footer，避免底部一直转。 */
    @Test
    public void failedRequest_resetsFooterWithoutAppending() {
        FakePage page = new FakePage();
        int token = page.beginLoad();
        assertTrue(page.backgroundShouldPost());

        page.deliverMorePage(token, null);

        assertTrue(page.applied.isEmpty());
        assertEquals("失败也要复位 footer", 1, page.footerResets);
    }

    /** 非回归：正常连续翻页应按顺序追加。 */
    @Test
    public void normalPagination_appendsPagesInOrder() {
        FakePage page = new FakePage();
        page.loadAndDeliver("page-1");
        page.loadAndDeliver("page-2");
        page.loadAndDeliver("page-3");

        assertEquals(3, page.applied.size());
        assertEquals("page-1", page.applied.get(0));
        assertEquals("page-2", page.applied.get(1));
        assertEquals("page-3", page.applied.get(2));
    }

    /** 非回归：一次加载在途时，重复触发应被忽略（防重入）。 */
    @Test
    public void reentrantLoadWhileInFlight_isIgnored() {
        FakePage page = new FakePage();
        int first = page.beginLoad();
        assertTrue(first != LoadMoreController.INVALID_TOKEN);

        // 第一次还没投递，又触发一次（例如用户连续上拉）。
        assertEquals(LoadMoreController.INVALID_TOKEN, page.beginLoad());

        // 第一次正常完成后，才能再次加载。
        page.deliverMorePage(first, "page-1");
        assertEquals(1, page.applied.size());
        page.loadAndDeliver("page-2");
        assertEquals(2, page.applied.size());
    }
}
