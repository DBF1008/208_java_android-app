package cn.eoe.app.view;

import java.util.ArrayList;
import java.util.List;

import android.app.Activity;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.BaseAdapter;
import android.widget.TextView;
import cn.eoe.app.R;
import cn.eoe.app.biz.WikiDao;
import cn.eoe.app.entity.WikiCategoryListEntity;
import cn.eoe.app.entity.WikiContentItem;
import cn.eoe.app.entity.WikiMoreResponse;

public class WikiFragment extends BaseListFragment
		implements BaseListFragment.LoadMoreCallback<WikiMoreResponse> {

	List<WikiContentItem> items_list = new ArrayList<WikiContentItem>();
	private Activity mActivity;
	private MyAdapter mAdapter;
	private String more_url;

	// Empty constructor required for Fragment re-instantiation
	// (e.g. after configuration change or ViewPager recreation)
	public WikiFragment() {
	}

	public WikiFragment(Activity c, WikiCategoryListEntity categorys) {
		this.mActivity = c;
		if (categorys != null) {
			this.more_url = categorys.getMore_url();
			this.items_list = categorys.getItems();
		}
	}

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container,
			Bundle savedInstanceState) {
		super.onCreateView(inflater, container, savedInstanceState);
		listview.setXListViewListener(this);
		// construct the RelativeLayout
		mAdapter = new MyAdapter();
		mAdapter.appendToList(items_list);
		listview.setAdapter(mAdapter);
		listview.setOnItemClickListener(new OnItemClickListener() {

			@Override
			public void onItemClick(AdapterView<?> parent, View view,
					int position, long id) {
				WikiContentItem item = (WikiContentItem) mAdapter
						.getItem(position - 1);
				startDetailActivity(mActivity, item.getDetail_url(), "教程",
						item.getTitle());
			}
		});
		return view;
	}

	@Override
	public void onDestroyView() {
		super.onDestroyView();
		mAdapter = null;
	}

	// ---- LoadMoreCallback<WikiMoreResponse> ----

	@Override
	public WikiMoreResponse doLoadMore() {
		return new WikiDao(mActivity).getMore(more_url);
	}

	@Override
	public List<?> extractItems(WikiMoreResponse response) {
		WikiCategoryListEntity entity = response.getResponse();
		return entity != null ? entity.getItems() : null;
	}

	@Override
	public String extractMoreUrl(WikiMoreResponse response) {
		WikiCategoryListEntity entity = response.getResponse();
		return entity != null ? entity.getMore_url() : null;
	}

	// ---- BaseListFragment abstract overrides ----

	@Override
	protected void appendToAdapter(Object items) {
		if (mAdapter != null) {
			@SuppressWarnings("unchecked")
			List<WikiContentItem> list = (List<WikiContentItem>) items;
			mAdapter.appendToList(list);
		}
	}

	@Override
	protected void onMoreUrlUpdated(String newMoreUrl) {
		this.more_url = newMoreUrl;
	}

	// ---- IXListViewListener ----

	@Override
	public void onRefresh() {
		onLoad();
	}

	@Override
	public void onLoadMore() {
		if (more_url == null || more_url.equals("")) {
			onLoad();
			return;
		}
		performLoadMore(this);
	}

	// ---- Inner adapter ----

	class MyAdapter extends BaseAdapter {

		List<WikiContentItem> mList = new ArrayList<WikiContentItem>();

		public MyAdapter() {

		}

		public void appendToList(List<WikiContentItem> lists) {

			if (lists == null) {
				return;
			}
			mList.addAll(lists);
			notifyDataSetChanged();
		}

		@Override
		public int getCount() {
			return mList.size();
		}

		@Override
		public Object getItem(int position) {
			return mList.get(position);
		}

		@Override
		public long getItemId(int position) {
			return position;
		}

		@Override
		public View getView(int position, View convertView, ViewGroup parent) {
			ViewHolder holder;
			WikiContentItem item = mList.get(position);
			if (convertView == null) {
				holder = new ViewHolder();
				convertView = mInflater
						.inflate(R.layout.wiki_item_layout, null);
				holder.title_ = (TextView) convertView
						.findViewById(R.id.wiki_title);
				holder.title_.setText(item.getTitle());
				convertView.setTag(holder);
			} else {
				holder = (ViewHolder) convertView.getTag();
				holder.title_.setText(item.getTitle());
			}
			return convertView;
		}
	}

	static class ViewHolder {
		public TextView title_;
		public TextView short_;
	}

}
