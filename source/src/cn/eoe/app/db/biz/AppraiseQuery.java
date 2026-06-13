package cn.eoe.app.db.biz;

/**
 * 详情页本地评价记录（点赞/踩/收藏）的查询语句构造。
 *
 * <p>本地评价状态必须按 {@code (url, key)} 联合定位，否则不同账号之间会串号：
 * 之前只按 url 查询，会让 B 账号、甚至匿名用户读到 A 账号写入的状态，
 * 随后又把自己的操作覆盖回同一条记录。
 *
 * <p>刻意做成不依赖 Android 的纯方法（表名、列名通过参数传入），
 * 以便在没有 Android 运行环境时也能对“查询是否真的按账号隔离”写回归测试。
 */
public final class AppraiseQuery {

    private AppraiseQuery() {
    }

    /**
     * 构造按文章 url 与登录账号 key 联合过滤的查询语句。
     *
     * @param table  表名
     * @param urlCol url 列名
     * @param url    文章 url
     * @param keyCol key 列名
     * @param key    登录账号 key
     * @return 形如 {@code select * from <table> where <urlCol>='<url>' and <keyCol>='<key>'}
     */
    public static String build(String table, String urlCol, String url,
            String keyCol, String key) {
        return "select * from " + table
                + " where " + urlCol + "='" + url + "'"
                + " and " + keyCol + "='" + key + "'";
    }
}
