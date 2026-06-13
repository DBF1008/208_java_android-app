package com.google.zxing;

/**
 * Framework-independent model of the scan page's disposable resources and its
 * teardown contract. The logic is extracted from {@link CaptureActivity} and
 * {@link com.google.zxing.decoding.CaptureActivityHandler} so it can be
 * regression-tested with plain JUnit, without the Android runtime.
 *
 * <p>Two regressions are guarded here:
 *
 * <ol>
 * <li>The beep {@link android.media.MediaPlayer} used to be created in
 * {@code onResume()} and never released, leaking one native audio resource
 * every time the scan page was opened and closed. {@link #attachBeep} /
 * {@link #releaseBeep} model a strict acquire/release pair so the player is
 * always released on pause <em>and</em> destroy, and
 * {@link #liveResourceCount()} returns to {@code 0} across repeated
 * lifecycles.</li>
 *
 * <li>{@code quitSynchronously()} used to remove only the
 * {@code decode_succeeded}/{@code decode_failed} messages, leaving queued
 * {@code auto_focus} / {@code restart_preview} messages behind. Those could
 * fire after teardown and keep the Activity alive or drive the already-released
 * camera on the next scan. {@link #pendingScanMessageIds} is the single source
 * of truth for the full set the handler must purge.</li>
 * </ol>
 *
 * <p>This class is intentionally free of Android dependencies. It is not
 * thread-safe; every call is expected to happen on the UI thread, mirroring the
 * Activity lifecycle callbacks that drive it.
 */
public final class ScanLifecycleResources {

    /** A resource that can be released exactly once (e.g. a MediaPlayer). */
    public interface Disposable {
        void dispose();
    }

    private Disposable beep;
    private int liveResourceCount;

    /**
     * Records the beep resource built in {@code onResume()}.
     *
     * <p>Idempotent with respect to leaks: if a beep is already held (for
     * example {@code onResume()} ran twice without an intervening
     * {@code onPause()}), the previous resource is released first so we never
     * accumulate. Passing {@code null} simply clears any currently held
     * resource.
     */
    public void attachBeep(Disposable beep) {
        releaseBeep();
        this.beep = beep;
        if (beep != null) {
            liveResourceCount++;
        }
    }

    /**
     * Releases the beep resource if one is held. Safe to call repeatedly, which
     * is what lets both {@code onPause()} and {@code onDestroy()} call it
     * unconditionally.
     */
    public void releaseBeep() {
        if (beep != null) {
            Disposable toRelease = beep;
            // Clear our reference first so a re-entrant call is a no-op.
            beep = null;
            liveResourceCount--;
            toRelease.dispose();
        }
    }

    /** @return {@code true} while a beep resource is currently held. */
    public boolean isBeepAttached() {
        return beep != null;
    }

    /**
     * Number of beep resources that have been acquired but not yet released.
     * This must be {@code 0} whenever the scan page is paused or destroyed; a
     * non-zero value means an audio resource has leaked.
     */
    public int liveResourceCount() {
        return liveResourceCount;
    }

    /**
     * The full set of handler message ids that must be removed from the capture
     * handler's queue during {@code quitSynchronously()} so that no autofocus,
     * preview-restart or decode messages survive the scan page being torn down.
     *
     * <p>The autofocus loop posts <em>delayed</em> {@code auto_focus} messages
     * and the decode loop posts {@code restart_preview}; both must be purged in
     * addition to the decode results, otherwise a stale message can fire after
     * teardown.
     *
     * @return a fresh array; callers loop over it invoking
     *         {@code removeMessages(id)} for each entry.
     */
    public static int[] pendingScanMessageIds(int autoFocus,
                                              int restartPreview,
                                              int decodeSucceeded,
                                              int decodeFailed) {
        return new int[] { autoFocus, restartPreview, decodeSucceeded, decodeFailed };
    }
}
