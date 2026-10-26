package system.common;

import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.validation.Validator;

import org.springframework.stereotype.Component;

/** Reuse the same typed request validation for interactive forms and spreadsheet imports. */
@Component
public class Payloads {
    private final ObjectMapper json;
    private final Validator validator;

    public Payloads(ObjectMapper json, Validator validator) {
        this.json = json;
        this.validator = validator;
    }

    public <T> T read(Object values, Class<T> type) {
        T body;
        try {
            body = json.convertValue(values, type);
        } catch (IllegalArgumentException error) {
            throw new BusinessException(400, "字段名、数字或日期格式不正确，请核对模板");
        }
        var errors = validator.validate(body);
        if (!errors.isEmpty()) {
            var first =
                    errors.stream()
                            .sorted(
                                    java.util.Comparator.comparing(
                                            e -> e.getPropertyPath().toString()))
                            .findFirst()
                            .orElseThrow();
            throw new BusinessException(400, first.getPropertyPath() + "：" + first.getMessage());
        }
        return body;
    }
}
