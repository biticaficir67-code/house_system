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
                                false));
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

    @Transactional
    public void delete(long id) {
        access.get("customer", id, true);
        Checks.require(
                db.count(
                                "SELECT COUNT(*) AS value FROM house_show WHERE customer_id=#{p.id}"
                                        + " AND status='SCHEDULED' AND deleted=0",
                                args("id", id))
                        == 0,
                "请先取消未完成预约");
        Checks.require(
                db.count(
                                "SELECT COUNT(*) AS value FROM house_deal WHERE customer_id=#{p.id}"
                                        + " AND status='CONFIRMED' AND deleted=0",
                                args("id", id))
                        == 0,
                "已成交客户应保留，不允许归档");
        db.update("customer", id, args("deleted", true, "status", "INACTIVE"));
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
        db.update("customer", id, args("version", id(customer, "version") + 1));
        audit.record("customer", id, "DEMAND", "更新购房需求", null, values);
        return detail(id);
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
