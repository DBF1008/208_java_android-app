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
import android.widget.ImageView;
import android.widget.TextView;
import cn.eoe.app.R;
import cn.eoe.app.biz.BlogsDao;
import cn.eoe.app.entity.BlogContentItem;
import cn.eoe.app.entity.BlogsCategoryListEntity;
import cn.eoe.app.entity.BlogsMoreResponse;
import cn.eoe.app.utils.ImageUtil;

/**
 * 博客部分的Fragment
 *
 * @author wangxin
 *
 */
public class BlogFragment extends BaseListFragment
		implements BaseListFragment.LoadMoreCallback<BlogsMoreResponse> {

	List<BlogContentItem> items_list = new ArrayList<BlogContentItem>();
	private Activity mActivity;
	private String more_url;
	private MyAdapter mAdapter;

	public BlogFragment() {
	}

	public BlogFragment(Activity c, BlogsCategoryListEntity categorys) {
		this.mActivity = c;

		if (categorys != null) {
			more_url = categorys.getMore_url();
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
				BlogContentItem item = (BlogContentItem) mAdapter
						.getItem(position - 1);
				startDetailActivity(mActivity, item.getDetail_url(), "博客",
						item.getTitle());
			}
		});
		return view;
	}

	@Override
	public void onSaveInstanceState(Bundle outState) {
		super.onSaveInstanceState(outState);
	}

	@Override
	public void onDestroyView() {
		super.onDestroyView();
		mAdapter = null;
	}

	// ---- LoadMoreCallback<BlogsMoreResponse> ----

	@Override
	public BlogsMoreResponse doLoadMore() {
		return new BlogsDao(mActivity).getMore(more_url);
	}

	@Override
	public List<?> extractItems(BlogsMoreResponse response) {
		BlogsCategoryListEntity entity = response.getResponse();
		return entity != null ? entity.getItems() : null;
	}

	@Override
	public String extractMoreUrl(BlogsMoreResponse response) {
		BlogsCategoryListEntity entity = response.getResponse();
		return entity != null ? entity.getMore_url() : null;
	}

	// ---- BaseListFragment abstract overrides ----

	@Override
	protected void appendToAdapter(Object items) {
		if (mAdapter != null) {
			@SuppressWarnings("unchecked")
			List<BlogContentItem> list = (List<BlogContentItem>) items;
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

		List<BlogContentItem> mList = new ArrayList<BlogContentItem>();

		public MyAdapter() {

		}

		public void appendToList(List<BlogContentItem> lists) {

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
			BlogContentItem item = mList.get(position);
			if (convertView == null) {
				holder = new ViewHolder();
				convertView = mInflater.inflate(R.layout.blogs_item_layout,
						null);
				holder.header_ = (TextView) convertView
						.findViewById(R.id.tx_header_title);
				holder.title_ = (TextView) convertView
						.findViewById(R.id.txt_title);
				holder.short_ = (TextView) convertView
						.findViewById(R.id.txt_short_content);
				holder.img_thu = (ImageView) convertView
						.findViewById(R.id.img_thu);
				convertView.setTag(holder);

			} else {
				holder = (ViewHolder) convertView.getTag();
			}
			holder.header_.setText(item.getName());
			holder.title_.setText(item.getTitle());
			holder.short_.setText(item.getShort_content());
			String url = item.getHead_image_url().replaceAll("=small",
					"=middle");
			if (url == null || url.equals("")) {
				holder.img_thu.setVisibility(View.GONE);
			} else {
				holder.img_thu.setVisibility(View.VISIBLE);
				ImageUtil.setThumbnailView(url, holder.img_thu, mActivity,
						callback1, false);
			}
			return convertView;
		}

	}

	static class ViewHolder {
		public TextView header_;
		public TextView title_;
		public TextView short_;
		public ImageView img_thu;
	}

}
