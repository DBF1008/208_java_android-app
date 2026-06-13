/*
 * Copyright (C) 2010 ZXing authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.zxing.decoding;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

import android.app.Activity;

/**
 * Finishes an activity after a period of inactivity.
 */
public final class InactivityTimer {

  private static final int INACTIVITY_DELAY_SECONDS = 5 * 60;

  private final ScheduledExecutorService inactivityTimer =
      Executors.newSingleThreadScheduledExecutor(new DaemonThreadFactory());
  private final Activity activity;
  private final int delaySeconds;
  private ScheduledFuture<?> inactivityFuture = null;

  public InactivityTimer(Activity activity) {
    this(activity, INACTIVITY_DELAY_SECONDS);
  }

  /**
   * Package-private constructor that allows specifying a custom delay,
   * primarily useful for testing.
   *
   * @param activity     The Activity to finish on inactivity.
   * @param delaySeconds Timeout in seconds before the Activity is finished.
   */
  InactivityTimer(Activity activity, int delaySeconds) {
    this.activity = activity;
    this.delaySeconds = delaySeconds;
    onActivity();
  }

  public void onActivity() {
    cancel();
    inactivityFuture = inactivityTimer.schedule(new FinishListener(activity),
                                                delaySeconds,
                                                TimeUnit.SECONDS);
  }

  /**
   * Cancels the pending inactivity timeout without shutting down the
   * underlying executor.  Call this from {@code Activity.onPause()} so
   * the timer does not fire {@code finish()} while the Activity is
   * invisible.  A subsequent call to {@link #onActivity()} (typically
   * from {@code onResume()}) will re-arm the timeout.
   */
  public void pause() {
    cancel();
  }

  private void cancel() {
    if (inactivityFuture != null) {
      inactivityFuture.cancel(true);
      inactivityFuture = null;
    }
  }

  public void shutdown() {
    cancel();
    inactivityTimer.shutdown();
  }

  private static final class DaemonThreadFactory implements ThreadFactory {
    public Thread newThread(Runnable runnable) {
      Thread thread = new Thread(runnable);
      thread.setDaemon(true);
      return thread;
    }
  }

}
