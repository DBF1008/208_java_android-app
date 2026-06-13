package cn.eoe.app.view;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.http.message.BasicNameValuePair;

import android.app.Activity;
import android.content.Context;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ListView;
import android.widget.SimpleAdapter;
import cn.eoe.app.R;
import cn.eoe.app.entity.UserFavoriteList;
import cn.eoe.app.ui.DetailsActivity;
import cn.eoe.app.utils.IntentUtil;

public class UserCollectListFragment extends Fragment implements
		OnItemClickListener {

	static final String ARG_FAVORITE_LIST = "favorite_list";

	private ListView mlv;
	private List<Map<String, Object>> mlist;
	private SimpleAdapter mAdapter;
	private Context mContext;

	private UserFavoriteList mUserFavoriteList;

	public UserCollectListFragment() {
	}

	public static UserCollectListFragment newInstance(
			UserFavoriteList userFavoriteList) {
		UserCollectListFragment fragment = new UserCollectListFragment();
		Bundle args = new Bundle();
		args.putSerializable(ARG_FAVORITE_LIST, userFavoriteList);
		fragment.setArguments(args);
		return fragment;
	}

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		if (getArguments() != null) {
			mUserFavoriteList = (UserFavoriteList) getArguments()
					.getSerializable(ARG_FAVORITE_LIST);
		}
	}

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container,
			Bundle savedInstanceState) {
		super.onCreateView(inflater, container, savedInstanceState);
		mContext = inflater.getContext();
		View view = inflater.inflate(R.layout.user_collect_list, null);
		mlv = (ListView) view.findViewById(R.id.user_listview_collect);
		mlist = new ArrayList<Map<String, Object>>();
		if (mUserFavoriteList != null) {
			getData();
			mAdapter = new SimpleAdapter(inflater.getContext(), mlist,
					R.layout.user_collect_list_item, new String[] { "name",
							"content" }, new int[] {
							R.id.user_textview_collectTitle,
							R.id.user_textview_collectContent });
			mlv.setAdapter(mAdapter);
			mlv.setOnItemClickListener(this);
		}
		return view;
	}

	private void getData() {
		for (int i = 0, count = mUserFavoriteList.getLists().size(); i < count; i++) {
			Map<String, Object> map = new HashMap<String, Object>();
			map.put("name", mUserFavoriteList.getLists().get(i).getTitle());
			map.put("target", mUserFavoriteList.getLists().get(i).getUrl());
			map.put("content", mUserFavoriteList.getLists().get(i)
					.getShort_content());
			mlist.add(map);
		}
	}

	@Override
	public void onItemClick(AdapterView<?> arg0, View arg1, int arg2, long arg3) {
		HashMap<String, Object> map = (HashMap<String, Object>) arg0
				.getItemAtPosition(arg2);
		Activity activity = getActivity();
		if (activity != null) {
			startDetailActivity(activity, map.get("target").toString(),
					mUserFavoriteList.getName().toString(), map.get("name")
							.toString());
		}
	}

	public void setListContent(UserFavoriteList userFavoriteList) {
		mUserFavoriteList = userFavoriteList;
		mlist.clear();
		getData();
		mAdapter.notifyDataSetChanged();
	}

	public void startDetailActivity(Activity mContext, String url,
			String title, String shareTitle) {
		IntentUtil.start_activity(mContext, DetailsActivity.class,
				new BasicNameValuePair("url", url), new BasicNameValuePair(
						"title", title), new BasicNameValuePair("sharetitle",
						shareTitle));
	}
}
