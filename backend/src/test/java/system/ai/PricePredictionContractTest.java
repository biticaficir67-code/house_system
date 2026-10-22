package system.ai;

import static org.assertj.core.api.Assertions.*;

import java.io.IOException;
import java.util.*;
import org.junit.jupiter.api.Test;

class PricePredictionContractTest {
    private Map<String, Object> result() {
        return new HashMap<>(Map.ofEntries(
                Map.entry("status", "DEMO_ONLY"), Map.entry("usage", "HISTORICAL_DEMO"),
                Map.entry("price_basis", "LISTING_UNVERIFIED"), Map.entry("model_version", "v1"),
                Map.entry("data_version", "data-v1"), Map.entry("generated_at", "2026-10-22T12:00:00+08:00"),
                Map.entry("quality_flags", List.of("HISTORICAL_DATA")),
                Map.entry("explanations", List.of("Historical reference")),
                Map.entry("estimated_unit_price_yuan_sqm", 40000),
                Map.entry("estimated_total_price_yuan", 4000000),
                Map.entry("comparable_sample_count", 20),
                Map.entry("price_interval", Map.of("lower_total_price_yuan", 3000000,
                        "upper_total_price_yuan", 5000000, "nominal_coverage", 0.9))));
    }

    @Test void acceptsHistoricalReference() {
        assertThatCode(() -> PricePredictionContract.validate(result())).doesNotThrowAnyException();
    }

    @Test void unavailableMustNotContainPrices() {
        var r = result(); r.put("status", "UNAVAILABLE");
        assertThatThrownBy(() -> PricePredictionContract.validate(r)).isInstanceOf(IOException.class);
        r.put("estimated_unit_price_yuan_sqm", null); r.put("estimated_total_price_yuan", null);
        r.put("price_interval", null);
        assertThatCode(() -> PricePredictionContract.validate(r)).doesNotThrowAnyException();
    }

    @Test void rejectsMalformedMetadataAndPrices() {
        for (Object bad : Arrays.asList(null, "40000", Double.NaN, Double.POSITIVE_INFINITY, -1, 0)) {
            var r = result(); r.put("estimated_unit_price_yuan_sqm", bad);
            assertThatThrownBy(() -> PricePredictionContract.validate(r)).isInstanceOf(IOException.class);
        }
        for (String key : List.of("model_version", "data_version", "generated_at")) {
            var r = result(); r.put(key, " ");
            assertThatThrownBy(() -> PricePredictionContract.validate(r)).isInstanceOf(IOException.class);
        }
        var r = result(); r.put("quality_flags", "not a list");
        assertThatThrownBy(() -> PricePredictionContract.validate(r)).isInstanceOf(IOException.class);
        var invalidCount = result(); invalidCount.put("comparable_sample_count", 1.5);
        assertThatThrownBy(() -> PricePredictionContract.validate(invalidCount)).isInstanceOf(IOException.class);
    }

    @Test void rejectsInvalidIntervalsAndCoverage() {
        for (double coverage : new double[] {0, 1, -0.1, Double.NaN}) {
            var r = result(); r.put("price_interval", Map.of("lower_total_price_yuan", 3000000,
                    "upper_total_price_yuan", 5000000, "nominal_coverage", coverage));
            assertThatThrownBy(() -> PricePredictionContract.validate(r)).isInstanceOf(IOException.class);
        }
        var r = result(); r.put("price_interval", Map.of("lower_total_price_yuan", 4500000,
                "upper_total_price_yuan", 5000000, "nominal_coverage", 0.9));
        assertThatThrownBy(() -> PricePredictionContract.validate(r)).isInstanceOf(IOException.class);
    }
}
