package cn.eoe.app.view;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import cn.eoe.app.R;
import cn.eoe.app.ui.UserLoginUidActivity;

public class UserLogOutFragment extends Fragment implements OnClickListener {

	static final String ARG_IS_SHOW_TXT = "is_show_txt";

	private Button btnLogOut;
	private TextView mtxt;

	private Context mContext;
	private boolean isShowtxt;

	public UserLogOutFragment() {
	}

	public static UserLogOutFragment newInstance(boolean isShowtxt) {
		UserLogOutFragment fragment = new UserLogOutFragment();
		Bundle args = new Bundle();
		args.putBoolean(ARG_IS_SHOW_TXT, isShowtxt);
		fragment.setArguments(args);
		return fragment;
	}

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		if (getArguments() != null) {
			isShowtxt = getArguments().getBoolean(ARG_IS_SHOW_TXT, false);
		}
	}

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container,
			Bundle savedInstanceState) {
		super.onCreateView(inflater, container, savedInstanceState);
		mContext = inflater.getContext();
		View view = inflater.inflate(R.layout.user_login_log_out, null);
		btnLogOut = (Button) view.findViewById(R.id.user_button_logOut);
		mtxt = (TextView) view.findViewById(R.id.user_textview_error);
		showText(isShowtxt);
		btnLogOut.setOnClickListener(this);
		return view;
	}

	public void showText(boolean isShow) {
		if (isShow) {
			mtxt.setVisibility(View.VISIBLE);
		} else {
			mtxt.setVisibility(View.GONE);
		}
	}

	@Override
	public void onClick(View v) {
		switch (v.getId()) {
		case R.id.user_button_logOut:
			SharedPreferences share = mContext.getSharedPreferences(
					UserLoginUidActivity.SharedName, Context.MODE_PRIVATE);
			SharedPreferences.Editor edit = share.edit();
			edit.clear().commit();
			Activity activity = getActivity();
			if (activity != null) {
				activity.finish();
			}
			break;
		}
	}
}
