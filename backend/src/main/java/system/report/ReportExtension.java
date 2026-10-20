package system.report;

import java.util.Map;
import java.util.Optional;

/**
 * C may implement this bean. Aggregate the supplied scopedSource as a subquery; all values are
 * bound.
 */
public interface ReportExtension {
    Optional<Object> query(
            String kind,
            ReportService.Filter filter,
            String scopedSource,
            Map<String, Object> parameters);
}
