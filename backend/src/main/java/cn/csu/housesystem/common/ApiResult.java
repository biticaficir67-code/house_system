package cn.csu.housesystem.common;

public record ApiResult<T>(int code, String msg, T data) {

    public static <T> ApiResult<T> ok(T data) {
        return new ApiResult<>(0, "ok", data);
    }

    public static ApiResult<Void> ok() {
        return new ApiResult<>(0, "ok", null);
    }

    public static ApiResult<Void> error(int code, String message) {
        return new ApiResult<>(code, message, null);
    }
}
