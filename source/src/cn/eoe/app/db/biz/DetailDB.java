package cn.eoe.app.db.biz;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import cn.eoe.app.db.DBHelper;
import cn.eoe.app.db.DetailColumn;

public class DetailDB {
	private DBHelper dbHelper;

	public DetailDB(Context context) {
		dbHelper = DBHelper.getInstance(context);
	}

	public void insertSQL(String url, String Key, int good, int bad, int collect) {
		String SQL = "insert into " + DetailColumn.TABLE_NAME + "("
				+ DetailColumn.URL + "," + DetailColumn.KEY + ","
				+ DetailColumn.GOOD + "," + DetailColumn.BAD + ","
				+ DetailColumn.COLLECT + ") values('" + url + "','" + Key
				+ "','" + good + "','" + bad + "','" + collect + "')";
		dbHelper.ExecSQL(SQL);
	}

	/**
	 * 更改评价
	 * 
	 * @param id
	 * @param good
	 * @param bad
	 * @param collect
	 * @return
	 */
	public int updateSQL(int id, int good, int bad, int collect) {
		ContentValues values = new ContentValues();
		values.put(DetailColumn.GOOD, good);
		values.put(DetailColumn.BAD, bad);
		values.put(DetailColumn.COLLECT, collect);
		return dbHelper.update(DetailColumn.TABLE_NAME, values,
				DetailColumn._ID + "=?", new String[] { id + "" });
	}


	public int deleteSQL(int id) {
		return dbHelper.delete(DetailColumn.TABLE_NAME, id);
	}

	/**
	 * 按文章 url + 登录账号 key 查询本地评价记录。
	 *
	 * <p>必须同时带上 key，否则不同账号会读到彼此的点赞/踩/收藏状态（串号）。
	 * 查询语句由 {@link AppraiseQuery#build} 构造，便于在不依赖 Android 的情况下做回归测试。
	 */
	public Cursor querySQL(String url, String key) {
		String SQL = AppraiseQuery.build(DetailColumn.TABLE_NAME, DetailColumn.URL,
				url, DetailColumn.KEY, key);
		return dbHelper.rawQuery(SQL, null);
	}

	public void dbClose() {
		dbHelper.closeDb();
	}
}
