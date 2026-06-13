package cn.eoe.app.ui;

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
import cn.eoe.app.ui.base.BaseFragmentActivity;
import cn.eoe.app.utils.SearchRequestCoordinator;

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
	private ImageView mWait;

	/** Issues a fresh token per search so only the most recent one renders. */
	private final SearchRequestCoordinator searchCoordinator = new SearchRequestCoordinator();
	/** The currently running search task, kept so a newer search can cancel it. */
	private MyTask currentTask;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		// TODO Auto-generated method stub
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
//		txtEmpty = (TextView) findViewById(R.id.txt_empty);
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
				// Only react to the key-down of ENTER. A physical key press
				// delivers both ACTION_DOWN and ACTION_UP; deduping on the
				// action (instead of the old toggling View tag) guarantees
				// exactly one search per press and removes the "every other
				// Enter is ignored" behaviour of the previous tag switch.
				if (keyCode == KeyEvent.KEYCODE_ENTER
						&& event.getAction() == KeyEvent.ACTION_DOWN) {
					startSearch();
					return true;
				}
				return false;
			}
		});
		mWait=(ImageView)findViewById(R.id.search_imageview_wait);
	}

	public void initViewPager() {
		mBasePageAdapter = new BasePageAdapter(SearchActivity.this);
		mViewPager.setAdapter(mBasePageAdapter);
	}

	/**
	 * Launches a search for the current input. Each call is stamped with a fresh
	 * token and cancels the previous in-flight task, so when several searches are
	 * fired in quick succession only the result of the last one is applied to the
	 * UI (see {@link SearchRequestCoordinator}). A per-request {@link SearchDao}
	 * instance is used so concurrent tasks never corrupt one another's state.
	 */
	private void startSearch() {
		edtSearch.clearFocus();
		String searchContent = edtSearch.getText().toString();

		SearchDao dao = new SearchDao(SearchActivity.this);
		dao.setValue(mTag, searchContent);

		int token = searchCoordinator.next();
		if (currentTask != null) {
			currentTask.cancel(true);
		}
		currentTask = new MyTask(token);
		currentTask.execute(dao);
	}

	public class MyTask extends AsyncTask<SearchDao, Void, Map<String, Object>> {

		private final int token;
		private SearchDao dao;

		public MyTask(int token) {
			this.token = token;
		}

		@Override
		protected void onPreExecute() {
			// TODO Auto-generated method stub
			mWait.setVisibility(View.GONE);
			loadLayout.setVisibility(View.VISIBLE);
			mViewPager.setVisibility(View.GONE);
			mViewPager.removeAllViews();
			mBasePageAdapter.Clear();
			super.onPreExecute();
		}

		@Override
		protected Map<String, Object> doInBackground(SearchDao... params) {
			// Read from the per-request DAO passed in params, never a shared
			// field, so a slow request cannot observe state mutated by a newer
			// one. The token check in onPostExecute then decides whether this
			// parsed result is still allowed to reach the UI.
			dao = params[0];
			List<Object> list = dao.mapperJson();
			if (list == null) {
				return null;
			}
			Map<String, Object> map = new HashMap<String, Object>();
			map.put("list", list);
			return map;
		}

		@Override
		protected void onPostExecute(Map<String, Object> result) {
			super.onPostExecute(result);
			// Last-input-wins: if this task was cancelled or a newer search has
			// since started, drop the stale result without touching the UI so it
			// can never overwrite the page rendered for a more recent keyword.
			if (isCancelled() || !searchCoordinator.isLatest(token)) {
				return;
			}

			mBasePageAdapter.Clear();
			mViewPager.removeAllViews();

			loadLayout.setVisibility(View.GONE);
			mViewPager.setVisibility(View.VISIBLE);

			if (result == null) {
				mBasePageAdapter.addNullFragment();
				loadLayout.setVisibility(View.GONE);
//				txtEmpty.setVisibility(View.VISIBLE);
				return;
			}
			if (dao.getHasChild()) {
				mBasePageAdapter.addFragment((List) result.get("list"));
				loadLayout.setVisibility(View.GONE);
//				txtEmpty.setVisibility(View.GONE);
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
