package system.common;

import java.util.Arrays;

public final class Checks {
    private Checks() {}

    public static void require(boolean condition, String message) {
        if (!condition) throw new BusinessException(400, message);
    }

    public static String choice(String value, String... allowed) {
        require(
                value != null && Arrays.asList(allowed).contains(value),
                "字段取值必须为 " + String.join("/", allowed));
        return value;
    }

    public static String search(String value) {
        if (value == null || value.isBlank()) return null;
        require(value.length() <= 128, "搜索词最多 128 字");
        return value.trim();
    }

    public static String password(String value) {
        require(
                value != null
                        && value.length() >= 10
                        && value.getBytes(java.nio.charset.StandardCharsets.UTF_8).length <= 72,
                "密码至少 10 个字符，UTF-8 编码不得超过 72 字节");
        return value;
    }
}
