package system.customer;

import static system.common.DataStore.*;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import system.common.*;
import system.security.AccessScope;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

/** Explainable preference matching. Scores describe supplied criteria, never sale probability. */
@Service
public class CustomerMatching {
    private final DataStore db;
    private final ResourceAccess access;
    private final AccessScope scope;

    public CustomerMatching(DataStore db, ResourceAccess access, AccessScope scope) {
        this.db = db;
        this.access = access;
        this.scope = scope;
    }

    private static BigDecimal number(Object value) {
        return value == null ? null : new BigDecimal(value.toString());
    }

    private static String district(Object value) {
        return Objects.toString(value, "").trim().replaceFirst("[区县]$", "");
    }

    private static boolean configured(Map<String, Object> demand, String lower, String upper) {
        return demand.get(lower) != null || demand.get(upper) != null;
    }

    private Map<String, Object> interval(
            String key,
            String label,
            Object value,
            Object lower,
            Object upper,
            String unit,
            double weight) {
        BigDecimal actual = number(value), min = number(lower), max = number(upper);
        boolean known = actual != null && actual.signum() > 0;
        BigDecimal difference =
                !known
                        ? null
                        : min != null && actual.compareTo(min) < 0
                                ? min.subtract(actual)
                                : max != null && actual.compareTo(max) > 0
                                        ? actual.subtract(max)
                                        : BigDecimal.ZERO;
        boolean matched = known && difference.signum() == 0;
        double factor = matched ? 1 : 0;
        if (known && !matched) {
            double reference = Math.max(1, (max != null ? max : min).doubleValue());
            factor = Math.max(0, 1 - difference.doubleValue() / (reference * 0.25));
        }
        String expected = Objects.toString(min, "不限") + "～" + Objects.toString(max, "不限") + unit;
        String differenceText =
                !known
                        ? "房源未提供该项资料，需确认"
                        : matched
                                ? "符合范围"
                                : "与需求边界相差 "
                                        + difference.stripTrailingZeros().toPlainString()
                                        + unit;
        return args(
                "key",
                key,
                "label",
                label,
                "expected",
                expected,
                "actual",
                actual,
                "unit",
                unit,
                "known",
                known,
                "matched",
                matched,
                "factor",
                factor,
                "weight",
                weight,
                "difference",
                difference,
                "explanation",
                differenceText);
    }

    private Map<String, Object> exact(
            String key,
            String label,
            String expected,
            Object actual,
            Boolean match,
            double weight) {
        boolean known = match != null;
        return args(
                "key",
                key,
                "label",
                label,
                "expected",
                expected,
                "actual",
                actual,
                "known",
                known,
                "matched",
                Boolean.TRUE.equals(match),
                "factor",
                Boolean.TRUE.equals(match) ? 1.0 : 0.0,
                "weight",
                weight,
                "explanation",
                !known ? "房源资料待补充，需确认" : match ? "符合偏好" : "与偏好不同，需协商");
    }

