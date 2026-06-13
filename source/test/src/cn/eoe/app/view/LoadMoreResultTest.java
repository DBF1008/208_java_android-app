package cn.eoe.app.view;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import junit.framework.TestCase;

import cn.eoe.app.view.BaseListFragment.LoadMoreResult;

/**
 * Pure JUnit tests for {@link BaseListFragment.LoadMoreResult}.
 * These tests verify the data-carrier object used to shuttle
 * load-more results through the Handler message.
 *
 * Can be run with a standard JUnit runner — no Android framework needed.
 */
public class LoadMoreResultTest extends TestCase {

	public void testConstructorStoresItemsAndUrl() {
		List<String> items = Arrays.asList("a", "b", "c");
		LoadMoreResult<String> result = new LoadMoreResult<String>(items, "http://next");

		assertEquals(3, result.items.size());
		assertEquals("a", result.items.get(0));
		assertEquals("b", result.items.get(1));
		assertEquals("c", result.items.get(2));
		assertEquals("http://next", result.newMoreUrl);
	}

	public void testConstructorWithNullUrl() {
		List<String> items = Collections.singletonList("x");
		LoadMoreResult<String> result = new LoadMoreResult<String>(items, null);

		assertEquals(1, result.items.size());
		assertNull(result.newMoreUrl);
	}

	public void testConstructorWithNullItems() {
		LoadMoreResult<String> result = new LoadMoreResult<String>(null, "http://next");

		assertNull(result.items);
		assertEquals("http://next", result.newMoreUrl);
	}

	public void testConstructorWithBothNull() {
		LoadMoreResult<Object> result = new LoadMoreResult<Object>(null, null);

		assertNull(result.items);
		assertNull(result.newMoreUrl);
	}

	public void testConstructorWithEmptyList() {
		List<Object> items = new ArrayList<Object>();
		LoadMoreResult<Object> result = new LoadMoreResult<Object>(items, "");

		assertNotNull(result.items);
		assertTrue(result.items.isEmpty());
		assertEquals("", result.newMoreUrl);
	}

	public void testItemsListIsNotDefensivelyCopied() {
		// Document current behavior: the list reference is stored directly.
		// If the caller mutates the list after construction, the result
		// reflects the mutation. This is acceptable because the caller
		// (performLoadMore) creates a fresh list from the parsed response
		// and never touches it again.
		List<String> items = new ArrayList<String>();
		items.add("before");
		LoadMoreResult<String> result = new LoadMoreResult<String>(items, null);

		items.add("after");
		assertEquals(2, result.items.size());
		assertEquals("after", result.items.get(1));
	}
}
