package system.common;

import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

@RestControllerAdvice
public class GlobalExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusinessException(BusinessException exception) {
        HttpStatus status = exception.getCode() == 404 ? HttpStatus.NOT_FOUND : HttpStatus.BAD_REQUEST;
        return ResponseEntity.status(status)
                .body(ApiResponse.failure(exception.getCode(), exception.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidation(MethodArgumentNotValidException exception) {
        FieldError fieldError = exception.getBindingResult().getFieldErrors().stream()
                .findFirst().orElse(null);
        String message = fieldError == null || fieldError.getDefaultMessage() == null
                ? "请求参数校验失败" : fieldError.getDefaultMessage();
        return ResponseEntity.badRequest().body(ApiResponse.failure(400, message));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleConstraintViolation(ConstraintViolationException exception) {
        String message = exception.getConstraintViolations().stream()
                .findFirst().map(violation -> violation.getMessage()).orElse("请求参数校验失败");
        return ResponseEntity.badRequest().body(ApiResponse.failure(400, message));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> handleUnreadableMessage() {
        return ResponseEntity.badRequest().body(ApiResponse.failure(400, "请求体格式错误"));
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ApiResponse<Void>> handleStatusException(ResponseStatusException exception) {
        int code = exception.getStatusCode().value();
        String message = exception.getReason() == null ? "请求失败" : exception.getReason();
        return ResponseEntity.status(exception.getStatusCode()).body(ApiResponse.failure(code, message));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnexpectedException(Exception exception) {
        log.error("Unhandled request error", exception);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.failure(500, "服务器内部错误"));
    }
}
