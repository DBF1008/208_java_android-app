package cn.eoe.app.ui;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import android.content.Context;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v4.view.ViewPager;
import android.view.KeyEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import cn.eoe.app.R;
import cn.eoe.app.adapter.BasePageAdapter;
import cn.eoe.app.biz.SearchDao;
import cn.eoe.app.entity.CategorysEntity;
import cn.eoe.app.ui.base.BaseFragmentActivity;
import cn.eoe.app.utils.SearchRequestTracker;

public class SearchActivity extends BaseFragmentActivity implements
		OnClickListener {

	private ImageView btnGohome;
	private EditText edtSearch;
	private ListView mListView;
	private LinearLayout loadLayout;
	private String mTag;
	private InputMethodManager imm;
	private ViewPager mViewPager;
	private BasePageAdapter mBasePageAdapter;
	private List<Object> categoryList;
	private ImageView mWait;

	/**
	 * Tracks the latest search version so that stale (slow) responses
	 * from earlier queries are discarded instead of overwriting the UI
	 * that already shows a newer query's results.
	 */
	private final SearchRequestTracker searchTracker = new SearchRequestTracker();

	/**
	 * Reference to the currently-running search task, kept so that it
	 * can be cancelled when a newer search supersedes it.
	 */
	private MyTask currentTask;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.search_layout);
		Intent i = getIntent();
		mTag = i.getStringExtra("tag");
		initData();
		initView();
		initViewPager();
	}

	public void initData() {
		imm = (InputMethodManager) getApplicationContext().getSystemService(
				Context.INPUT_METHOD_SERVICE);
	}

	public void initView() {
		btnGohome = (ImageView) findViewById(R.id.btn_gohome);
		btnGohome.setOnClickListener(this);
		edtSearch = (EditText) findViewById(R.id.edt_search);
		mListView = (ListView) findViewById(R.id.list_view);
		loadLayout = (LinearLayout) findViewById(R.id.view_loading);

		loadLayout.setVisibility(View.GONE);
		mViewPager = (ViewPager) findViewById(R.id.above_pager);
		edtSearch.setHint("即将为您搜索 " + mTag);
		edtSearch.setOnFocusChangeListener(new View.OnFocusChangeListener() {
			public void onFocusChange(View v, boolean hasFocus) {
				if (hasFocus) {
					imm.showSoftInput(v, 0);
				} else {
					imm.hideSoftInputFromWindow(v.getWindowToken(), 0);
				}
			}
		});

		edtSearch.setOnKeyListener(new View.OnKeyListener() {
			public boolean onKey(View v, int keyCode, KeyEvent event) {
				if (keyCode == KeyEvent.KEYCODE_ENTER) {
					edtSearch.clearFocus();
					String searchContent = edtSearch.getText().toString();
					executeSearch(mTag, searchContent);
					return true;
				}
				return false;
			}
		});
		mWait = (ImageView) findViewById(R.id.search_imageview_wait);
	}

	/**
	 * Launches a search, cancelling any previous in-flight request.
	 *
	 * <p>A fresh {@link SearchDao} is created per call so that concurrent
	 * tasks never share mutable state (previously a single shared
	 * {@code SearchDao} could have its {@code mTag}/{@code keyWord}
	 * overwritten mid-flight by a newer search).
	 */
	private void executeSearch(String tag, String keyword) {
		// Cancel the previous task so it cannot deliver stale results even
		// if the version-check in onPostExecute were somehow bypassed.
		if (currentTask != null) {
			currentTask.cancel(true);
			currentTask = null;
		}

		int version = searchTracker.newSearch();
		SearchDao dao = new SearchDao(this);
		dao.setValue(tag, keyword);

		MyTask task = new MyTask(version, dao);
		currentTask = task;
		task.execute();
	}

	public void initViewPager() {
		mBasePageAdapter = new BasePageAdapter(SearchActivity.this);
		mViewPager.setAdapter(mBasePageAdapter);
	}

	/**
	 * Background task that performs a single search request.
	 *
	 * <p>Each task carries:
	 * <ul>
	 *   <li>a {@code version} id captured at creation time, used to
	 *       detect staleness in {@link #onPostExecute};</li>
	 *   <li>its own {@link SearchDao} instance, so concurrent tasks
	 *       never step on each other's mutable state.</li>
	 * </ul>
	 */
	public class MyTask extends AsyncTask<Void, String, Map<String, Object>> {

		private final int version;
		private final SearchDao dao;

		public MyTask(int version, SearchDao dao) {
			this.version = version;
			this.dao = dao;
		}

		@Override
		protected void onPreExecute() {
			mWait.setVisibility(View.GONE);
			loadLayout.setVisibility(View.VISIBLE);
			mViewPager.setVisibility(View.GONE);
			mViewPager.removeAllViews();
			mBasePageAdapter.Clear();
			super.onPreExecute();
		}

		@Override
		protected Map<String, Object> doInBackground(Void... params) {
			if (isCancelled()) {
				return null;
			}
			List<CategorysEntity> categorys = new ArrayList<CategorysEntity>();
			Map<String, Object> map = new HashMap<String, Object>();
			if ((categoryList = dao.mapperJson()) != null) {
				categorys = dao.getCategorys();
				map.put("list", categoryList);
				return map;
			} else {
				return null;
			}
		}

		@Override
		protected void onPostExecute(Map<String, Object> result) {
			super.onPostExecute(result);

			// ---- staleness guard ------------------------------------------
			// If a newer search was issued after this one, discard these
			// results entirely so they cannot overwrite the current UI.
			if (!searchTracker.isCurrent(version)) {
				return;
			}
			// This task is no longer the active one (it just completed).
			if (currentTask == this) {
				currentTask = null;
			}
			// ---------------------------------------------------------------

			mBasePageAdapter.Clear();
			mViewPager.removeAllViews();

			loadLayout.setVisibility(View.GONE);
			mViewPager.setVisibility(View.VISIBLE);

			if (result == null) {
				mBasePageAdapter.addNullFragment();
				loadLayout.setVisibility(View.GONE);
				return;
			}
			if (dao.getHasChild()) {
				mBasePageAdapter.addFragment((List) result.get("list"));
				loadLayout.setVisibility(View.GONE);
			} else {
				mWait.setVisibility(View.VISIBLE);
			}
			mBasePageAdapter.notifyDataSetChanged();
			mViewPager.setCurrentItem(0);
		}
	}

	@Override
	public void onClick(View v) {
		int id = v.getId();
		switch (id) {
		case R.id.btn_gohome:
			finish();
			break;
		}
	}

}
