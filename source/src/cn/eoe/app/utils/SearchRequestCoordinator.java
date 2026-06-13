package cn.eoe.app.utils;

/**
 * Coordinates "latest request wins" semantics for a stream of asynchronous
 * requests (e.g. type-then-enter searches).
 *
 * <p>Every time the user triggers a new request, call {@link #next()} to obtain
 * a monotonically increasing token. The asynchronous worker must remember the
 * token it was started with and, when it finishes, only mutate shared UI state
 * if {@link #isLatest(int)} still returns {@code true} for that token. Because
 * {@link #next()} invalidates every previously issued token, a slow response for
 * an older keyword can no longer overwrite the results of a newer one.</p>
 *
 * <p>This class deliberately has no Android dependencies so the ordering logic
 * can be unit tested in isolation. All methods are thread safe: tokens are
 * typically issued on the UI thread but {@link #isLatest(int)} is queried from
 * background worker callbacks, so the small amount of shared state is guarded by
 * the instance monitor.</p>
 */
public final class SearchRequestCoordinator {

    /**
     * Sentinel returned by {@link #latest()} before any request has been issued.
     * {@link #next()} never returns this value, so a real in-flight request can
     * always be distinguished from the "no request yet" state.
     */
    public static final int NO_REQUEST = 0;

    private int latestToken = NO_REQUEST;

    /**
     * Begins a new request, invalidating every token issued before it.
     *
     * @return a unique, strictly increasing token identifying this request.
     */
    public synchronized int next() {
        return ++latestToken;
    }

    /**
     * @return {@code true} iff {@code token} identifies the most recently issued
     *         request, i.e. this result is still allowed to update the UI.
     */
    public synchronized boolean isLatest(int token) {
        return token != NO_REQUEST && token == latestToken;
    }

    /**
     * @return the token of the most recent request, or {@link #NO_REQUEST} if
     *         {@link #next()} has never been called.
     */
    public synchronized int latest() {
        return latestToken;
    }
}
