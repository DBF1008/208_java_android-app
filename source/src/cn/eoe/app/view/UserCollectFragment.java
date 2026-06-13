package cn.eoe.app.view;

import android.content.Context;
import android.graphics.Color;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.LinearLayout.LayoutParams;
import android.widget.TextView;
import cn.eoe.app.R;
import cn.eoe.app.entity.UserResponse;

public class UserCollectFragment extends Fragment {

	private static final String ARG_USER_RESPONSE = "arg_user_response";

	LinearLayout mLinearLayout;
	private UserResponse mUserResponse;
	private Context mContext;
	private WindowManager wm;

	/**
	 * Required empty public constructor. After a configuration change or process
	 * death the framework re-creates fragments via reflection using this
	 * no-argument constructor, so all state must be supplied through
	 * {@link #newInstance(UserResponse)} / the arguments {@link Bundle} rather
	 * than a custom constructor or an {@code Activity} field.
	 */
	public UserCollectFragment() {
	}

	public static UserCollectFragment newInstance(UserResponse userResponse) {
		UserCollectFragment fragment = new UserCollectFragment();
		Bundle args = new Bundle();
		args.putSerializable(ARG_USER_RESPONSE, userResponse);
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

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container,
			Bundle savedInstanceState) {
		mContext = inflater.getContext();
		wm = (WindowManager) getActivity().getSystemService(
				Context.WINDOW_SERVICE);
		View view = inflater.inflate(R.layout.user_collect_fragment, null);
		mLinearLayout = (LinearLayout) view
				.findViewById(R.id.user_linear_collect_name);
		initLinear();
		// Add the nested list fragment only on first creation. After a
		// configuration change / process death the child FragmentManager
		// restores it automatically, so re-adding would duplicate it.
		if (savedInstanceState == null) {
			UserCollectListFragment listFragment = UserCollectListFragment
					.newInstance(mUserResponse.getFavorite().get(0));
			getChildFragmentManager().beginTransaction()
					.replace(R.id.user_linear_Collect_replace, listFragment)
					.commit();
		}
		return view;
	}

	private void initLinear() {
		for (int i = 0, count = mUserResponse.getFavorite().size(); i < count; i++) {
			mLinearLayout.addView(CreateTextView(i, mUserResponse.getFavorite()
					.get(i).getName()));
			if (i < count - 1) {
				mLinearLayout.addView(CreateSide());
			}
		}
	}

	/**
	 * Returns the currently attached list fragment, looked up from the child
	 * FragmentManager so the reference stays valid after the host fragment is
	 * re-created (the system restores the child into the same container).
	 */
	private UserCollectListFragment getListFragment() {
		return (UserCollectListFragment) getChildFragmentManager()
				.findFragmentById(R.id.user_linear_Collect_replace);
	}

	private TextView CreateTextView(final int i, String name) {
		// TODO Auto-generated method stub
		TextView tv = new TextView(mContext);
		int width = wm.getDefaultDisplay().getWidth() / 3;
		LayoutParams layout = new LayoutParams(width, LayoutParams.MATCH_PARENT);
		tv.setLayoutParams(layout);
		if (i == 0) {
			tv.setBackgroundResource(R.drawable.dis_usercollect_left);
			mLinearLayout.setTag(tv);
		}
		tv.setGravity(Gravity.CENTER);
		tv.setTextColor(Color.BLACK);
		tv.setText(name);
		tv.setClickable(true);
		tv.setOnClickListener(new View.OnClickListener() {

			@Override
			public void onClick(View v) {
				// TODO Auto-generated method stub
				UserCollectListFragment listFragment = getListFragment();
				if (listFragment != null) {
					listFragment.setListContent(mUserResponse.getFavorite().get(
							i));
				}
				if (mLinearLayout.getTag() != null) {
					((View) mLinearLayout.getTag())
							.setBackgroundColor(Color.TRANSPARENT);
				}
				mLinearLayout.setTag(v);
				if (i == 0) {
					v.setBackgroundResource(R.drawable.dis_usercollect_left);
				} else if (i == mUserResponse.getFavorite().size() - 1) {
					v.setBackgroundResource(R.drawable.dis_usercollect_right);
				}
			}
		});
		return tv;
	}

	private ImageView CreateSide() {
		ImageView img = new ImageView(mContext);
		LayoutParams params = new LayoutParams(LayoutParams.WRAP_CONTENT,
				LayoutParams.MATCH_PARENT);
		img.setLayoutParams(params);
		img.setBackgroundResource(R.drawable.dis_behind_verticalside);
		return img;
	}
}
