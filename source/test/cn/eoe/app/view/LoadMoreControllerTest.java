package cn.eoe.app.view;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.Test;

/**
 * {@link LoadMoreController} 的单元测试。
 *
 * <p>该闸门是"分页结果与 Fragment 生命周期一致"修复的核心：它决定一个在后台线程
 * 取得的分页结果是否还能回写到存活的 UI。这里覆盖正常分页、各类生命周期失效
 * （切 tab / 返回 / 旋转 / 切到新分类实例）以及并发安全。
 */
public class LoadMoreControllerTest {

    @Test
    public void freshController_isActiveAndIdle() {
        LoadMoreController c = new LoadMoreController();
        assertTrue(c.isActive());
        assertFalse(c.isLoading());
    }

    @Test
    public void begin_returnsValidToken_andMarksLoading() {
        LoadMoreController c = new LoadMoreController();
        int token = c.begin();
        assertNotEquals(LoadMoreController.INVALID_TOKEN, token);
        assertTrue(c.isLoading());
        assertTrue("alive 且令牌为当前会话，应可投递", c.shouldDeliver(token));
    }

    @Test
    public void secondBeginWhileLoading_isRejected() {
        LoadMoreController c = new LoadMoreController();
        int first = c.begin();
        assertNotEquals(LoadMoreController.INVALID_TOKEN, first);
        // 第一次请求尚未结束，重复触发应被拒绝（对应 XListView 的"加载中"防重入）。
        assertEquals(LoadMoreController.INVALID_TOKEN, c.begin());
    }

    @Test
    public void finishLoad_allowsAnotherBeginInSameSession() {
        LoadMoreController c = new LoadMoreController();
        int first = c.begin();
        c.finishLoad();
        assertFalse(c.isLoading());
        int second = c.begin();
        assertNotEquals(LoadMoreController.INVALID_TOKEN, second);
        // 同一会话内，先后两次请求的令牌相同，且都可投递。
        assertEquals(first, second);
        assertTrue(c.shouldDeliver(second));
    }

    /** 用户在结果回来前切走 tab / 返回：view 被销毁，闸门 invalidate，旧结果必须作废。 */
    @Test
    public void invalidate_makesInFlightResultStale() {
        LoadMoreController c = new LoadMoreController();
        int token = c.begin();
        assertTrue(c.shouldDeliver(token));

        c.invalidate(); // onDestroyView

        assertFalse("失效后旧令牌不得投递", c.shouldDeliver(token));
        assertFalse(c.isActive());
        assertFalse(c.isLoading());
    }

    @Test
    public void invalidate_blocksFurtherBegin() {
        LoadMoreController c = new LoadMoreController();
        c.invalidate();
        // view 已销毁，不应再发起任何分页请求。
        assertEquals(LoadMoreController.INVALID_TOKEN, c.begin());
    }

    /** 旋转屏幕 / 切到新分类实例：view 重建，reset 开启新会话，上一轮残留结果作废。 */
    @Test
    public void reset_startsNewSession_andRejectsOldToken() {
        LoadMoreController c = new LoadMoreController();
        int oldToken = c.begin();

        int newSession = c.reset(); // onCreateView 重新创建

        assertNotEquals("新会话号必须不同于旧令牌", oldToken, newSession);
        assertFalse("上一轮 view 的结果不得回写到新 view", c.shouldDeliver(oldToken));
        assertTrue(c.isActive());
        assertFalse(c.isLoading());

        int newToken = c.begin();
        assertEquals(newSession, newToken);
        assertTrue(c.shouldDeliver(newToken));
    }

    @Test
    public void reset_reactivatesAfterInvalidate() {
        LoadMoreController c = new LoadMoreController();
        c.invalidate();
        assertFalse(c.isActive());

        c.reset();
        assertTrue(c.isActive());

        int token = c.begin();
        assertNotEquals(LoadMoreController.INVALID_TOKEN, token);
        assertTrue(c.shouldDeliver(token));
    }

    @Test
    public void invalidToken_isNeverDelivered_evenWhenActive() {
        LoadMoreController c = new LoadMoreController();
        assertTrue(c.isActive());
        assertFalse(c.shouldDeliver(LoadMoreController.INVALID_TOKEN));
    }

    @Test
    public void everyLifecycleBumpProducesADistinctSession() {
        LoadMoreController c = new LoadMoreController();
        int t0 = c.begin();
        int s1 = c.reset();
        c.invalidate();
        int s2 = c.reset();
        // 令牌随每次生命周期变化而推进，旧令牌永不复活。
        assertNotEquals(t0, s1);
        assertNotEquals(s1, s2);
        assertNotEquals(t0, s2);
        assertFalse(c.shouldDeliver(t0));
        assertFalse(c.shouldDeliver(s1));
        assertTrue(c.shouldDeliver(s2));
    }

    /**
     * 并发：大量线程同时 begin()，由于 loading 标志受 synchronized 保护，
     * 在没有人 finishLoad 的前提下，只能有恰好一个线程拿到有效令牌。
     */
    @Test
    public void concurrentBegin_exactlyOneSucceeds() throws InterruptedException {
        final LoadMoreController c = new LoadMoreController();
        final int threads = 64;
        final CountDownLatch start = new CountDownLatch(1);
        final CountDownLatch done = new CountDownLatch(threads);
        final AtomicInteger successes = new AtomicInteger(0);

        for (int i = 0; i < threads; i++) {
            new Thread() {
                @Override
                public void run() {
                    try {
                        start.await();
                    } catch (InterruptedException ignored) {
                        Thread.currentThread().interrupt();
                    }
                    if (c.begin() != LoadMoreController.INVALID_TOKEN) {
                        successes.incrementAndGet();
                    }
                    done.countDown();
                }
            }.start();
        }

        start.countDown();
        assertTrue("线程未在限定时间内结束", done.await(5, TimeUnit.SECONDS));
        assertEquals(1, successes.get());
    }

    /**
     * 并发：一个线程 invalidate，同时大量线程反复读取 shouldDeliver。验证线程安全
     * （无异常、状态一致），并断言 invalidate 完成后旧令牌一定被拒绝。
     */
    @Test
    public void concurrentReadsDuringInvalidate_endStateRejectsStaleToken()
            throws InterruptedException {
        final LoadMoreController c = new LoadMoreController();
        final int token = c.begin();
        assertTrue(c.shouldDeliver(token));

        final int readers = 32;
        final CountDownLatch start = new CountDownLatch(1);
        final CountDownLatch done = new CountDownLatch(readers + 1);

        for (int i = 0; i < readers; i++) {
            new Thread() {
                @Override
                public void run() {
                    try {
                        start.await();
                    } catch (InterruptedException ignored) {
                        Thread.currentThread().interrupt();
                    }
                    for (int k = 0; k < 2000; k++) {
                        c.shouldDeliver(token);
                    }
                    done.countDown();
                }
            }.start();
        }
        new Thread() {
            @Override
            public void run() {
                try {
                    start.await();
                } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                }
                c.invalidate();
                done.countDown();
            }
        }.start();

        start.countDown();
        assertTrue("线程未在限定时间内结束", done.await(5, TimeUnit.SECONDS));

        assertFalse(c.shouldDeliver(token));
        assertFalse(c.isActive());
    }
}
