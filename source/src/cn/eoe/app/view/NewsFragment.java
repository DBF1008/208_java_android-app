package cn.eoe.app.view;

import java.util.ArrayList;
import java.util.List;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.TextView;
import cn.eoe.app.R;
import cn.eoe.app.biz.NewsDao;
import cn.eoe.app.entity.NewsCategoryListEntity;
import cn.eoe.app.entity.NewsContentItem;
import cn.eoe.app.entity.NewsMoreResponse;
import cn.eoe.app.utils.ImageUtil;

@SuppressLint("NewApi")
public class NewsFragment extends BaseListFragment
		implements BaseListFragment.LoadMoreCallback<NewsMoreResponse> {

	public Activity mActivity;
	private List<NewsContentItem> items_list = new ArrayList<NewsContentItem>();
	private String more_url;
	private MyAdapter mAdapter;

	// add this constructor by King0769, 2013/5/7
	// in order to solve an exception that "can't instantiate class
	// cn.eoe.app.view.NewsFragment; no empty constructor"
	// I found it in this case : 1.open eoe program -> 2.change system
	// language -> 3.reopen eoe, can see FC(force close)
	// I think this bug will happens in many cases.
	public NewsFragment() {

	}
	//--------------------

	public NewsFragment(Activity c, NewsCategoryListEntity categorys) {
		this.mActivity = c;

		if (categorys != null) {
			this.items_list = categorys.getItems();
			more_url = categorys.getMore_url();
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
				NewsContentItem item = (NewsContentItem) mAdapter
						.getItem(position - 1);
				startDetailActivity(mActivity, item.getDetail_url(), "资讯",
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

	// ---- LoadMoreCallback<NewsMoreResponse> ----

	@Override
	public NewsMoreResponse doLoadMore() {
		return new NewsDao(mActivity).getMore(more_url);
	}

	@Override
	public List<?> extractItems(NewsMoreResponse response) {
		NewsCategoryListEntity entity = response.getResponse();
		return entity != null ? entity.getItems() : null;
	}

	@Override
	public String extractMoreUrl(NewsMoreResponse response) {
		NewsCategoryListEntity entity = response.getResponse();
		return entity != null ? entity.getMore_url() : null;
	}

	// ---- BaseListFragment abstract overrides ----

	@Override
	protected void appendToAdapter(Object items) {
		if (mAdapter != null) {
			@SuppressWarnings("unchecked")
			List<NewsContentItem> list = (List<NewsContentItem>) items;
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
		List<NewsContentItem> mList = new ArrayList<NewsContentItem>();

		public MyAdapter() {
		}

		public void appendToList(List<NewsContentItem> lists) {
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
			NewsContentItem item = mList.get(position);
			if (convertView == null) {
				holder = new ViewHolder();
				convertView = mInflater
						.inflate(R.layout.news_item_layout, null);
				holder.title_ = (TextView) convertView
						.findViewById(R.id.news_title);
				holder.short_ = (TextView) convertView
						.findViewById(R.id.news_short_content);
				holder.img_thu = (ImageView) convertView
						.findViewById(R.id.img_thu);
				convertView.setTag(holder);
			} else {
				holder = (ViewHolder) convertView.getTag();
			}
			holder.title_.setText(item.getTitle());
			holder.short_.setText(item.getShort_content());
			String img_url = item.getThumbnail_url();
			if (img_url == null || img_url.equals("")) {
				holder.img_thu.setVisibility(View.GONE);
			} else {
				holder.img_thu.setVisibility(View.VISIBLE);
				ImageUtil.setThumbnailView(img_url, holder.img_thu, mActivity,
						callback1, false);
			}
			return convertView;
		}
	}

	static class ViewHolder {
		public TextView title_;
		public TextView short_;
		public ImageView img_thu;
	}

}
