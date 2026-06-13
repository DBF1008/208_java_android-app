package cn.eoe.app.utils;

/**
 * Tracks search request versions so that only the most recent search's
 * results are applied to the UI.
 *
 * <p>Usage pattern:
 * <pre>
 *   int version = tracker.newSearch();
 *   // ... kick off async work ...
 *   // later, in the callback:
 *   if (!tracker.isCurrent(version)) {
 *       return; // a newer search has been issued; discard stale results
 *   }
 * </pre>
 *
 * <p>This class is intentionally free of Android dependencies so that the
 * versioning logic can be regression-tested with plain JUnit.
 *
 * <p><b>Thread-safety:</b> not thread-safe — all calls must happen on the
 * same thread (the UI thread in the typical Android usage).
 */
public class SearchRequestTracker {

    /** Monotonically increasing version counter. 0 means "no search issued yet". */
    private int currentVersion = 0;

    /**
     * Registers a new search request and returns its version id.
     * The returned id is guaranteed to be strictly greater than any
     * previously returned id within this tracker instance.
     */
    public int newSearch() {
        return ++currentVersion;
    }

    /**
     * Returns {@code true} if {@code version} is still the most recent
     * search — i.e. no newer search has been issued since this one.
     */
    public boolean isCurrent(int version) {
        return version == currentVersion;
    }

    /**
     * Returns the current (latest) version number. Useful for tests and
     * diagnostics.
     */
    public int getCurrentVersion() {
        return currentVersion;
    }
}