    @Transactional(readOnly = true)
    public Map<String, Object> recommend(long customerId, int limit, String mode, String priority) {
        Checks.require(limit >= 1 && limit <= 100, "limit 范围为1～100");
        Checks.choice(mode, "STRICT", "FLEXIBLE");
        Checks.choice(priority, "BALANCED", "BUDGET", "SPACE");
        var customer = access.get("customer", customerId, false);
        var demand =
                db.optional(
                                "SELECT * FROM customer_demand WHERE customer_id=#{p.id}",
                                args("id", customerId))
                        .orElse(Map.of());
        List<?> districts = demand.get("districts") instanceof List<?> list ? list : List.of();
        boolean area = configured(demand, "minArea", "maxArea");
        boolean budget = configured(demand, "minPrice", "maxPrice");
        boolean rooms = configured(demand, "minRooms", "maxRooms");
        boolean decoration =
                demand.get("decoration") != null && !"UNKNOWN".equals(demand.get("decoration"));
        boolean elevator = customer.get("tags") instanceof List<?> tags && tags.contains("有电梯");
        int criteriaCount =
                (districts.isEmpty() ? 0 : 1)
                        + (area ? 1 : 0)
                        + (budget ? 1 : 0)
                        + (rooms ? 1 : 0)
                        + (decoration ? 1 : 0)
                        + (elevator ? 1 : 0);
        var response =
                args(
                        "customerId",
                        customerId,
                        "customerStatus",
                        customer.get("status"),
                        "demandConfigured",
                        criteriaCount > 0,
                        "criteriaCount",
                        criteriaCount,
                        "mode",
                        mode,
                        "priority",
                        priority,
                        "scoreBasis",
                        "按已填写条件计算规则符合度；不代表成交概率；未填条件不参与评分",
                        "demand",
                        demand,
                        "candidateCount",
                        0,
                        "strictCount",
                        0,
                        "informationMissingCount",
                        0,
                        "truncated",
                        false,
                        "records",
                        List.of());
        if (criteriaCount == 0) return response;

        var p = args();
        String where = scope.sql("h", p) + " AND h.store_id=#{p.customerStore}";
        p.put("customerStore", customer.get("storeId"));
        var candidates =
                db.list(
                        "SELECT"
                            + " h.id,h.community_name,h.city,h.district,h.building_area_sqm,h.rooms,h.halls,h.bathrooms,"
                            + "h.listing_price_yuan,h.decoration,h.has_elevator,h.floor_level,h.total_floors,h.orientation,h.version,h.store_id,h.broker_id,(SELECT"
                            + " m.id FROM house_media m WHERE m.house_id=h.id AND m.deleted=0 AND"
                            + " m.media_type='IMAGE' ORDER BY m.is_cover DESC,m.sort_order,m.id"
                            + " LIMIT 1) AS cover_media_id FROM house h JOIN store s ON"
                            + " s.id=h.store_id AND s.enabled=1 JOIN broker b ON b.id=h.broker_id"
                            + " AND b.store_id=h.store_id AND b.audit_status=1 AND"
                            + " b.account_status=1 AND TRIM(b.username)<>'' AND"
                            + " TRIM(b.password)<>'' WHERE h.deleted=0 AND h.status='ON_SALE' AND"
                            + " h.verification_status='VERIFIED' AND EXISTS(SELECT 1 FROM sys_role"
                            + " WHERE code='BROKER' AND enabled=1) AND "
                                + where
                                + " ORDER BY h.id DESC LIMIT 5001",
                        p);
        response.put("truncated", candidates.size() > 5000);
        if (candidates.size() > 5000) candidates = candidates.subList(0, 5000);
        double budgetWeight = priority.equals("BUDGET") ? 50 : priority.equals("SPACE") ? 20 : 30;
        double areaWeight = priority.equals("SPACE") ? 40 : priority.equals("BUDGET") ? 15 : 25;
        double districtWeight = priority.equals("BALANCED") ? 25 : 20;
        var scored = new ArrayList<Map<String, Object>>();
        int strictCount = 0, missingCount = 0;
        for (var row : candidates) {
            var details = new ArrayList<Map<String, Object>>();
            if (budget)
                details.add(
                        interval(
                                "budget",
                                "挂牌总价",
                                row.get("listingPriceYuan"),
                                demand.get("minPrice"),
                                demand.get("maxPrice"),
                                "元",
                                budgetWeight));
            if (area)
                details.add(
                        interval(
                                "area",
                                "建筑面积",
                                row.get("buildingAreaSqm"),
                                demand.get("minArea"),
                                demand.get("maxArea"),
                                "㎡",
                                areaWeight));
            if (rooms)
                details.add(
                        interval(
                                "rooms",
                                "室数",
                                row.get("rooms"),
                                demand.get("minRooms"),
                                demand.get("maxRooms"),
                                "室",
                                15));
            if (!districts.isEmpty()) {
                boolean known =
                        row.get("district") != null && !row.get("district").toString().isBlank();
                details.add(
                        exact(
                                "district",
                                "意向区域",
                                districts.toString(),
                                row.get("district"),
                                known
                                        ? districts.stream()
                                                .anyMatch(
                                                        x ->
                                                                district(x)
                                                                        .equals(
                                                                                district(
                                                                                        row.get(
                                                                                                "district"))))
                                        : null,
                                districtWeight));
            }
            if (decoration)
                details.add(
                        exact(
                                "decoration",
                                "装修",
                                demand.get("decoration").toString(),
                                row.get("decoration"),
                                row.get("decoration") == null
                                                || "UNKNOWN".equals(row.get("decoration"))
                                        ? null
                                        : demand.get("decoration").equals(row.get("decoration")),
                                5));
            if (elevator)
                details.add(
                        exact(
                                "elevator",
                                "电梯偏好",
                                "有电梯",
                                row.get("hasElevator"),
                                row.get("hasElevator") == null
                                        ? null
                                        : truth(row.get("hasElevator")),
                                10));
            double totalWeight =
                    details.stream()
                            .mapToDouble(x -> ((Number) x.get("weight")).doubleValue())
                            .sum();
            double points = 0, knownWeight = 0;
            var satisfied = new ArrayList<String>();
            var gaps = new ArrayList<String>();
            var unknowns = new ArrayList<String>();
            for (var item : details) {
                double weight = ((Number) item.get("weight")).doubleValue();
                points += weight * ((Number) item.get("factor")).doubleValue();
                item.put("weightPercent", Math.round(weight * 100 / totalWeight));
                String label = item.get("label").toString();
                if (!truth(item.get("known"))) unknowns.add(label + "待确认");
                else {
                    knownWeight += weight;
                    if (truth(item.get("matched"))) satisfied.add(label + "符合");
                    else gaps.add(label + "：" + item.get("explanation"));
                }
            }
            boolean strict = gaps.isEmpty() && unknowns.isEmpty();
            if (strict) strictCount++;
            if (!unknowns.isEmpty()) missingCount++;
            int score = (int) Math.round(points * 100 / totalWeight);
            if (!strict) score = Math.min(99, score);
            row.put("matchScore", score);
            row.put("coveragePercent", Math.round(knownWeight * 100 / totalWeight));
            row.put(
                    "matchTier",
                    strict ? "EXACT" : !unknowns.isEmpty() ? "NEEDS_CONFIRMATION" : "NEGOTIABLE");
            row.put("matchReasons", satisfied);
            row.put("matchGaps", gaps);
            row.put("missingInformation", unknowns);
            row.put("criteriaDetails", details);
            row.put(
                    "matchExplanation",
                    String.join("；", satisfied)
                            + (gaps.isEmpty() ? "" : "；" + String.join("；", gaps))
                            + (unknowns.isEmpty() ? "" : "；" + String.join("；", unknowns)));
            BigDecimal price = number(row.get("listingPriceYuan")),
                    size = number(row.get("buildingAreaSqm"));
            if (price != null && price.signum() <= 0) price = null;
            row.put(
                    "unitPriceYuanSqm",
                    price != null && size != null && size.signum() > 0
                            ? price.divide(size, 2, RoundingMode.HALF_UP)
                            : null);
            row.put(
                    "budgetOverrunYuan",
                    price != null && demand.get("maxPrice") != null
                            ? price.subtract(number(demand.get("maxPrice"))).max(BigDecimal.ZERO)
                            : null);
            if (mode.equals("FLEXIBLE") || strict) scored.add(row);
        }
        Comparator<Map<String, Object>> order =
                Comparator.comparingInt(x -> ((Number) x.get("matchScore")).intValue());
        order = order.reversed();
        if (priority.equals("BUDGET"))
            order =
                    order.thenComparing(
                            x -> number(x.get("listingPriceYuan")),
                            Comparator.nullsLast(Comparator.naturalOrder()));
        else if (priority.equals("SPACE"))
            order =
                    order.thenComparing(
                            x -> number(x.get("buildingAreaSqm")),
                            Comparator.nullsLast(Comparator.reverseOrder()));
        order =
                order.thenComparing(
                        Comparator.comparingLong((Map<String, Object> x) -> id(x, "id"))
                                .reversed());
        scored.sort(order);
        response.put("candidateCount", candidates.size());
        response.put("strictCount", strictCount);
        response.put("informationMissingCount", missingCount);
        response.put("resultCount", scored.size());
        response.put("records", scored.subList(0, Math.min(limit, scored.size())));
        return response;
    }
}
