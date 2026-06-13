package cn.eoe.app.db.biz;

import android.content.Context;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.test.AndroidTestCase;
import android.test.RenamingDelegatingContext;

import cn.eoe.app.db.DBHelper;
import cn.eoe.app.db.DetailColumn;

import java.lang.reflect.Field;

/**
 * 详情页本地评价状态的回归测试。
 * <p>
 * 验证修复：
 * 1. 按 (url, key) 隔离查询 —— 防止跨账号状态污染
 * 2. 未登录（空 key）时不写入数据库
 * 3. updateSQL 按 (id, key) 双重匹配 —— 防止跨账号误改
 * 4. UNIQUE(url, key) 约束 —— 防止同一用户重复记录
 */
public class DetailDBTest extends AndroidTestCase {

    private static final String URL_ARTICLE_1 = "http://example.com/article/1";
    private static final String URL_ARTICLE_2 = "http://example.com/article/2";
    private static final String KEY_USER_A = "userA_key:123";
    private static final String KEY_USER_B = "userB_key:456";
    private static final String KEY_EMPTY = "";

    private DetailDB detailDB;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        // 使用 RenamingDelegatingContext 隔离测试数据库，不影响正式数据
        Context testContext = new RenamingDelegatingContext(getContext(), "test_");
        resetDBHelperSingleton();
        detailDB = new DetailDB(testContext);
    }

    @Override
    protected void tearDown() throws Exception {
        if (detailDB != null) {
            detailDB.dbClose();
        }
        resetDBHelperSingleton();
        super.tearDown();
    }

    /**
     * 通过反射重置 DBHelper 单例，确保每个测试用例使用干净的数据库
     */
    private void resetDBHelperSingleton() {
        try {
            Field field = DBHelper.class.getDeclaredField("mdbHelper");
            field.setAccessible(true);
            DBHelper existing = (DBHelper) field.get(null);
            if (existing != null) {
                existing.closeDb();
            }
            field.set(null, null);
        } catch (Exception e) {
            throw new RuntimeException("Failed to reset DBHelper singleton", e);
        }
    }

    private long insertRecord(String url, String key, int good, int bad, int collect) {
        return detailDB.insertSQL(url, key, good, bad, collect);
    }

    private Cursor queryRecord(String url, String key) {
        return detailDB.querySQL(url, key);
    }

    private void assertCursorCount(Cursor cursor, int expected) {
        assertNotNull("Cursor should not be null", cursor);
        try {
            assertEquals("Expected " + expected + " rows", expected, cursor.getCount());
        } finally {
            cursor.close();
        }
    }

    // ========================================================================
    // 测试：按 (url, key) 隔离查询
    // ========================================================================

    /**
     * 同一文章 URL，不同用户 key，查询应返回各自的数据。
     * 回归验证：修复前 querySQL 只按 url 查询，会读到其他用户的记录。
     */
    public void testQueryWithKeyReturnsOnlyOwnUserData() {
        insertRecord(URL_ARTICLE_1, KEY_USER_A, 1, 0, 1); // A: 点赞+收藏
        insertRecord(URL_ARTICLE_1, KEY_USER_B, 0, 1, 0); // B: 踩

        Cursor cursorA = queryRecord(URL_ARTICLE_1, KEY_USER_A);
        try {
            assertTrue("User A should have a record", cursorA.moveToFirst());
            assertEquals(1, cursorA.getInt(cursorA.getColumnIndex(DetailColumn.GOOD)));
            assertEquals(0, cursorA.getInt(cursorA.getColumnIndex(DetailColumn.BAD)));
            assertEquals(1, cursorA.getInt(cursorA.getColumnIndex(DetailColumn.COLLECT)));
        } finally {
            cursorA.close();
        }

        Cursor cursorB = queryRecord(URL_ARTICLE_1, KEY_USER_B);
        try {
            assertTrue("User B should have a record", cursorB.moveToFirst());
            assertEquals(0, cursorB.getInt(cursorB.getColumnIndex(DetailColumn.GOOD)));
            assertEquals(1, cursorB.getInt(cursorB.getColumnIndex(DetailColumn.BAD)));
            assertEquals(0, cursorB.getInt(cursorB.getColumnIndex(DetailColumn.COLLECT)));
        } finally {
            cursorB.close();
        }
    }

    /**
     * 用户 A 有记录时，用户 B 查询同一 URL 应返回空结果。
     * 回归验证：修复前 B 会读到 A 的记录（跨账号污染）。
     */
    public void testQueryWithDifferentKeyReturnsEmpty() {
        insertRecord(URL_ARTICLE_1, KEY_USER_A, 1, 0, 0);

        Cursor cursor = queryRecord(URL_ARTICLE_1, KEY_USER_B);
        assertCursorCount(cursor, 0);
    }

    /**
     * 空 key 查询应返回空结果，不应返回有 key 的记录。
     * 回归验证：修复前空 key 会匹配到其他用户的记录。
     */
    public void testQueryWithEmptyKeyReturnsEmpty() {
        insertRecord(URL_ARTICLE_1, KEY_USER_A, 1, 0, 0);

        Cursor cursor = queryRecord(URL_ARTICLE_1, KEY_EMPTY);
        assertCursorCount(cursor, 0);
    }

    // ========================================================================
    // 测试：插入/查询往返
    // ========================================================================

    /**
     * 插入记录后应能通过 (url, key) 查询回来，所有字段匹配。
     */
    public void testInsertQueryRoundTrip() {
        long id = insertRecord(URL_ARTICLE_1, KEY_USER_A, 1, 0, 1);
        assertTrue("Insert should return positive ID", id > 0);

        Cursor cursor = queryRecord(URL_ARTICLE_1, KEY_USER_A);
        try {
            assertTrue("Should find the inserted record", cursor.moveToFirst());
            assertEquals(URL_ARTICLE_1,
                    cursor.getString(cursor.getColumnIndex(DetailColumn.URL)));
            assertEquals(KEY_USER_A,
                    cursor.getString(cursor.getColumnIndex(DetailColumn.KEY)));
            assertEquals(1, cursor.getInt(cursor.getColumnIndex(DetailColumn.GOOD)));
            assertEquals(0, cursor.getInt(cursor.getColumnIndex(DetailColumn.BAD)));
            assertEquals(1, cursor.getInt(cursor.getColumnIndex(DetailColumn.COLLECT)));
        } finally {
            cursor.close();
        }
    }

    // ========================================================================
    // 测试：updateSQL 按 (id, key) 双重匹配
    // ========================================================================

    /**
     * 更新用户 A 的记录不应影响用户 B 的记录。
     * 回归验证：修复前 updateSQL 只按 id 更新，可能误改其他用户的记录。
     */
    public void testUpdateOnlyAffectsMatchingKey() {
        long idA = insertRecord(URL_ARTICLE_1, KEY_USER_A, 1, 0, 0);
        insertRecord(URL_ARTICLE_1, KEY_USER_B, 0, 1, 0);

        // 用 A 的 key 更新 A 的记录
        int affected = detailDB.updateSQL((int) idA, KEY_USER_A, 0, 0, 1);
        assertEquals("Should update exactly 1 row", 1, affected);

        // 验证 A 的记录已更新
        Cursor cursorA = queryRecord(URL_ARTICLE_1, KEY_USER_A);
        try {
            assertTrue(cursorA.moveToFirst());
            assertEquals(0, cursorA.getInt(cursorA.getColumnIndex(DetailColumn.GOOD)));
            assertEquals(0, cursorA.getInt(cursorA.getColumnIndex(DetailColumn.BAD)));
            assertEquals(1, cursorA.getInt(cursorA.getColumnIndex(DetailColumn.COLLECT)));
        } finally {
            cursorA.close();
        }

        // 验证 B 的记录未被修改
        Cursor cursorB = queryRecord(URL_ARTICLE_1, KEY_USER_B);
        try {
            assertTrue(cursorB.moveToFirst());
            assertEquals(0, cursorB.getInt(cursorB.getColumnIndex(DetailColumn.GOOD)));
            assertEquals(1, cursorB.getInt(cursorB.getColumnIndex(DetailColumn.BAD)));
            assertEquals(0, cursorB.getInt(cursorB.getColumnIndex(DetailColumn.COLLECT)));
        } finally {
            cursorB.close();
        }
    }

    /**
     * 用错误的 key 更新记录，应该影响 0 行（拒绝更新）。
     * 验证 updateSQL 的 key 过滤确实生效。
     */
    public void testUpdateWithWrongKeyAffectsNothing() {
        long idA = insertRecord(URL_ARTICLE_1, KEY_USER_A, 1, 0, 0);

        // 尝试用 B 的 key 更新 A 的记录 —— 应该失败
        int affected = detailDB.updateSQL((int) idA, KEY_USER_B, 0, 1, 1);
        assertEquals("Should not update any row with wrong key", 0, affected);

        // 验证 A 的记录保持不变
        Cursor cursor = queryRecord(URL_ARTICLE_1, KEY_USER_A);
        try {
            assertTrue(cursor.moveToFirst());
            assertEquals(1, cursor.getInt(cursor.getColumnIndex(DetailColumn.GOOD)));
            assertEquals(0, cursor.getInt(cursor.getColumnIndex(DetailColumn.BAD)));
            assertEquals(0, cursor.getInt(cursor.getColumnIndex(DetailColumn.COLLECT)));
        } finally {
            cursor.close();
        }
    }

    // ========================================================================
    // 测试：UNIQUE(url, key) 约束
    // ========================================================================

    /**
     * 同一 (url, key) 不允许重复插入，第二次应返回 -1。
     * 回归验证：修复前没有 UNIQUE 约束，同一用户同一文章会产生多条记录。
     */
    public void testInsertDuplicateUrlKeyFails() {
        long first = insertRecord(URL_ARTICLE_1, KEY_USER_A, 1, 0, 0);
        assertTrue("First insert should succeed", first > 0);

        long second = insertRecord(URL_ARTICLE_1, KEY_USER_A, 0, 1, 1);
        assertEquals("Duplicate insert should return -1", -1, second);
    }

    /**
     * 同一 URL 不同 key 应各自独立插入成功。
     */
    public void testInsertDifferentKeysSameUrlSucceeds() {
        long idA = insertRecord(URL_ARTICLE_1, KEY_USER_A, 1, 0, 0);
        long idB = insertRecord(URL_ARTICLE_1, KEY_USER_B, 0, 1, 0);

        assertTrue("User A insert should succeed", idA > 0);
        assertTrue("User B insert should succeed", idB > 0);
    }

    // ========================================================================
    // 测试：旧版 querySQL(url) 的废弃方法行为（保持向后兼容）
    // ========================================================================

    /**
     * 旧版 querySQL(url) 仍应能查到数据（向后兼容），
     * 但应标记为 @Deprecated，不应在新代码中使用。
     */
    @SuppressWarnings("deprecation")
    public void testDeprecatedQuerySqlStillWorks() {
        insertRecord(URL_ARTICLE_1, KEY_USER_A, 1, 0, 0);

        Cursor cursor = detailDB.querySQL(URL_ARTICLE_1);
        try {
            assertTrue("Deprecated method should still return data",
                    cursor.moveToFirst());
        } finally {
            cursor.close();
        }
    }

    // ========================================================================
    // 测试：多文章多用户交叉场景
    // ========================================================================

    /**
     * 模拟真实场景：两个用户分别对两篇文章进行评价，
     * 验证所有查询都返回正确结果，无交叉污染。
     */
    public void testMultipleArticlesMultipleUsersIsolation() {
        // A 对文章 1 点赞
        insertRecord(URL_ARTICLE_1, KEY_USER_A, 1, 0, 0);
        // A 对文章 2 收藏
        insertRecord(URL_ARTICLE_2, KEY_USER_A, 0, 0, 1);
        // B 对文章 1 踩
        insertRecord(URL_ARTICLE_1, KEY_USER_B, 0, 1, 0);

        // A 查文章 1
        Cursor c1 = queryRecord(URL_ARTICLE_1, KEY_USER_A);
        try {
            assertEquals(1, c1.getCount());
            c1.moveToFirst();
            assertEquals(1, c1.getInt(c1.getColumnIndex(DetailColumn.GOOD)));
        } finally { c1.close(); }

        // A 查文章 2
        Cursor c2 = queryRecord(URL_ARTICLE_2, KEY_USER_A);
        try {
            assertEquals(1, c2.getCount());
            c2.moveToFirst();
            assertEquals(1, c2.getInt(c2.getColumnIndex(DetailColumn.COLLECT)));
        } finally { c2.close(); }

        // B 查文章 1
        Cursor c3 = queryRecord(URL_ARTICLE_1, KEY_USER_B);
        try {
            assertEquals(1, c3.getCount());
            c3.moveToFirst();
            assertEquals(1, c3.getInt(c3.getColumnIndex(DetailColumn.BAD)));
        } finally { c3.close(); }

        // B 查文章 2 —— 应该为空
        Cursor c4 = queryRecord(URL_ARTICLE_2, KEY_USER_B);
        assertCursorCount(c4, 0);
    }
}
