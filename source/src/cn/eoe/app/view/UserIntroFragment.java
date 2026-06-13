package cn.eoe.app.view;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import android.content.Context;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.SimpleAdapter;
import android.widget.TextView;
import cn.eoe.app.R;
import cn.eoe.app.entity.UserResponse;
import cn.eoe.app.utils.ImageUtil;
import cn.eoe.app.utils.ImageUtil.ImageCallback;

public class UserIntroFragment extends Fragment {
	UserResponse mUserResponse;
	private ImageView img;
	private TextView txtName;
	private TextView txtRegTime;
	private TextView txtEP;
	private TextView txtEM;
	private GridView gvGrid;
	SimpleAdapter mAdapter;
	private List<Map<String, Object>> mList;
	private Context mContext;

	private static final String ARG_USER_RESPONSE = "arg_user_response";

	/**
	 * Required empty public constructor. After a configuration change or process
	 * death the framework re-creates fragments via reflection using this
	 * no-argument constructor, so all state must be supplied through
	 * {@link #newInstance(UserResponse)} / the arguments {@link Bundle} rather
	 * than a custom constructor.
	 */
	public UserIntroFragment() {
	}

	public static UserIntroFragment newInstance(UserResponse result) {
		UserIntroFragment fragment = new UserIntroFragment();
		Bundle args = new Bundle();
		args.putSerializable(ARG_USER_RESPONSE, result);
		fragment.setArguments(args);
		return fragment;
	}

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		if (getArguments() != null) {
			mUserResponse = (UserResponse) getArguments().getSerializable(
					ARG_USER_RESPONSE);
		}
	}

	// [start]继承方法

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container,
			Bundle savedInstanceState) {
		mContext = inflater.getContext();
		View v = inflater.inflate(R.layout.user_center_intro_fragment, null);
		initControl(v);
		initGridView();
		setControl();
		return v;
	}

	@Override
	public void onSaveInstanceState(Bundle outState) {
		super.onSaveInstanceState(outState);
	}

	// [end]

	private void initControl(View v) {
		img = (ImageView) v.findViewById(R.id.user_imageview_icon);
		txtName = (TextView) v.findViewById(R.id.user_textview_name);
		txtRegTime = (TextView) v.findViewById(R.id.user_textview_reg_time);
		txtEP = (TextView) v.findViewById(R.id.user_textview_e_p);
		txtEM = (TextView) v.findViewById(R.id.user_textview_e_m);
		gvGrid = (GridView) v.findViewById(R.id.user_gridview_medal);
	}

	private void initGridView() {
		getData();
		mAdapter = new SimpleAdapter(mContext, mList,
				R.layout.user_gridview_item_medal, new String[] { "img" },
				new int[] { R.id.user_imageview_medal }) {

			@Override
			public void setViewImage(ImageView v, String value) {
				// TODO Auto-generated method stub
				super.setViewImage(v, value);
				ImageUtil.setThumbnailView(value, v, mContext,
						new imageCallback(), false);
			}

			class imageCallback implements ImageCallback {

				@Override
				public void loadImage(Bitmap bitmap, String imagePath) {
					// TODO Auto-generated method stub
					try {
						ImageView img = (ImageView) gvGrid
								.findViewWithTag(imagePath);
						img.setImageBitmap(bitmap);
					} catch (NullPointerException ex) {
						Log.e("error", "ImageView = null");
					}
				}

			}

		};
		gvGrid.setAdapter(mAdapter);
	}

	private void getData() {
		mList = new ArrayList<Map<String, Object>>();
		for (int i = 0, count = mUserResponse.getInfo().getIcon().size(); i < count; i++) {
			Map<String, Object> map = new HashMap<String, Object>();
			map.put("img", mUserResponse.getInfo().getIcon().get(i).getImg());
			mList.add(map);
		}
	}

	private void setControl() {
		txtName.setText(mUserResponse.getInfo().getName());
		txtRegTime.setText(mUserResponse.getInfo().getReg_at());
		txtEP.setText(getString(R.string.user_center_e_coin, mUserResponse.getInfo().getEoe_m()));
		txtEM.setText(getString(R.string.user_center_e_reputation,  mUserResponse.getInfo().getEoe_p()));
		String imgUrl = mUserResponse.getInfo().getHead_image_url()
				.replaceAll("(?<==)small", "middle");
		ImageUtil.setThumbnailView(imgUrl, img, mContext,
				new myImageCallBack(), true);
	}

	class myImageCallBack implements ImageCallback {

		@Override
		public void loadImage(Bitmap bitmap, String imagePath) {
			// TODO Auto-generated method stub
			img.setImageBitmap(bitmap);
		}

	}
}
