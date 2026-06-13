# Tests

This legacy project is an Eclipse/Ant Android project with no Gradle and no test
runner wired up. These tests therefore live **outside** `source/src` (so the APK
build never compiles them) and are run manually with a vendored JUnit.

## What is covered

The fix for "分页线程在 fragment 失效后仍回写旧页面" centers on
[`LoadMoreController`](../src/cn/eoe/app/view/LoadMoreController.java), a small
framework-independent lifecycle gate that decides whether a page fetched on a
background thread may still be written back to a live UI.

- **`LoadMoreControllerTest`** — unit tests of the gate: normal paging,
  re-entrancy guard, and invalidation on every lifecycle transition (tab switch /
  back / rotation / rebind to a new category), plus concurrency (mutual exclusion
  of `begin()`, and rejection of stale tokens after `invalidate()`).
- **`LoadMoreDispatchTest`** — a regression test that faithfully reproduces
  `BaseListFragment`'s dispatch pipeline and asserts lifecycle-correct delivery
  for each scenario from the bug report (tab switch, back, rotation, new-category
  rebind, the post-post/pre-deliver race, failed request, and non-regression of
  normal paging).

## Run

From the `source/` directory:

```sh
CP="test/libs/junit-4.13.2.jar:test/libs/hamcrest-core-1.3.jar"
mkdir -p /tmp/lmtest-build
javac -cp "$CP" -d /tmp/lmtest-build \
  src/cn/eoe/app/view/LoadMoreController.java \
  test/cn/eoe/app/view/LoadMoreControllerTest.java \
  test/cn/eoe/app/view/LoadMoreDispatchTest.java
java -cp "/tmp/lmtest-build:$CP" org.junit.runner.JUnitCore \
  cn.eoe.app.view.LoadMoreControllerTest \
  cn.eoe.app.view.LoadMoreDispatchTest
```

Expected: `OK (21 tests)`.

`LoadMoreController` has no Android dependencies, so it compiles with a plain JDK;
the tests only need JUnit + Hamcrest (vendored under `test/libs/`).
