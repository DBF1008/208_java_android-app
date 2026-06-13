package cn.eoe.app.utils;

import static org.junit.Assert.*;
import org.junit.Before;
import org.junit.Test;

/**
 * Regression tests for {@link SearchRequestTracker}.
 *
 * <p>These tests document the exact contract that prevents the
 * "old search results overwrite new results" race condition in
 * {@code SearchActivity}.
 */
public class SearchRequestTrackerTest {

    private SearchRequestTracker tracker;

    @Before
    public void setUp() {
        tracker = new SearchRequestTracker();
    }

    // ---- basic contract --------------------------------------------------

    @Test
    public void initialVersionIsZero() {
        assertEquals("Before any search, version should be 0", 0, tracker.getCurrentVersion());
    }

    @Test
    public void newSearchReturnsIncrementingVersions() {
        int v1 = tracker.newSearch();
        int v2 = tracker.newSearch();
        int v3 = tracker.newSearch();

        assertTrue("Each new version must be strictly greater than the previous",
                v2 > v1 && v3 > v2);
    }

    @Test
    public void latestSearchIsCurrent() {
        int v = tracker.newSearch();
        assertTrue("The just-issued search must be current", tracker.isCurrent(v));
    }

    // ---- the core race-condition scenario --------------------------------

    @Test
    public void olderSearchIsNotCurrentAfterNewerSearch() {
        int vA = tracker.newSearch();  // user searches keyword A
        assertTrue(tracker.isCurrent(vA));

        int vB = tracker.newSearch();  // user immediately searches keyword B
        assertTrue("B (the latest) must be current", tracker.isCurrent(vB));
        assertFalse("A (the older) must NOT be current — this is the bug fix",
                tracker.isCurrent(vA));
    }

    @Test
    public void rapidFireSearchesOnlyLatestIsCurrent() {
        // Simulate the user pressing Enter 10 times in rapid succession.
        int[] versions = new int[10];
        for (int i = 0; i < versions.length; i++) {
            versions[i] = tracker.newSearch();
        }

        for (int i = 0; i < versions.length - 1; i++) {
            assertFalse("Search #" + i + " should be stale",
                    tracker.isCurrent(versions[i]));
        }
        assertTrue("Only the very last search should be current",
                tracker.isCurrent(versions[versions.length - 1]));
    }

    // ---- edge cases ------------------------------------------------------

    @Test
    public void staleVersionNeverBecomesCurrentAgain() {
        int vA = tracker.newSearch();
        int vB = tracker.newSearch();
        int vC = tracker.newSearch();

        // Even after multiple newer searches, vA stays stale.
        assertFalse(tracker.isCurrent(vA));
        assertFalse(tracker.isCurrent(vB));
        assertTrue(tracker.isCurrent(vC));
    }

    @Test
    public void isCurrentWithZeroVersionIsFalseAfterAnySearch() {
        tracker.newSearch();
        assertFalse("Version 0 (no search) should never be current once a search exists",
                tracker.isCurrent(0));
    }

    @Test
    public void isCurrentWithArbitraryNumberIsFalse() {
        tracker.newSearch();
        assertFalse("An arbitrary number not issued by the tracker must not be current",
                tracker.isCurrent(999));
    }

    @Test
    public void consecutiveTrackersAreIndependent() {
        SearchRequestTracker t1 = new SearchRequestTracker();
        SearchRequestTracker t2 = new SearchRequestTracker();

        int v1a = t1.newSearch();  // t1 -> version 1
        int v1b = t1.newSearch();  // t1 -> version 2

        // Advancing t1 must not affect t2 — t2 is still at version 0.
        assertEquals(0, t2.getCurrentVersion());

        // t1's version-1 search should now be stale in t1.
        assertFalse(t1.isCurrent(v1a));
        assertTrue(t1.isCurrent(v1b));

        // t2 never issued any search, so nothing is current there yet.
        assertTrue(t2.isCurrent(t2.newSearch()));  // t2 -> version 1
    }
}
