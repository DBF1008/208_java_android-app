package cn.eoe.app.view;

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

	private static final String ARG_SHOW_ERROR = "arg_show_error";

	private Button btnLogOut;
	private TextView mtxt;

	private Context mContext;
	private boolean isShowtxt;

	/**
	 * Required empty public constructor. After a configuration change or process
	 * death the framework re-creates fragments via reflection using this
	 * no-argument constructor, so all state must be supplied through
	 * {@link #newInstance(boolean)} / the arguments {@link Bundle} rather than a
	 * custom constructor or an {@code Activity} field.
	 */
	public UserLogOutFragment() {
	}

	public static UserLogOutFragment newInstance(boolean isShow) {
		UserLogOutFragment fragment = new UserLogOutFragment();
		Bundle args = new Bundle();
		args.putBoolean(ARG_SHOW_ERROR, isShow);
		fragment.setArguments(args);
		return fragment;
	}

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		if (getArguments() != null) {
			isShowtxt = getArguments().getBoolean(ARG_SHOW_ERROR);
		}
	}

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container,
			Bundle savedInstanceState) {
		// TODO Auto-generated method stub
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
		// TODO Auto-generated method stub
		switch (v.getId()) {
		case R.id.user_button_logOut:
			SharedPreferences share = mContext.getSharedPreferences(
					UserLoginUidActivity.SharedName, Context.MODE_PRIVATE);
			SharedPreferences.Editor edit = share.edit();
			edit.clear().commit();
			getActivity().finish();
			break;
		}
	}
}
