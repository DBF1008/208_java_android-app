package cn.eoe.app.view;

import java.lang.ref.WeakReference;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.apache.http.message.BasicNameValuePair;
import org.codehaus.jackson.map.ObjectMapper;

import android.app.Activity;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.support.v4.app.Fragment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import cn.eoe.app.R;
import cn.eoe.app.entity.base.BaseContentList;
import cn.eoe.app.ui.DetailsActivity;
import cn.eoe.app.utils.ImageUtil.ImageCallback;
import cn.eoe.app.utils.IntentUtil;
import cn.eoe.app.widget.XListView;
import cn.eoe.app.widget.XListView.IXListViewListener;

public abstract class BaseListFragment extends Fragment implements
		IXListViewListener {

	protected XListView listview;
	protected View view;
	LayoutInflater mInflater;
	protected boolean mIsScroll = false;
	ObjectMapper mMapper = new ObjectMapper();
	protected BaseAdapter mAdapter;

	public ExecutorService executorService = Executors.newFixedThreadPool(5);

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container,
			Bundle savedInstanceState) {
		mInflater = inflater;
		view = inflater.inflate(R.layout.main, null);
		listview = (XListView) view.findViewById(R.id.list_view);
		initListView();
		listview.setPullLoadEnable(true);
		listview.setPullRefreshEnable(false);
		// 每次 view 创建都开启一个新的分页会话，并新建一个不持有 Fragment 强引用的
		// Handler；这样上一轮 view 残留的在途请求结果会被会话号判定为过期而丢弃。
		mLoadMoreController.reset();
		mLoadMoreHandler = new LoadMoreHandler(this);
		return super.onCreateView(inflater, container, savedInstanceState);
	}

	private void initListView() {
	}

	public void startDetailActivity(Activity mContext, String url,
			String title, String shareTitle) {
		IntentUtil.start_activity(mContext, DetailsActivity.class,
				new BasicNameValuePair("url", url), new BasicNameValuePair(
						"title", title), new BasicNameValuePair("sharetitle",
						shareTitle));
	}

	protected void onLoad() {
		listview.stopRefresh();
		listview.stopLoadMore();
		listview.setRefreshTime("刚刚");
	}

	cn.eoe.app.utils.ImageUtil.ImageCallback callback1 = new ImageCallback() {

		@Override
		public void loadImage(Bitmap bitmap, String imagePath) {
			// TODO Auto-generated method stub
			try {
				ImageView img = (ImageView) listview.findViewWithTag(imagePath);
				img.setImageBitmap(bitmap);
			} catch (NullPointerException ex) {
				Log.e("error", "ImageView = null");
			}
		}
	};

	// ====================================================================
	// 与 Fragment 生命周期一致的"加载更多"分发
	//
	// 旧实现里每个子类 Fragment 直接 new Thread() 请求下一页，再通过成员匿名
	// Handler 把结果追加进 Adapter。匿名 Handler / Thread 隐式持有 Fragment（进而
	// 持有 Activity / View），且没有任何取消或存活检查：用户快速切 tab、返回上级
	// 页面、旋转屏幕后，晚到的线程仍会调用 appendToList()/onLoad()，把旧分页塞进
	// 新页面、甚至触碰已销毁的 view，并造成泄漏。
	//
	// 现在统一在基类处理：begin() 拿到会话令牌 -> 后台线程取数据 -> 通过一个静态、
	// 仅持有 WeakReference 的 Handler 把 (令牌, 结果) 投递回主线程 -> 只有
	// LoadMoreController 判定令牌仍有效（view 还活着且仍是同一会话）时才回写。
	// onDestroyView() 关闭闸门并清空消息队列，晚到的结果会被静默丢弃。
	// ====================================================================

	private static final int MSG_LOAD_MORE_DONE = 0x4C4D; // 'LM'

	/** 与本 Fragment 生命周期一致的分页闸门，详见 {@link LoadMoreController}。 */
	protected final LoadMoreController mLoadMoreController = new LoadMoreController();

	/** 静态、弱引用本 Fragment 的 Handler；view 创建时新建，销毁时清空。 */
	private LoadMoreHandler mLoadMoreHandler;

	@Override
	public void onLoadMore() {
		if (!hasMoreToLoad()) {
			// 没有更多数据可加载，直接复位底部"加载中"footer。
			onLoad();
			return;
		}
		final int token = mLoadMoreController.begin();
		if (token == LoadMoreController.INVALID_TOKEN) {
			// view 已销毁，或已有一个请求在途——忽略本次触发。
			return;
		}
		new LoadMoreTask(this, mLoadMoreHandler, token).start();
	}

	/**
	 * 子类返回：当前是否还有下一页可以请求（一般即 more_url 非空）。基类默认返回
	 * {@code false}，从而对未参与分页的子类是安全的 no-op。
	 */
	protected boolean hasMoreToLoad() {
		return false;
	}

	/**
	 * 在后台线程调用：请求并返回下一页数据，没有则返回 {@code null}。所有列表实体都
	 * 继承自 {@link BaseContentList}，因此用它作为统一返回类型。
	 */
	protected BaseContentList fetchMorePage() {
		return null;
	}

	/**
	 * 在主线程调用，且仅当结果已通过生命周期校验时才会被调用：把已投递的一页追加到
	 * Adapter，并推进 more_url。子类按各自的具体实体类型实现。
	 */
	protected void applyMorePage(BaseContentList page) {
	}

	/**
	 * 主线程：分页请求返回后的最终分发。先结束本次加载，再校验令牌——只有仍然有效时
	 * 才真正回写并复位 footer，否则静默丢弃（view 已销毁或已切到新会话）。请求失败
	 * （page 为 null）时不追加数据，但仍复位 footer，避免底部"加载中"一直转。
	 */
	private void deliverMorePage(int token, BaseContentList page) {
		mLoadMoreController.finishLoad();
		if (mLoadMoreController.shouldDeliver(token)) {
			if (page != null) {
				applyMorePage(page);
			}
			onLoad();
		}
	}

	@Override
	public void onDestroyView() {
		// 关闭闸门 + 清空尚未派发的消息：在途线程返回后会被判定过期而丢弃，且不再
		// 触碰已销毁的 view，Handler 也不再持有本 Fragment。
		mLoadMoreController.invalidate();
		if (mLoadMoreHandler != null) {
			mLoadMoreHandler.removeCallbacksAndMessages(null);
			mLoadMoreHandler = null;
		}
		super.onDestroyView();
	}

	/**
	 * 静态 Handler，仅通过 {@link WeakReference} 持有 Fragment，避免排队中的消息把
	 * 已销毁的 Fragment / Activity 钉在内存里。
	 */
	private static final class LoadMoreHandler extends Handler {
		private final WeakReference<BaseListFragment> mFragmentRef;

		LoadMoreHandler(BaseListFragment fragment) {
			super(Looper.getMainLooper());
			mFragmentRef = new WeakReference<BaseListFragment>(fragment);
		}

		@Override
		public void handleMessage(Message msg) {
			if (msg.what != MSG_LOAD_MORE_DONE) {
				return;
			}
			BaseListFragment fragment = mFragmentRef.get();
			if (fragment == null) {
				return; // Fragment 已被回收，无需处理
			}
			fragment.deliverMorePage(msg.arg1, (BaseContentList) msg.obj);
		}
	}

	/**
	 * 静态后台任务，仅通过 {@link WeakReference} 持有 Fragment。请求期间会临时强引用
	 * Fragment（Dao 需要 Activity），但任务本身短命且不被任何成员引用，请求一结束即可
	 * 被回收，因此不会随切 tab 累积泄漏。
	 */
	private static final class LoadMoreTask extends Thread {
		private final WeakReference<BaseListFragment> mFragmentRef;
		private final LoadMoreHandler mHandler;
		private final int mToken;

		LoadMoreTask(BaseListFragment fragment, LoadMoreHandler handler, int token) {
			mFragmentRef = new WeakReference<BaseListFragment>(fragment);
			mHandler = handler;
			mToken = token;
		}

		@Override
		public void run() {
			BaseListFragment fragment = mFragmentRef.get();
			if (fragment == null || mHandler == null) {
				return;
			}
			BaseContentList page = fragment.fetchMorePage();
			if (!fragment.mLoadMoreController.isActive()) {
				// view 已销毁，连消息都不必投递。
				fragment.mLoadMoreController.finishLoad();
				return;
			}
			Message msg = mHandler.obtainMessage(MSG_LOAD_MORE_DONE, page);
			msg.arg1 = mToken;
			mHandler.sendMessage(msg);
		}
	}

}
