package com.google.zxing;

import static org.junit.Assert.*;

import org.junit.Before;
import org.junit.Test;

/**
 * Regression tests for {@link ScanLifecycleResources}.
 *
 * <p>These tests document the exact contract that prevents the scan page from
 * leaking audio (and, by extension, behaving unreliably on re-entry) when the
 * user repeatedly opens/closes it, scans-and-returns immediately, or presses
 * Home and comes back. Two original defects are pinned down here:
 *
 * <ul>
 * <li><b>Beep leak:</b> the beep {@code MediaPlayer} was created in
 * {@code onResume()} but never released, so one native audio resource leaked
 * per open/close cycle. The fix routes the player through
 * {@link ScanLifecycleResources} and releases it in {@code onPause()} and
 * {@code onDestroy()}.</li>
 * <li><b>Stale handler messages:</b> {@code quitSynchronously()} only purged
 * the decode-result messages, leaving {@code auto_focus} / {@code restart_preview}
 * queued. The fix purges the full {@link #pendingScanMessageIds} set.</li>
 * </ul>
 *
 * <p>The class under test is intentionally Android-free, so these run with plain
 * JUnit (no emulator / Robolectric).
 */
public class ScanLifecycleResourcesTest {

    private ScanLifecycleResources res;

    @Before
    public void setUp() {
        res = new ScanLifecycleResources();
    }

    /** A beep resource that records how many times it was released. */
    private static final class CountingBeep implements ScanLifecycleResources.Disposable {
        int disposeCount;

        @Override
        public void dispose() {
            disposeCount++;
        }
    }

    private static boolean contains(int[] array, int value) {
        for (int v : array) {
            if (v == value) {
                return true;
            }
        }
        return false;
    }

    // ---- basic beep acquire/release contract -----------------------------

    @Test
    public void freshCoordinatorHoldsNoBeep() {
        assertFalse("A new coordinator holds nothing", res.isBeepAttached());
        assertEquals("Nothing acquired yet", 0, res.liveResourceCount());
    }

    @Test
    public void attachThenReleaseFreesTheResource() {
        CountingBeep beep = new CountingBeep();

        res.attachBeep(beep);
        assertTrue("After attach the beep is held", res.isBeepAttached());
        assertEquals("Exactly one resource is live", 1, res.liveResourceCount());
        assertEquals("Attaching must not dispose", 0, beep.disposeCount);

        res.releaseBeep();
        assertFalse("After release nothing is held", res.isBeepAttached());
        assertEquals("No resource is live after release", 0, res.liveResourceCount());
        assertEquals("Release must dispose exactly once", 1, beep.disposeCount);
    }

    @Test
    public void releaseWithoutAttachIsNoOp() {
        // onPause() may run when no beep was ever built (e.g. silent mode).
        res.releaseBeep();
        assertFalse(res.isBeepAttached());
        assertEquals("Count must never go negative", 0, res.liveResourceCount());
    }

    // ---- idempotency that onPause()+onDestroy() rely on -------------------

    @Test
    public void doubleReleaseDisposesOnlyOnce() {
        CountingBeep beep = new CountingBeep();
        res.attachBeep(beep);

        res.releaseBeep(); // onPause
        res.releaseBeep(); // onDestroy

        assertEquals("The player must be released exactly once", 1, beep.disposeCount);
        assertEquals(0, res.liveResourceCount());
        assertFalse(res.isBeepAttached());
    }

    @Test
    public void onDestroyAfterOnPauseIsHarmless() {
        CountingBeep beep = new CountingBeep();
        res.attachBeep(beep);
        res.releaseBeep();                 // onPause already freed it
        assertEquals(0, res.liveResourceCount());

        res.releaseBeep();                 // onDestroy must be a safe no-op
        assertEquals(1, beep.disposeCount);
        assertEquals(0, res.liveResourceCount());
    }

    // ---- defensive: re-acquire without an intervening release ------------

    @Test
    public void attachWhileHoldingReleasesPrevious() {
        // Models onResume() running twice without onPause() in between.
        CountingBeep first = new CountingBeep();
        CountingBeep second = new CountingBeep();

        res.attachBeep(first);
        res.attachBeep(second);

        assertEquals("The superseded player must be disposed", 1, first.disposeCount);
        assertEquals("The new player must still be live", 0, second.disposeCount);
        assertTrue(res.isBeepAttached());
        assertEquals("Still only one live resource — no leak", 1, res.liveResourceCount());
    }

    @Test
    public void attachNullClearsHeldResource() {
        CountingBeep beep = new CountingBeep();
        res.attachBeep(beep);

        res.attachBeep(null);

        assertEquals("Attaching null releases the held resource", 1, beep.disposeCount);
        assertFalse(res.isBeepAttached());
        assertEquals(0, res.liveResourceCount());
    }

    // ---- the core regression: repeated enter/exit must not leak audio ----

    @Test
    public void resourceCountReturnsToZeroAcrossManyLifecycles() {
        final int cycles = 50;
        int totalDisposed = 0;

        for (int i = 0; i < cycles; i++) {
            CountingBeep beep = new CountingBeep(); // a fresh MediaPlayer each onResume()

            res.attachBeep(beep);                   // onResume(): build + register
            assertTrue("Beep held while resumed (cycle " + i + ")", res.isBeepAttached());
            assertEquals("Exactly one live resource while resumed (cycle " + i + ")",
                    1, res.liveResourceCount());

            res.releaseBeep();                      // onPause()
            res.releaseBeep();                      // onDestroy() (idempotent)

            assertFalse("Nothing held after teardown (cycle " + i + ")", res.isBeepAttached());
            assertEquals("No audio resource may survive a lifecycle (cycle " + i + ")",
                    0, res.liveResourceCount());
            assertEquals("Each cycle's player disposed exactly once (cycle " + i + ")",
                    1, beep.disposeCount);

            totalDisposed += beep.disposeCount;
        }

        assertEquals("Every player built across all cycles must be released",
                cycles, totalDisposed);
        assertEquals("After 50 open/close cycles, zero resources remain — no leak",
                0, res.liveResourceCount());
    }

    // ---- the second regression: which messages get purged on quit --------

    @Test
    public void pendingScanMessageIdsIncludesAutofocusAndPreview() {
        // Distinct sentinels stand in for the real R.id.* values.
        final int autoFocus = 11;
        final int restartPreview = 22;
        final int decodeSucceeded = 33;
        final int decodeFailed = 44;

        int[] ids = ScanLifecycleResources.pendingScanMessageIds(
                autoFocus, restartPreview, decodeSucceeded, decodeFailed);

        assertEquals("Exactly four message kinds must be purged on quit", 4, ids.length);
        assertTrue("auto_focus must be purged — this is the bug fix", contains(ids, autoFocus));
        assertTrue("restart_preview must be purged — this is the bug fix",
                contains(ids, restartPreview));
        assertTrue("decode_succeeded must still be purged", contains(ids, decodeSucceeded));
        assertTrue("decode_failed must still be purged", contains(ids, decodeFailed));
    }

    @Test
    public void pendingScanMessageIdsReturnsAFreshArray() {
        int[] a = ScanLifecycleResources.pendingScanMessageIds(1, 2, 3, 4);
        int[] b = ScanLifecycleResources.pendingScanMessageIds(1, 2, 3, 4);

        assertNotSame("Each caller gets its own array to iterate over", a, b);
        assertArrayEquals("Same inputs yield the same set", a, b);
    }
}
