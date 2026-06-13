package cn.eoe.app.utils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;

import org.junit.Test;

/**
 * Regression tests for the "latest search wins" ordering guarantee used by
 * {@code SearchActivity}.
 *
 * <p>They reproduce the original defect: searching keyword <em>A</em> and then
 * immediately keyword <em>B</em> could leave the slower response for <em>A</em>
 * overwriting the page already rendered for <em>B</em>. {@link
 * SearchRequestCoordinator} stamps every request with a token and only the most
 * recent token is allowed to mutate the UI, so a stale response is detectably
 * not-latest and is dropped.</p>
 */
public class SearchRequestCoordinatorTest {

    @Test
    public void firstRequestIsTheLatest() {
        SearchRequestCoordinator c = new SearchRequestCoordinator();

        int a = c.next();

        assertTrue(c.isLatest(a));
        assertEquals(a, c.latest());
    }

    /**
     * The core regression. Search A, then B. Even when A's network response is
     * the slower one and lands last, A's token must no longer be the latest
     * while B's token still is, so the UI keeps B's results.
     */
    @Test
    public void newerSearchSupersedesOlder_staleResultIsDropped() {
        SearchRequestCoordinator c = new SearchRequestCoordinator();

        int tokenA = c.next(); // user searches "A"
        int tokenB = c.next(); // user immediately searches "B"

        // Now A's slow response arrives AFTER B's. It must be rejected...
        assertFalse("slow stale response for A must be dropped", c.isLatest(tokenA));
        // ...while B, the most recent query, is allowed to update the UI.
        assertTrue("response for the latest query B must be applied", c.isLatest(tokenB));
    }

    @Test
    public void onlyTheLastOfManyRapidSearchesWins() {
        SearchRequestCoordinator c = new SearchRequestCoordinator();

        List<Integer> tokens = new ArrayList<Integer>();
        for (int i = 0; i < 10; i++) {
            tokens.add(c.next());
        }

        int last = tokens.get(tokens.size() - 1);
        for (int i = 0; i < tokens.size() - 1; i++) {
            assertFalse("intermediate request " + tokens.get(i) + " must be stale",
                    c.isLatest(tokens.get(i)));
        }
        assertTrue("only the final request may win", c.isLatest(last));
    }

    @Test
    public void tokensAreStrictlyIncreasingAndUnique() {
        SearchRequestCoordinator c = new SearchRequestCoordinator();

        Set<Integer> seen = new HashSet<Integer>();
        int prev = Integer.MIN_VALUE;
        for (int i = 0; i < 100; i++) {
            int t = c.next();
            assertTrue("tokens must strictly increase", t > prev);
            assertTrue("tokens must be unique", seen.add(t));
            prev = t;
        }
    }

    @Test
    public void beforeAnyRequest_thereIsNoLatest() {
        SearchRequestCoordinator c = new SearchRequestCoordinator();

        assertEquals(SearchRequestCoordinator.NO_REQUEST, c.latest());
        // The sentinel must never be mistaken for an in-flight request.
        assertFalse(c.isLatest(SearchRequestCoordinator.NO_REQUEST));
    }

    /**
     * Tokens are issued on the UI thread but {@code isLatest} is queried from
     * background worker callbacks. After many threads concurrently obtain
     * tokens, every token must be unique and exactly one may be the latest.
     */
    @Test
    public void concurrentRequests_exactlyOneTokenIsLatest() throws InterruptedException {
        final SearchRequestCoordinator c = new SearchRequestCoordinator();
        final int threads = 16;
        final CountDownLatch start = new CountDownLatch(1);
        final CountDownLatch done = new CountDownLatch(threads);
        final List<Integer> issued = Collections.synchronizedList(new ArrayList<Integer>());

        for (int i = 0; i < threads; i++) {
            new Thread(new Runnable() {
                public void run() {
                    try {
                        start.await();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                    issued.add(c.next());
                    done.countDown();
                }
            }).start();
        }
        start.countDown(); // release every thread at once to maximise contention
        done.await();

        assertEquals("every concurrent request must get a unique token",
                threads, new HashSet<Integer>(issued).size());

        int latestCount = 0;
        for (int token : issued) {
            if (c.isLatest(token)) {
                latestCount++;
            }
        }
        assertEquals("exactly one issued token may be the latest", 1, latestCount);
    }
}
