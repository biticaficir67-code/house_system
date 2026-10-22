package system.ai;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/** 模型结果通过协议校验后才允许落库。 */
final class PricePredictionContract {
    private PricePredictionContract() {}

    static void validate(Map<String, Object> result) throws IOException {
        Object status = result.get("status");
        if (!("DEMO_ONLY".equals(status) || "UNAVAILABLE".equals(status))
                || !"HISTORICAL_DEMO".equals(result.get("usage"))
                || !"LISTING_UNVERIFIED".equals(result.get("price_basis")))
            throw new IOException("Invalid price result contract");
        for (String key : List.of("model_version", "data_version", "generated_at")) {
            if (!(result.get(key) instanceof String value) || value.isBlank())
                throw new IOException("Missing price metadata: " + key);
        }
        for (String key : List.of("quality_flags", "explanations")) {
            if (!(result.get(key) instanceof List<?> values)
                    || values.stream().anyMatch(value -> !(value instanceof String)))
                throw new IOException("Invalid price list: " + key);
        }
        if (result.get("comparable_sample_count") != null)
            count(result.get("comparable_sample_count"));
        if ("UNAVAILABLE".equals(status)) {
            if (result.get("estimated_unit_price_yuan_sqm") != null
                    || result.get("estimated_total_price_yuan") != null
                    || result.get("price_interval") != null)
                throw new IOException("Unavailable prices must be null");
            return;
        }
        positive(result.get("estimated_unit_price_yuan_sqm"));
        BigDecimal total = positive(result.get("estimated_total_price_yuan"));
        if (!(result.get("price_interval") instanceof Map<?, ?> interval))
            throw new IOException("Missing price interval");
        BigDecimal lower = positive(interval.get("lower_total_price_yuan"));
        BigDecimal upper = positive(interval.get("upper_total_price_yuan"));
        if (lower.compareTo(total) > 0 || upper.compareTo(total) < 0)
            throw new IOException("Invalid price interval");
        BigDecimal coverage = positive(interval.get("nominal_coverage"));
        if (coverage.compareTo(BigDecimal.ONE) >= 0)
            throw new IOException("Invalid nominal coverage");
        if (interval.get("calibration_sample_count") != null)
            count(interval.get("calibration_sample_count"));
    }

    private static BigDecimal number(Object value) throws IOException {
        if (!(value instanceof Number)) throw new IOException("Price field must be numeric");
        try {
            return new BigDecimal(value.toString());
        } catch (NumberFormatException ex) {
            throw new IOException("Price field must be finite", ex);
        }
    }

    private static BigDecimal positive(Object value) throws IOException {
        BigDecimal result = number(value);
        if (result.signum() <= 0) throw new IOException("Price field must be positive");
        return result;
    }

    private static void count(Object value) throws IOException {
        BigDecimal result = number(value);
        if (result.signum() < 0 || result.stripTrailingZeros().scale() > 0)
            throw new IOException("Sample count must be a nonnegative integer");
    }
}
