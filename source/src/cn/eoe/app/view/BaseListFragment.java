package cn.eoe.app.view;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.apache.http.message.BasicNameValuePair;
import org.codehaus.jackson.map.ObjectMapper;

import android.app.Activity;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.support.v4.app.Fragment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import cn.eoe.app.R;
import cn.eoe.app.ui.DetailsActivity;
import cn.eoe.app.utils.ImageUtil.ImageCallback;
import cn.eoe.app.utils.IntentUtil;
import cn.eoe.app.widget.XListView;
import cn.eoe.app.widget.XListView.IXListViewListener;

public abstract class BaseListFragment extends Fragment implements
		IXListViewListener {

	protected XListView listview;
	protected View view;
	LayoutInflater mInflater;
	protected boolean mIsScroll = false;
	ObjectMapper mMapper = new ObjectMapper();
	protected BaseAdapter mAdapter;

	public ExecutorService executorService = Executors.newFixedThreadPool(5);

	// ---- Lifecycle-aware load-more state ----
	private boolean mIsDestroyed = false;
	private int mLoadMoreGeneration = 0;

	private static final int MSG_LOAD_MORE_RESULT = 0;

	private Handler mHandler = new Handler(Looper.getMainLooper()) {
		@Override
		public void handleMessage(Message msg) {
			if (mIsDestroyed || !isAdded()) {
				return;
			}
			if (msg.what == MSG_LOAD_MORE_RESULT) {
				int generation = msg.arg1;
				if (generation != mLoadMoreGeneration) {
					// Stale result from a previous load-more cycle
					// (e.g. before tab switch or screen rotation). Discard.
					return;
				}
				if (msg.obj != null) {
					@SuppressWarnings("unchecked")
					LoadMoreResult<Object> result =
							(LoadMoreResult<Object>) msg.obj;
					appendToAdapter(result.items);
					if (result.newMoreUrl != null) {
						onMoreUrlUpdated(result.newMoreUrl);
					}
				}
				// If msg.obj == null: network error or empty response.
				// Still call onLoad() below to stop the loading spinner.
			}
			onLoad();
		}
	};

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container,
			Bundle savedInstanceState) {
		mIsDestroyed = false;
		mInflater = inflater;
		view = inflater.inflate(R.layout.main, null);
		listview = (XListView) view.findViewById(R.id.list_view);
		initListView();
		listview.setPullLoadEnable(true);
		listview.setPullRefreshEnable(false);
		return super.onCreateView(inflater, container, savedInstanceState);
	}

	@Override
	public void onDestroyView() {
		mIsDestroyed = true;
		mLoadMoreGeneration++;
		if (mHandler != null) {
			mHandler.removeCallbacksAndMessages(null);
		}
		super.onDestroyView();
	}

	@Override
	public void onDestroy() {
		if (mHandler != null) {
			mHandler.removeCallbacksAndMessages(null);
		}
		super.onDestroy();
	}

	private void initListView() {
	}

	public void startDetailActivity(Activity mContext, String url,
			String title, String shareTitle) {
		IntentUtil.start_activity(mContext, DetailsActivity.class,
				new BasicNameValuePair("url", url), new BasicNameValuePair(
						"title", title), new BasicNameValuePair("sharetitle",
						shareTitle));
	}

	protected void onLoad() {
		if (listview == null) {
			return;
		}
		listview.stopRefresh();
		listview.stopLoadMore();
		listview.setRefreshTime("刚刚");
	}

	/**
	 * Perform a load-more request with lifecycle awareness.
	 * The request runs on a background thread; the result is delivered
	 * on the main thread only if the fragment is still alive and the
	 * generation counter matches (i.e. no tab switch / rotation happened).
	 *
	 * @param callback encapsulates the network call and result parsing
	 * @param <T>      the response type
	 */
	protected <T> void performLoadMore(LoadMoreCallback<T> callback) {
		mLoadMoreGeneration++;
		final int generation = mLoadMoreGeneration;

		new Thread(new Runnable() {
			@Override
			public void run() {
				try {
					T response = callback.doLoadMore();
					if (response != null && mHandler != null) {
						List<?> items = callback.extractItems(response);
						String newUrl = callback.extractMoreUrl(response);
						LoadMoreResult<Object> result =
								new LoadMoreResult<Object>(items, newUrl);
						Message msg = mHandler.obtainMessage(
								MSG_LOAD_MORE_RESULT, result);
						msg.arg1 = generation;
						mHandler.sendMessage(msg);
					} else if (mHandler != null) {
						// No data — still need to stop the loading spinner
						Message msg = mHandler.obtainMessage(
								MSG_LOAD_MORE_RESULT);
						msg.arg1 = generation;
						mHandler.sendMessage(msg);
					}
				} catch (Exception e) {
					Log.e("BaseListFragment",
							"Load-more request failed", e);
					if (mHandler != null) {
						Message msg = mHandler.obtainMessage(
								MSG_LOAD_MORE_RESULT);
						msg.arg1 = generation;
						mHandler.sendMessage(msg);
					}
				}
			}
		}).start();
	}

	/**
	 * Called on the main thread to append loaded items into the adapter.
	 * Subclasses implement this to call their specific adapter's
	 * appendToList() method.
	 *
	 * @param items the list of items to append
	 */
	protected abstract void appendToAdapter(Object items);

	/**
	 * Called after a successful load-more to let the subclass update
	 * its stored more_url. Default implementation does nothing.
	 *
	 * @param newMoreUrl the new "more" URL from the response
	 */
	protected void onMoreUrlUpdated(String newMoreUrl) {
		// Subclasses may override
	}

	cn.eoe.app.utils.ImageUtil.ImageCallback callback1 = new ImageCallback() {

		@Override
		public void loadImage(Bitmap bitmap, String imagePath) {
			try {
				ImageView img = (ImageView) listview.findViewWithTag(imagePath);
				img.setImageBitmap(bitmap);
			} catch (NullPointerException ex) {
				Log.e("error", "ImageView = null");
			}
		}
	};

	// ---- Inner types for lifecycle-aware load-more ----

	/**
	 * Callback that subclasses pass to
	 * {@link #performLoadMore(LoadMoreCallback)} to encapsulate
	 * the network call and result parsing.
	 *
	 * @param <T> the response type returned by the DAO
	 */
	public interface LoadMoreCallback<T> {
		/**
		 * Execute the network/cache request on a background thread.
		 *
		 * @return the parsed response, or null on failure
		 */
		T doLoadMore();

		/**
		 * Extract the list of items from the response.
		 *
		 * @param response the non-null response
		 * @return list of items to append
		 */
		List<?> extractItems(T response);

		/**
		 * Extract the next-page "more" URL from the response.
		 *
		 * @param response the non-null response
		 * @return the new more URL, or null to keep the current one
		 */
		String extractMoreUrl(T response);
	}

	/**
	 * Carries a load-more result through the Handler message.
	 */
	public static class LoadMoreResult<T> {
		public final List<T> items;
		public final String newMoreUrl;

		public LoadMoreResult(List<T> items, String newMoreUrl) {
			this.items = items;
			this.newMoreUrl = newMoreUrl;
		}
	}
}
