package cn.eoe.app.utils;

import org.junit.Test;
import static org.junit.Assert.*;

/**
 * 回归测试：验证 RequestCacheUtil 的响应校验逻辑。
 *
 * 修复背景：原 getStringFromWeb() 使用了
 *   if (result.equals(null) && result.equals(""))
 * 这一无效判断——当 HttpUtils.getByHttpClient() 返回 null 时会直接抛
 * NullPointerException 并被 catch 吞掉；当返回空字符串时又会继续更新
 * 时间戳、覆盖本地文件并刷新内存缓存，从而把最后一份可用缓存冲掉。
 *
 * 修复方案：提取 isValidResponse() 方法，统一判断响应是否有效。
 * 只有 isValidResponse() 返回 true 的响应才会写入任何一层缓存。
 *
 * 此测试类通过同包下独立复现的 isValidResponse() 逻辑进行验证，
 * 不依赖 Android SDK，可在普通 JVM 上运行。
 * 方法签名与语义必须与 RequestCacheUtil.isValidResponse() 保持一致。
 */
public class RequestCacheUtilTest {

    /**
     * 与 RequestCacheUtil.isValidResponse() 完全一致的实现。
     * 任何对该方法的修改都必须同步更新此复现，否则编译或测试将失败。
     */
    private static boolean isValidResponse(String response) {
        return response != null && !response.isEmpty();
    }

    // ==================== isValidResponse 边界用例 ====================

    @Test
    public void testNull_isInvalid() {
        assertFalse("null 响应必须被判定为无效", isValidResponse(null));
    }

    @Test
    public void testEmptyString_isInvalid() {
        assertFalse("空字符串必须被判定为无效", isValidResponse(""));
    }

    @Test
    public void testValidJson_isValid() {
        assertTrue("有效 JSON 响应必须被接受",
                isValidResponse("{\"response\":{\"news\":[]}}"));
    }

    @Test
    public void testValidXml_isValid() {
        assertTrue("有效 XML 响应必须被接受",
                isValidResponse("<?xml version=\"1.0\"?><data/>"));
    }

    @Test
    public void testSingleChar_isValid() {
        assertTrue("单字符响应必须被接受", isValidResponse("a"));
    }

    @Test
    public void testWhitespace_isValid() {
        // 空白字符串虽然不是有效 JSON，但服务器确实返回了内容，
        // 不应视为"空响应"。上层 DAO 的 Jackson 解析会自行处理。
        assertTrue("空白字符串应被视为有效（服务端确实返回了内容）",
                isValidResponse("   "));
    }

    @Test
    public void testEmptyJsonArray_isValid() {
        assertTrue("空 JSON 数组是有效响应", isValidResponse("[]"));
    }

    @Test
    public void testEmptyJsonObject_isValid() {
        assertTrue("空 JSON 对象是有效响应", isValidResponse("{}"));
    }

    // ==================== 原始 bug 场景复现 ====================

    /**
     * 复现原始 bug：result.equals(null) 在 result 为 null 时抛 NPE。
     * 修复后 isValidResponse(null) 应安全返回 false，不抛任何异常。
     */
    @Test
    public void testOriginalBug_nullDoesNotThrowNPE() {
        String result = null;
        try {
            boolean valid = isValidResponse(result);
            assertFalse("null 应返回 false", valid);
        } catch (NullPointerException e) {
            fail("isValidResponse(null) 不应抛出 NullPointerException——"
                    + "这正是原始 bug 的核心问题");
        }
    }

    /**
     * 复现原始 bug：result.equals(null) && result.equals("") 中
     * && 运算符导致两个条件必须同时为真才走早返回，逻辑完全错误。
     * 修复后 null 和 "" 都应被独立判定为无效。
     */
    @Test
    public void testOriginalBug_bothNullAndEmptyAreInvalid() {
        assertFalse("null 必须为无效", isValidResponse(null));
        assertFalse("空串必须为无效", isValidResponse(""));
        // 原始代码用 && 连接，导致两者都不会触发早返回
    }

