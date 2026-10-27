package system.customer;

import static system.common.DataStore.*;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import system.common.*;
import system.house.HouseService;
import system.security.AccessScope;

import java.math.BigDecimal;
import java.util.*;

@Service
public class CustomerService {
    private final DataStore db;
    private final ResourceAccess access;
    private final AccessScope scope;
    private final AuditService audit;
    private final CustomerMatching matching;

    public CustomerService(
            DataStore db,
            ResourceAccess access,
            AccessScope scope,
            AuditService audit,
            CustomerMatching matching) {
        this.db = db;
        this.access = access;
        this.scope = scope;
        this.audit = audit;
        this.matching = matching;
    }

    public Object list(String keyword, String status, int page, int size) {
        var p = args("keyword", Checks.search(keyword), "status", status);
        String filter =
                p.get("keyword") == null
                        ? ""
                        : " AND (name LIKE CONCAT('%',#{p.keyword},'%') OR phone LIKE"
                                + " CONCAT('%',#{p.keyword},'%'))";
        if (status != null) {
            Checks.choice(status, "ACTIVE", "DEAL", "INACTIVE");
            filter += " AND status=#{p.status}";
        }
        return access.forPage("customer", filter, p, new PageQuery(page, size));
    }

    public Map<String, Object> detail(long id) {
        var row = access.get("customer", id, false);
        row.put(
                "demand",
                db.optional(
                                "SELECT * FROM customer_demand WHERE customer_id=#{p.id}",
                                args("id", id))
                        .orElse(null));
        return row;
    }

    @Transactional
    public Object create(CustomerModels.Write b) {
        long store = scope.store(b.storeId());
        Long broker = scope.broker(b.brokerId());
        access.store(store);
        access.broker(store, broker);
        if (b.demand() != null) validateDemand(b.demand());
        long id =
                db.insert(
                        "customer",
                        args(
                                "name",
                                b.name(),
                                "phone",
                                b.phone(),
                                "tags",
                                db.json(b.tags() == null ? List.of() : b.tags()),
                                "remark",
                                b.remark(),
                                "store_id",
                                store,
                                "broker_id",
                                broker,
                                "status",
                                "ACTIVE",
                                "version",
                                0L,
                                "deleted",
                                false,
                                "demand_desc",
                                b.demand() == null ? null : demandSummary(b.demand())));
        if (b.demand() != null) db.insert("customer_demand", demandValues(id, b.demand()));
        return detail(id);
    }

    @Transactional
    public Object update(long id, CustomerModels.Write b) {
        var old = access.get("customer", id, true);
        HouseService.version(old, b.version());
        Checks.require(b.storeId() == null || id(old, "storeId") == b.storeId(), "客户不能直接跨店迁移");
        Long broker =
                scope.broker(b.brokerId() == null ? (Long) old.get("brokerId") : b.brokerId());
        Checks.require(Objects.equals(old.get("brokerId"), broker), "调整负责人请使用管理员交接接口并填写原因");
        access.broker(id(old, "storeId"), broker);
        db.update(
                "customer",
                id,
                args(
                        "name",
                        b.name(),
                        "phone",
                        b.phone(),
                        "tags",
                        db.json(b.tags() == null ? old.get("tags") : b.tags()),
                        "remark",
                        b.remark(),
                        "broker_id",
                        broker,
                        "version",
                        id(old, "version") + 1));
        return detail(id);
    }

    // 停用客户：档案留在库里，不 DELETE
    @Transactional
    public void delete(long id) {
        var row = access.get("customer", id, true);
        status(id, new CustomerModels.StatusChange("INACTIVE", "停用客户", id(row, "version")));
    }

    private static void range(BigDecimal min, BigDecimal max) {
        Checks.require(min == null || max == null || min.compareTo(max) <= 0, "需求范围下限不能大于上限");
    }

    @Transactional
    public Object demand(long id, CustomerModels.Demand b) {
        var customer = access.get("customer", id, true);
        if (b.version() != null) HouseService.version(customer, b.version());
        validateDemand(b);
        var values = demandValues(id, b);
        var existing =
                db.optional(
                        "SELECT id FROM customer_demand WHERE customer_id=#{p.id}", args("id", id));
        if (existing.isEmpty()) db.insert("customer_demand", values);
        else db.update("customer_demand", id(existing.get(), "id"), values);
        db.update(
                "customer",
                id,
                args(
                        "demand_desc",
                        demandSummary(b),
                        "version",
                        id(customer, "version") + 1));
        audit.record("customer", id, "DEMAND", "更新购房需求", null, values);
        return detail(id);
    }

