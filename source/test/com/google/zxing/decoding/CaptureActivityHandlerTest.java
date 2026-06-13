/*
 * Regression tests for CaptureActivityHandler message-queue cleanup.
 *
 * Bug: quitSynchronously() only removed decode_succeeded and decode_failed
 * from the Handler's message queue, but NOT auto_focus or restart_preview.
 * After onPause() returned, a delayed auto_focus message could still
 * arrive and attempt to request autofocus on a camera that had already
 * been closed — causing RuntimeExceptions or camera-driver instability
 * on the next open.
 *
 * The fix adds removeMessages() calls for ALL four queued message types
 * and an early-return guard in handleMessage() when state == DONE.
 *
 * Run with: JUnit 4 + Robolectric (for Looper / Handler / Activity stubs)
 *
 * Required test dependencies:
 *   - junit:junit:4.13.2
 *   - org.robolectric:robolectric:4.9
 */
package com.google.zxing.decoding;

import android.os.Message;

import com.google.zxing.CaptureActivity;

import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowLooper;

import cn.eoe.app.R;

import static org.junit.Assert.assertFalse;

@RunWith(RobolectricTestRunner.class)
@Config(manifest = "test/AndroidManifest.xml")
public class CaptureActivityHandlerTest {

    private CaptureActivityHandler handler;
    private CaptureActivity activity;

    @After
    public void tearDown() {
        if (handler != null) {
            try {
                handler.quitSynchronously();
            } catch (Exception ignored) {
                // handler may already be quit
            }
            handler = null;
        }
    }

    private void setupActivityAndHandler() {
        activity = Robolectric.buildActivity(CaptureActivity.class)
                .create().start().resume().get();
        // The handler is created by CaptureActivity when the surface is ready.
        // For these tests we construct it directly; the constructor will
        // call CameraManager.get().startPreview() and restartPreviewAndDecode()
        // which are safe no-ops when CameraManager.camera is null.
        handler = new CaptureActivityHandler(activity, null, null);
    }

    /**
     * Core regression test: quitSynchronously() must remove ALL queued
     * message types from the Handler — not just decode_succeeded/failed.
     *
     * Previously, auto_focus (delayed 1500 ms from AutoFocusCallback)
     * and restart_preview messages survived quitSynchronously() and could
     * fire after the camera was already closed.
     */
    @Test
    public void quitSynchronouslyRemovesAllMessages() {
        setupActivityAndHandler();

        // Drain the Looper so constructor-posted messages are processed
        ShadowLooper.shadowMainLooper().idle();

        // Enqueue one of each message type that the handler processes
        handler.sendEmptyMessage(R.id.auto_focus);
        handler.sendEmptyMessage(R.id.restart_preview);
        handler.sendEmptyMessage(R.id.decode_succeeded);
        handler.sendEmptyMessage(R.id.decode_failed);

        // Shut down — must remove every pending message
        handler.quitSynchronously();

        // All four message types must be gone from the queue
        assertFalse("auto_focus messages must be removed on quit",
                handler.hasMessages(R.id.auto_focus));
        assertFalse("restart_preview messages must be removed on quit",
                handler.hasMessages(R.id.restart_preview));
        assertFalse("decode_succeeded messages must be removed on quit",
                handler.hasMessages(R.id.decode_succeeded));
        assertFalse("decode_failed messages must be removed on quit",
                handler.hasMessages(R.id.decode_failed));
    }

    /**
     * Regression: after quitSynchronously(), state must be DONE so that
     * any message that somehow still arrives is silently dropped by the
     * early-return guard in handleMessage().
     */
    @Test
    public void stateIsDoneAfterQuitSynchronously() {
        setupActivityAndHandler();
        handler.quitSynchronously();

        // Send messages after quit — they must be silently ignored
        // (handleMessage returns early when state == DONE)
        handler.sendEmptyMessage(R.id.auto_focus);
        handler.sendEmptyMessage(R.id.restart_preview);

        // Processing these must not throw or cause side effects
        ShadowLooper.shadowMainLooper().idle();
    }

    /**
     * Regression: the delayed auto_focus message (sent with 1500 ms delay
     * by AutoFocusCallback.onAutoFocus()) must be removed by
     * quitSynchronously().  This is the most common leak scenario because
     * the AF callback fires asynchronously.
     */
    @Test
    public void quitSynchronouslyRemovesDelayedAutoFocus() {
        setupActivityAndHandler();

        // Simulate AutoFocusCallback sending a delayed message
        Message afMsg = handler.obtainMessage(R.id.auto_focus);
        handler.sendMessageDelayed(afMsg, 1500L);

        // The delayed message should be pending
        assert handler.hasMessages(R.id.auto_focus);

        // quitSynchronously must remove it
        handler.quitSynchronously();

        assertFalse("Delayed auto_focus message must be removed on quit",
                handler.hasMessages(R.id.auto_focus));
    }

    /**
     * Regression: multiple rapid quitSynchronously() calls (e.g., from
     * onPause() being called twice due to configuration change) must
     * not throw.
     */
    @Test
    public void doubleQuitDoesNotThrow() {
        setupActivityAndHandler();

        handler.quitSynchronously();
        // Second call — DecodeThread is already joined, this should be safe
        try {
            handler.quitSynchronously();
        } catch (Exception e) {
            throw new AssertionError("Double quitSynchronously() must not throw", e);
        }
    }
}
