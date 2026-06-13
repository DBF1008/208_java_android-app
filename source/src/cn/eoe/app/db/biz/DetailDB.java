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

	/**
	 * 插入评价记录（使用 ContentValues 防止 SQL 注入）
	 *
	 * @return 新行 ID，失败或 UNIQUE 冲突时返回 -1
	 */
	public long insertSQL(String url, String key, int good, int bad, int collect) {
		ContentValues values = new ContentValues();
		values.put(DetailColumn.URL, url);
		values.put(DetailColumn.KEY, key);
		values.put(DetailColumn.GOOD, good);
		values.put(DetailColumn.BAD, bad);
		values.put(DetailColumn.COLLECT, collect);
		return dbHelper.insert(DetailColumn.TABLE_NAME, values);
	}

	/**
	 * 更改评价（按 ID + key 双重匹配，防止跨账号误改）
	 *
	 * @param id      记录 ID
	 * @param key     当前用户 key
	 * @param good    点赞状态
	 * @param bad     踩状态
	 * @param collect 收藏状态
	 * @return 受影响行数
	 */
	public int updateSQL(int id, String key, int good, int bad, int collect) {
		ContentValues values = new ContentValues();
		values.put(DetailColumn.GOOD, good);
		values.put(DetailColumn.BAD, bad);
		values.put(DetailColumn.COLLECT, collect);
		return dbHelper.update(DetailColumn.TABLE_NAME, values,
				DetailColumn._ID + "=? AND " + DetailColumn.KEY + "=?",
				new String[] { String.valueOf(id), key });
	}


	public int deleteSQL(int id) {
		return dbHelper.delete(DetailColumn.TABLE_NAME, id);
	}

	/**
	 * 按 URL + key 查询当前用户的评价记录（参数化查询，防止 SQL 注入）
	 *
	 * @param url 文章 URL
	 * @param key 当前用户 key
	 * @return Cursor（调用方需负责关闭）
	 */
	public Cursor querySQL(String url, String key) {
		return dbHelper.query(DetailColumn.TABLE_NAME, null,
				DetailColumn.URL + "=? AND " + DetailColumn.KEY + "=?",
				new String[] { url, key });
	}

	/**
	 * @deprecated 仅按 URL 查询，会导致跨账号状态污染。请使用 {@link #querySQL(String, String)}
	 */
	@Deprecated
	public Cursor querySQL(String url) {
		String SQL = "select * from " + DetailColumn.TABLE_NAME + " where " + DetailColumn.URL + "='" + url + "'";
		return dbHelper.rawQuery(SQL, null);
	}

	public void dbClose() {
		dbHelper.closeDb();
	}
}
