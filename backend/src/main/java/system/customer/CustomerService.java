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

    public CustomerService(DataStore db, ResourceAccess access, AccessScope scope) {
        this.db = db;
        this.access = access;
        this.scope = scope;
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
                        db.json(b.tags() == null ? List.of() : b.tags()),
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
        access.get("customer", id, true);
        range(b.minPrice(), b.maxPrice());
        range(b.minArea(), b.maxArea());
        Checks.require(
                b.minRooms() == null || b.maxRooms() == null || b.minRooms() <= b.maxRooms(),
                "室数下限不能大于上限");
        if (b.decoration() != null)
            Checks.choice(b.decoration(), "ROUGH", "SIMPLE", "FINE", "OTHER", "UNKNOWN");
        var values =
                args(
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
        var existing =
                db.optional(
                        "SELECT id FROM customer_demand WHERE customer_id=#{p.id}", args("id", id));
        if (existing.isEmpty()) db.insert("customer_demand", values);
        else db.update("customer_demand", id(existing.get(), "id"), values);
        return detail(id);
    }

    public Object matches(long customerId, int limit) {
        Checks.require(limit >= 1 && limit <= 100, "limit 范围为 1-100");
        access.get("customer", customerId, false);
        var demand =
                db.one(
                        "SELECT * FROM customer_demand WHERE customer_id=#{p.id}",
                        args("id", customerId));
        var p = args("limit", limit);
        StringBuilder where =
                new StringBuilder("h.deleted=0 AND h.status='ON_SALE' AND " + scope.sql("h", p));
        for (String bound :
                List.of("minPrice", "maxPrice", "minArea", "maxArea", "minRooms", "maxRooms")) {
            if (demand.get(bound) == null) continue;
            p.put(bound, demand.get(bound));
            String column =
                    bound.endsWith("Price")
                            ? "listing_price_yuan"
                            : bound.endsWith("Area") ? "building_area_sqm" : "rooms";
            where.append(" AND h.")
                    .append(column)
                    .append(bound.startsWith("min") ? ">=" : "<=")
                    .append("#{p.")
                    .append(bound)
                    .append("}");
        }
        if (demand.get("decoration") != null) {
            where.append(" AND h.decoration=#{p.decoration}");
            p.put("decoration", demand.get("decoration"));
        }
        if (demand.get("districts") instanceof List<?> districts && !districts.isEmpty()) {
            List<String> placeholders = new ArrayList<>();
            for (int i = 0; i < districts.size(); i++) {
                p.put("district" + i, districts.get(i));
                placeholders.add("#{p.district" + i + "}");
            }
            where.append(" AND h.district IN (").append(String.join(",", placeholders)).append(")");
        }
        var rows =
                db.list(
                        "SELECT"
                            + " h.id,h.community_name,h.district,h.building_area_sqm,h.rooms,h.listing_price_yuan,h.decoration"
                            + " FROM house h WHERE "
                                + where
                                + " ORDER BY h.id DESC LIMIT #{p.limit}",
                        p);
        rows.forEach(row -> row.put("matchExplanation", "满足已填写的区域、预算、面积、户型及装修条件；未填写项不参与匹配"));
        return rows;
    }
}