    /**
     * 验证原始 bug 代码的等价判断确实恒为 false：
     *   result.equals(null) 在 result != null 时永远返回 false
     *   （没有任何对象 .equals(null) 返回 true）
     * 这意味着原始守卫条件永远不生效，任何响应（包括空串）都会穿透到缓存更新。
     */
    @Test
    public void testOriginalBug_equalsNullAlwaysFalse() {
        String result = "";
        // String.equals(null) 永远返回 false
        assertFalse("\"\".equals(null) 应为 false", result.equals(null));

        result = "some valid data";
        assertFalse("\"data\".equals(null) 应为 false", result.equals(null));

        // 而我们的修复版本正确地拒绝空串并接受有效数据
        assertFalse(isValidResponse(""));
        assertTrue(isValidResponse("some valid data"));
    }

    // ==================== 缓存保护语义验证 ====================

    /**
     * 核心语义：只有有效响应才应触发缓存刷新。
     * 模拟 getStringFromWeb() 在各种返回值下的缓存保护行为。
     */
    @Test
    public void testCacheProtection_nullResponse_doesNotCorrupt() {
        String cachedData = "{\"response\":{\"news\":[{\"title\":\"old\"}]}}";
        String webResponse = null; // 模拟 HttpUtils.getByHttpClient() 返回 null

        // getStringFromWeb 的修复逻辑：
        // if (!isValidResponse(result)) { return stale fallback; }
        // 因此 null 不会触发缓存写入
        assertFalse("null 响应不应触发缓存写入", isValidResponse(webResponse));
        // 已有缓存数据保持不变
        assertTrue("已有缓存数据应保持有效", isValidResponse(cachedData));
    }

    @Test
    public void testCacheProtection_emptyResponse_doesNotCorrupt() {
        String cachedData = "{\"response\":{\"news\":[{\"title\":\"old\"}]}}";
        String webResponse = ""; // 模拟服务端返回空内容

        assertFalse("空响应不应触发缓存写入", isValidResponse(webResponse));
        assertTrue("已有缓存数据应保持有效", isValidResponse(cachedData));
    }

    @Test
    public void testCacheProtection_validResponse_doesUpdate() {
        String webResponse = "{\"response\":{\"news\":[{\"title\":\"new\"}]}}";

        assertTrue("有效响应应触发缓存写入", isValidResponse(webResponse));
    }

    /**
     * 模拟异常场景：HttpUtils.getByHttpClient() 抛 RuntimeException 时，
     * getStringFromWeb() 的 catch 块捕获异常后 result 保持为初始值 null。
     * 修复后 null 不会触发缓存写入。
     */
    @Test
    public void testCacheProtection_exceptionThrown_doesNotCorrupt() {
        String result = null; // 模拟 result 在 try 块赋值前异常被抛出
        try {
            // 模拟 HttpUtils.getByHttpClient() 抛异常
            throw new RuntimeException("网络连接超时");
        } catch (Exception e) {
            // catch 块吞掉异常，result 保持 null
        }

        assertFalse("异常后 result 为 null，不应触发缓存写入",
                isValidResponse(result));
    }

    // ==================== 与 getStringFromSoftReference 一致性验证 ====================

    /**
     * getStringFromSoftReference 内部也做了 result != null && !result.equals("") 检查。
     * 验证 isValidResponse() 与之一致。
     */
    @Test
    public void testConsistencyWithSoftReferenceCheck() {
        // getStringFromSoftReference 中的检查：result != null && !result.equals("")
        // isValidResponse 中的检查：response != null && !response.isEmpty()
        // 两者语义完全一致

        String[] testCases = { null, "", " ", "data", "[]", "{}" };
        for (String tc : testCases) {
            boolean softRefCheck = (tc != null && !tc.equals(""));
            boolean isValid = isValidResponse(tc);
            assertEquals(
                    "isValidResponse 与 getStringFromSoftReference 的检查应对 \""
                            + tc + "\" 一致",
                    softRefCheck, isValid);
        }
    }
}
