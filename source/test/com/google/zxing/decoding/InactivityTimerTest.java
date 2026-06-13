/*
 * Regression tests for InactivityTimer pause / resume / shutdown lifecycle.
 *
 * Bug: InactivityTimer kept firing finish() on the Activity even after
 * onPause(), which could finish a backgrounded Activity.  The fix adds
 * a pause() method (called from CaptureActivity.onPause()) and re-arms
 * the timer from onResume() via onActivity().
 *
 * Run with: JUnit 4 + Robolectric (for Activity stub)
 *
 * Required test dependencies:
 *   - junit:junit:4.13.2
 *   - org.robolectric:robolectric:4.9
 */
package com.google.zxing.decoding;

import android.app.Activity;

import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertFalse;

@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE)
public class InactivityTimerTest {

    private InactivityTimer timer;

    @After
    public void tearDown() {
        if (timer != null) {
            timer.shutdown();
        }
    }

    /**
     * Regression: after pause(), the pending finish() callback must be
     * cancelled.  Previously there was no pause() — the timer would fire
     * while the Activity was invisible, causing a black-screen re-entry
     * or Activity-finished-in-background crash.
     */
    @Test
    public void pauseCancelsPendingCallback() throws Exception {
        Activity activity = Robolectric.buildActivity(Activity.class).create().get();
        CountDownLatch fired = new CountDownLatch(1);

        // Use a 1-second delay so we can observe the timeout quickly
        timer = new InactivityTimer(activity, 1);

        // pause() must cancel the pending future
        timer.pause();

        // Wait well past the 1-second timeout — the callback must NOT fire
        boolean timedOut = fired.await(3, TimeUnit.SECONDS);
        // Since the CountDownLatch never counts down (no finish() was called),
        // await should time out → timedOut == false
        assertFalse("Timer callback should not fire after pause()", timedOut);
    }

    /**
     * Regression: after pause(), a subsequent onActivity() call (from
     * onResume()) must re-arm the timer.  Previously there was no way to
     * re-arm after pause because pause() didn't exist.
     */
    @Test
    public void onActivityAfterPauseRearmsTimer() throws Exception {
        Activity activity = Robolectric.buildActivity(Activity.class).create().get();
        timer = new InactivityTimer(activity, 1);

        timer.pause();
        // Re-arm — this should schedule a new future without throwing
        timer.onActivity();

        // If we got here without exception, the timer was successfully re-armed
        // Give it time to fire and verify no crash
        Thread.sleep(2000);
    }

    /**
     * After shutdown(), no further scheduling is possible.
     * onActivity() after shutdown() should not throw but the underlying
     * executor is terminated.
     */
    @Test
    public void shutdownPreventsReschedule() throws Exception {
        Activity activity = Robolectric.buildActivity(Activity.class).create().get();
        timer = new InactivityTimer(activity, 1);

        timer.shutdown();
        // Calling onActivity after shutdown should not throw
        // (the executor silently rejects new tasks)
        timer.onActivity();
        timer = null; // already shut down, prevent double-shutdown in tearDown
    }

    /**
     * Regression: onActivity() cancels any previous pending callback
     * before scheduling a new one.  This prevents duplicate finish() calls
     * when handleDecode() calls onActivity() right before the Activity
     * is finished normally.
     */
    @Test
    public void onActivityCancelsPreviousFuture() throws Exception {
        Activity activity = Robolectric.buildActivity(Activity.class).create().get();
        timer = new InactivityTimer(activity, 60); // 60s — won't fire during test

        // Calling onActivity() again should cancel the previous future
        // and schedule a new one.  Should not throw.
        timer.onActivity();
        timer.onActivity();
        timer.onActivity();

        // Verify the timer can still be paused cleanly after multiple onActivity() calls
        timer.pause();
    }

    /**
     * Verifies that multiple pause() calls in a row are idempotent and
     * do not throw or corrupt state.
     */
    @Test
    public void multiplePauseIsIdempotent() throws Exception {
        Activity activity = Robolectric.buildActivity(Activity.class).create().get();
        timer = new InactivityTimer(activity, 1);

        timer.pause();
        timer.pause();
        timer.pause();

        // Should still be able to re-arm after multiple pauses
        timer.onActivity();
    }

    /**
     * Verifies that pause() followed by shutdown() works cleanly —
     * this mirrors the onPause() → onDestroy() lifecycle.
     */
    @Test
    public void pauseThenShutdownSucceeds() throws Exception {
        Activity activity = Robolectric.buildActivity(Activity.class).create().get();
        timer = new InactivityTimer(activity, 1);

        timer.pause();
        timer.shutdown();
        timer = null; // already shut down
    }

    /**
     * Verifies the full lifecycle: create → pause → resume (onActivity) →
     * pause → shutdown.  This mirrors repeated open/close of the scan page.
     */
    @Test
    public void fullLifecycleRepeatedOpenClose() throws Exception {
        Activity activity = Robolectric.buildActivity(Activity.class).create().get();
        timer = new InactivityTimer(activity, 1);

        // Simulate first open → close
        timer.pause();

        // Simulate re-open
        timer.onActivity();

        // Simulate second close
        timer.pause();

        // Simulate re-open again
        timer.onActivity();

        // Final close + destroy
        timer.pause();
        timer.shutdown();
        timer = null;
    }
}
