package cn.eoe.app;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import cn.eoe.app.db.biz.AppraiseQuery;
import cn.eoe.app.utils.LoginUtils;

/**
 * 详情页本地评价状态“按账号隔离”的回归测试。
 *
 * <p>覆盖两个历史缺陷：
 * <ol>
 *   <li><b>写入：</b>{@code DetailsActivity.onPause()} 的守卫写成
 *       {@code if (mKey.equals(null) && mKey.equals(""))}，两个条件永远不可能同时成立，
 *       于是未登录（空 key）状态也被写进了数据库。</li>
 *   <li><b>读取：</b>{@code DetailsActivity.initAppraise()} 通过
 *       {@code DetailDB.querySQL(mUrl)} 只按文章 url 查询、忽略登录 key，
 *       导致 B 账号甚至匿名用户会读到 A 账号写入的点赞/踩/收藏状态（串号），
 *       随后又把自己的操作覆盖回同一条记录。</li>
 * </ol>
 *
 * <p>修复时把这两处逻辑抽取为不依赖 Android 的纯方法
 * {@link LoginUtils#isLoggedIn(String)} 与 {@link AppraiseQuery#build}，
 * 因此本测试无需 Android 运行环境即可直接验证。
 *
 * <p>本工程是 Eclipse ADT 结构、没有 Gradle/测试框架接入，可用如下命令独立编译运行
 * （JUnit 取自本机 Maven 仓库）：
 * <pre>
 *   JUNIT=~/.m2/repository/junit/junit/4.12/junit-4.12.jar
 *   HAMCREST=~/.m2/repository/org/hamcrest/hamcrest-core/1.3/hamcrest-core-1.3.jar
 *   javac -cp "$JUNIT:$HAMCREST" -sourcepath source/src:source/test -d /tmp/out \
 *         source/test/cn/eoe/app/DetailAppraiseRegressionTest.java
 *   java -cp "/tmp/out:$JUNIT:$HAMCREST" org.junit.runner.JUnitCore \
 *         cn.eoe.app.DetailAppraiseRegressionTest
 * </pre>
 */
public class DetailAppraiseRegressionTest {

    // ---------- 缺陷一：未登录（null / 空 key）不得被当作已登录而落库 ----------

    @Test
    public void notLoggedIn_whenKeyIsNull() {
        assertFalse("null key 必须视为未登录", LoginUtils.isLoggedIn(null));
    }

    @Test
    public void notLoggedIn_whenKeyIsEmpty() {
        // 这正是原 onPause 守卫失效、把空账号状态写进数据库的场景
        assertFalse("空串 key 必须视为未登录", LoginUtils.isLoggedIn(""));
    }

    @Test
    public void loggedIn_whenKeyIsPresent() {
        assertTrue("非空 key 应视为已登录", LoginUtils.isLoggedIn("user-token-abc"));
    }

    // ---------- 缺陷二：查询必须按 (url, key) 联合过滤，保证账号隔离 ----------

    @Test
    public void query_filtersByBothUrlAndKey() {
        String sql = AppraiseQuery.build("detailRecord", "url",
                "http://eoe.cn/a/1", "key", "tokenA");
        assertTrue("查询必须包含 url 过滤", sql.contains("url='http://eoe.cn/a/1'"));
        assertTrue("查询必须包含 key 过滤（账号隔离的关键）", sql.contains("key='tokenA'"));
    }

    @Test
    public void query_differentAccountsProduceDifferentSql() {
        String url = "http://eoe.cn/a/1";
        String sqlA = AppraiseQuery.build("detailRecord", "url", url, "key", "tokenA");
        String sqlB = AppraiseQuery.build("detailRecord", "url", url, "key", "tokenB");
        // 同一篇文章、不同账号必须命中不同的查询条件，否则就会读到别人的状态
        assertFalse("不同账号对同一 url 的查询不应相同", sqlA.equals(sqlB));
    }

    @Test
    public void query_exactStatementForKnownInput() {
        String sql = AppraiseQuery.build("detailRecord", "url", "u", "key", "k");
        assertEquals("select * from detailRecord where url='u' and key='k'", sql);
    }
}