    // 列表「需求摘要」：把结构化需求压成一句话，写入 customer.demand_desc
    String demandSummary(CustomerModels.Demand b) {
        List<String> parts = new ArrayList<>();
        if (b.districts() != null && !b.districts().isEmpty())
            parts.add(String.join("/", b.districts()));
        String budget = wanRange(b.minPrice(), b.maxPrice());
        if (budget != null) parts.add("预算" + budget + "万");
        String area = numRange(b.minArea(), b.maxArea());
        if (area != null) parts.add(area + "㎡");
        String rooms = intRange(b.minRooms(), b.maxRooms());
        if (rooms != null) parts.add(rooms + "室");
        if (b.decoration() != null && !b.decoration().isBlank())
            parts.add(decorationText(b.decoration()));
        if (b.expectedPurchaseDate() != null) parts.add("计划" + b.expectedPurchaseDate());
        if (b.notes() != null && !b.notes().isBlank()) parts.add(b.notes().trim());
        if (parts.isEmpty()) return null;
        String text = String.join("，", parts);
        return text.length() <= 500 ? text : text.substring(0, 500);
    }

    private static String wanRange(BigDecimal min, BigDecimal max) {
        if (min == null && max == null) return null;
        return wan(min) + "-" + wan(max);
    }

    private static String wan(BigDecimal yuan) {
        if (yuan == null) return "";
        return yuan.divide(new BigDecimal("10000")).stripTrailingZeros().toPlainString();
    }

    private static String numRange(BigDecimal min, BigDecimal max) {
        if (min == null && max == null) return null;
        return plain(min) + "-" + plain(max);
    }

    private static String plain(BigDecimal n) {
        if (n == null) return "";
        return n.stripTrailingZeros().toPlainString();
    }

    private static String intRange(Integer min, Integer max) {
        if (min == null && max == null) return null;
        return (min == null ? "" : min) + "-" + (max == null ? "" : max);
    }

    private static String decorationText(String code) {
        return switch (code) {
            case "ROUGH" -> "毛坯";
            case "SIMPLE" -> "简装";
            case "FINE" -> "精装";
            case "OTHER" -> "其他装修";
            default -> "装修不限";
        };
    }

    public void validateDemand(CustomerModels.Demand b) {
        range(b.minPrice(), b.maxPrice());
        range(b.minArea(), b.maxArea());
        Checks.require(
                b.minRooms() == null || b.maxRooms() == null || b.minRooms() <= b.maxRooms(),
                "室数下限不能大于上限");
        if (b.decoration() != null)
            Checks.choice(b.decoration(), "ROUGH", "SIMPLE", "FINE", "OTHER", "UNKNOWN");
    }

    private Map<String, Object> demandValues(long id, CustomerModels.Demand b) {
        return args(
                "customer_id",
                id,
                "districts",
                db.json(b.districts() == null ? List.of() : b.districts()),
                "min_price",
                b.minPrice(),
                "max_price",
                b.maxPrice(),
                "min_area",
                b.minArea(),
                "max_area",
                b.maxArea(),
                "min_rooms",
                b.minRooms(),
                "max_rooms",
                b.maxRooms(),
                "decoration",
                b.decoration(),
                "expected_purchase_date",
                b.expectedPurchaseDate(),
                "notes",
                b.notes());
    }

    public Object matches(long customerId, int limit) {
        return matching.recommend(customerId, limit, "STRICT", "BALANCED").get("records");
    }

    public Object recommendations(long customerId, int limit, String mode, String priority) {
        return matching.recommend(customerId, limit, mode, priority);
    }

    @Transactional
    public Object status(long id, CustomerModels.StatusChange b) {
        Checks.choice(b.status(), "ACTIVE", "INACTIVE");
        var row = access.get("customer", id, true);
        HouseService.version(row, b.version());
        Checks.require(!"DEAL".equals(row.get("status")), "成交客户状态由成交模块维护");
        Checks.require(!Objects.equals(row.get("status"), b.status()), "客户已处于该状态");
        if (b.status().equals("INACTIVE"))
            Checks.require(
                    db.count(
                                    "SELECT COUNT(*) AS value FROM house_show WHERE"
                                            + " customer_id=#{p.id} AND status='SCHEDULED' AND"
                                            + " deleted=0",
                                    args("id", id))
                            == 0,
                    "请先处理未完成预约");
        db.update(
                "customer",
                id,
                args(
                        "status",
                        b.status(),
                        "deleted",
                        false,
                        "inactive_reason",
                        b.status().equals("INACTIVE") ? b.reason() : null,
                        "version",
                        id(row, "version") + 1));
        audit.record(
                "customer",
                id,
                "STATUS",
                b.reason(),
                args("status", row.get("status")),
                args("status", b.status()));
        return detail(id);
    }
}
