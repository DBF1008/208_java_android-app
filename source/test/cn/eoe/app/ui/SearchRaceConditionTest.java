package cn.eoe.app.ui;

import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.Before;
import org.junit.Test;

import cn.eoe.app.utils.SearchRequestTracker;

/**
 * Integration-style regression test that reproduces the original race
 * condition ("search A is slow, search B is fast — A's result overwrites
 * B's UI") and verifies that the version-tracking fix prevents it.
 *
 * <p>The test uses two threads to simulate two concurrent network
 * requests with controllable ordering. No Android framework is required.
 */
public class SearchRaceConditionTest {

    /**
     * Minimal simulation of the "apply search result" side-effect.
     * In the real app this would update the ViewPager; here we just
     * record which keyword ended up on "screen".
     */
    private volatile String displayedKeyword;
    private SearchRequestTracker tracker;

    @Before
    public void setUp() {
        tracker = new SearchRequestTracker();
        displayedKeyword = null;
    }

    /**
     * Simulates what {@code MyTask.onPostExecute} does after the
     * staleness guard was added: only apply the result when the
     * version is still current.
     */
    private void applyResult(int version, String keyword) {
        if (tracker.isCurrent(version)) {
            displayedKeyword = keyword;
        }
        // else: silently discard — this is the fix
    }

    // ---- regression: the exact bug scenario ------------------------------

    @Test
    public void slowFirstSearchDoesNotOverwriteFastSecondSearch() throws Exception {
        // User types keyword A and presses Enter.
        final int versionA = tracker.newSearch();

        // User immediately types keyword B and presses Enter (before A returns).
        final int versionB = tracker.newSearch();

        // B's network request is fast — it returns first.
        applyResult(versionB, "B");
        assertEquals("B", displayedKeyword);

        // A's network request is slow — it returns AFTER B.
        // Without the fix, this would overwrite B with A.
        applyResult(versionA, "A");

        assertEquals(
            "Displayed keyword must remain 'B' — A's stale result must be discarded",
            "B", displayedKeyword);
    }

    @Test
    public void outOfOrderDeliveryStillShowsLatestResult() throws Exception {
        // Issue 5 searches rapidly.
        int[] versions = new int[5];
        for (int i = 0; i < 5; i++) {
            versions[i] = tracker.newSearch();
        }

        // Deliver results in reverse order (slowest first search arrives last).
        for (int i = 4; i >= 0; i--) {
            applyResult(versions[i], "keyword-" + i);
        }

        // Only the very first delivery (keyword-4, the latest version) should stick.
        assertEquals("keyword-4", displayedKeyword);
    }

    @Test
    public void latestResultAlwaysWinsRegardlessOfDeliveryOrder() throws Exception {
        int vA = tracker.newSearch();
        int vB = tracker.newSearch();
        int vC = tracker.newSearch();

        // Deliver C first (latest), then A, then B — all out of order.
        applyResult(vC, "C");
        applyResult(vA, "A");
        applyResult(vB, "B");

        assertEquals("Only C (the latest issued search) should be displayed",
                "C", displayedKeyword);
    }

    // ---- concurrent stress test ------------------------------------------

    @Test
    public void concurrentSearchesOnlyLatestSurvives() throws Exception {
        final int N = 50;
        final int[] versions = new int[N];
        final CountDownLatch startGate = new CountDownLatch(1);
        final CountDownLatch doneLatch = new CountDownLatch(N);
        final List<Thread> threads = new ArrayList<Thread>();

        // Issue N searches sequentially (versions are assigned on the main thread).
        for (int i = 0; i < N; i++) {
            versions[i] = tracker.newSearch();
        }

        // Deliver all results concurrently in arbitrary order.
        for (int i = 0; i < N; i++) {
            final int idx = i;
            Thread t = new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        startGate.await();  // wait for all threads to be ready
                        applyResult(versions[idx], "kw-" + idx);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } finally {
                        doneLatch.countDown();
                    }
                }
            });
            threads.add(t);
            t.start();
        }

        // Release all threads at once.
        startGate.countDown();
        assertTrue("All threads should finish within 5 seconds",
                doneLatch.await(5, TimeUnit.SECONDS));

        // No matter which thread won the race, the displayed keyword
        // MUST be the last issued version (N-1).
        assertEquals("kw-" + (N - 1), displayedKeyword);
    }
}
