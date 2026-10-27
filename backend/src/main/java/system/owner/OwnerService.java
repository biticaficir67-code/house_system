package system.owner;

import static system.common.DataStore.*;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import system.common.*;
import system.house.HouseService;
import system.security.AccessScope;

import java.time.LocalDate;

@Service
public class OwnerService {
    private final DataStore db;
    private final ResourceAccess access;
    private final AccessScope scope;
    private final OwnerReminderJob reminders;

    public OwnerService(
            DataStore db, ResourceAccess access, AccessScope scope, OwnerReminderJob reminders) {
        this.db = db;
        this.access = access;
        this.scope = scope;
        this.reminders = reminders;
    }

    public Object list(String keyword, String status, int page, int size) {
        var pageQuery = new PageQuery(page, size);
        var p = args("keyword", Checks.search(keyword), "status", status);
        String filter = "o.deleted=0 AND " + scope.sql("o", p);
        if (p.get("keyword") != null)
            filter +=
                    " AND (o.name LIKE CONCAT('%',#{p.keyword},'%') OR o.phone LIKE"
                            + " CONCAT('%',#{p.keyword},'%'))";
        if (status != null && !status.isBlank()) {
            Checks.choice(status, "ACTIVE", "INACTIVE");
            filter += " AND o.status=#{p.status}";
        }
        p.put("offset", pageQuery.offset());
        p.put("size", pageQuery.size());
        String houseJoin = " FROM house h WHERE h.deleted=0 AND h.owner_id=o.id";
        var records =
                db.list(
                        "SELECT o.*,"
                                + " (SELECT COUNT(*)"
                                + houseJoin
                                + " AND h.status='ON_SALE') AS on_sale_count,"
                                + " (SELECT COUNT(*)"
                                + houseJoin
                                + " AND h.status='OFF') AS off_count,"
                                + " (SELECT COUNT(*)"
                                + houseJoin
                                + " AND h.status='DEAL') AS deal_count"
                                + " FROM owner o WHERE "
                                + filter
                                + " ORDER BY o.id DESC LIMIT #{p.size} OFFSET #{p.offset}",
                        p);
        for (var row : records) row.put("houses", housesOf(id(row, "id")));
        return args(
                "total",
                db.count("SELECT COUNT(*) AS value FROM owner o WHERE " + filter, p),
                "page",
                pageQuery.page(),
                "size",
                pageQuery.size(),
                "records",
                records);
    }

    public Object houseOptions(Long storeId, Long ownerId) {
        long store = scope.store(storeId);
        var p = args("store", store, "owner", ownerId);
        return db.list(
                "SELECT h.id,h.community_name,h.district,h.status,h.listing_price_yuan,"
                    + " h.building_area_sqm,h.rooms,h.halls,h.owner_id,o.name AS owner_name"
                    + " FROM house h LEFT JOIN owner o ON o.id=h.owner_id AND o.deleted=0"
                    + " WHERE h.deleted=0 AND h.store_id=#{p.store} AND "
                    + scope.sql("h", p)
                    + " ORDER BY h.id DESC",
                p);
    }

    public Object housesOf(long owner) {
        return db.list(
                "SELECT id,community_name,district,status,listing_price_yuan,building_area_sqm,"
                    + " rooms,halls,owner_id FROM house WHERE deleted=0 AND owner_id=#{p.id} ORDER"
                    + " BY id",
                args("id", owner));
    }

    public Object detail(long id) {
        var row = access.get("owner", id, false);
        row.put("houses", housesOf(id));
        row.put("entrustments", entrustments(id));
        return row;
    }

    @Transactional
    public Object create(OwnerModels.Write b) {
        long store = scope.store(b.storeId());
        Long broker = scope.broker(b.brokerId());
        access.store(store);
        access.broker(store, broker);
        long id =
                db.insert(
                        "owner",
                        args(
                                "name",
                                b.name(),
                                "phone",
                                b.phone(),
                                "address",
                                b.address(),
                                "remark",
                                b.remark(),
                                "store_id",
                                store,
                                "broker_id",
                                broker,
                                "version",
                                0L,
                                "deleted",
                                false,
                                "status",
                                "ACTIVE"));
        bindHouses(id, access.get("owner", id, true), b.houseIds());
        return detail(id);
    }

    @Transactional
    public Object update(long id, OwnerModels.Write b) {
        var row = access.get("owner", id, true);
        HouseService.version(row, b.version());
        Checks.require(b.storeId() == null || id(row, "storeId") == b.storeId(), "业主不能直接跨店迁移");
        Long broker =
                scope.broker(b.brokerId() == null ? (Long) row.get("brokerId") : b.brokerId());
        Checks.require(
                java.util.Objects.equals(row.get("brokerId"), broker), "调整负责人请使用管理员交接接口并填写原因");
        access.broker(id(row, "storeId"), broker);
        db.update(
                "owner",
                id,
                args(
                        "name",
                        b.name(),
                        "phone",
                        b.phone(),
                        "address",
                        b.address(),
                        "remark",
                        b.remark(),
                        "broker_id",
                        broker,
                        "version",
                        id(row, "version") + 1));
        db.execute(
                "UPDATE owner_entrustment SET broker_id=#{p.broker} WHERE owner_id=#{p.id}",
                args("broker", broker, "id", id));
        bindHouses(id, access.get("owner", id, true), b.houseIds());
        return detail(id);
    }

    // 名下房源写在 house.owner_id：一套房只挂一个业主，取消勾选只解绑不删房
    private void bindHouses(long ownerId, java.util.Map<String, Object> person, java.util.List<Long> houseIds) {
        if (houseIds == null) return;
        var want = new java.util.LinkedHashSet<>(houseIds);
        Checks.require(want.size() <= 50, "一次最多匹配50套房源");
        var current =
                db.list(
                        "SELECT id FROM house WHERE owner_id=#{p.id} AND deleted=0",
                        args("id", ownerId));
        for (var row : current) {
            long hid = id(row, "id");
            if (!want.contains(hid)) db.update("house", hid, args("owner_id", null));
        }
        for (Long hid : want) {
            var house = access.get("house", hid, true);
            access.sameStore(person, house);
            db.update("house", hid, args("owner_id", ownerId));
        }
    }

    // 停用业主：档案留在库里，不 DELETE；房源自己的在售/下架/成交不受影响
    @Transactional
    public void delete(long id) {
        status(id, new OwnerModels.StatusChange("INACTIVE", "停用业主", null));
    }

    @Transactional
    public Object status(long id, OwnerModels.StatusChange b) {
        Checks.choice(b.status(), "ACTIVE", "INACTIVE");
        var row = access.get("owner", id, true);
        if (b.version() != null) HouseService.version(row, b.version());
        Checks.require(!java.util.Objects.equals(row.get("status"), b.status()), "业主已处于该状态");
        db.update(
                "owner",
                id,
                args(
                        "status",
                        b.status(),
                        "deleted",
                        false,
                        "version",
                        id(row, "version") + 1));
        return detail(id);
    }

    public Object entrustments(long owner) {
        access.get("owner", owner, false);
        return db.list(
                "SELECT * FROM owner_entrustment WHERE owner_id=#{p.id} AND deleted=0 ORDER BY id"
                        + " DESC",
                args("id", owner));
    }

    @Transactional
    public Object entrust(long owner, OwnerModels.Entrustment b) {
        var person = access.get("owner", owner, true);
        var house = access.get("house", b.houseId(), true);
        access.sameStore(person, house);
        Checks.require(!b.endDate().isBefore(b.startDate()), "委托结束日期不能早于开始日期");
        Checks.require(!b.endDate().isBefore(LocalDate.now()), "新委托不能已过期");
        Checks.require(!"DEAL".equals(house.get("status")), "已成交房源不能新增委托");
        Long agreement = null;
        if (b.jointWithId() != null) {
            var joint = access.get("owner_entrustment", b.jointWithId(), false);
            Checks.require(
                    id(joint, "houseId") == b.houseId() && "ACTIVE".equals(joint.get("status")),
                    "共同委托须关联同一房源的有效委托");
            Checks.require(
                    java.util.Objects.equals(joint.get("startDate"), b.startDate())
                            && java.util.Objects.equals(joint.get("endDate"), b.endDate())
                            && truth(joint.get("isExclusive")) == b.exclusive(),
                    "共同委托的日期与独家约定必须一致");
            var fee = (java.math.BigDecimal) joint.get("commissionRate");
            Checks.require(
                    fee == null
                            ? b.commissionRate() == null
                            : b.commissionRate() != null && fee.compareTo(b.commissionRate()) == 0,
                    "共同委托的佣金比例必须一致");
            agreement = id(joint, "agreementId");
        }
        Checks.require(
                db.count(
                                "SELECT COUNT(*) AS value FROM owner_entrustment WHERE"
                                    + " house_id=#{p.house} AND status='ACTIVE' AND deleted=0 AND"
                                    + " start_date<=#{p.end} AND end_date>=#{p.start} AND"
                                    + " (#{p.agreement} IS NULL OR agreement_id<>#{p.agreement} OR"
                                    + " owner_id=#{p.owner}) AND (is_exclusive=TRUE OR"
                                    + " #{p.exclusive}=TRUE OR owner_id=#{p.owner})",
                                args(
                                        "house",
                                        b.houseId(),
                                        "end",
                                        b.endDate(),
                                        "start",
                                        b.startDate(),
                                        "exclusive",
                                        b.exclusive(),
                                        "owner",
                                        owner,
                                        "agreement",
                                        agreement))
                        == 0,
                "委托期限与现有有效委托冲突");
        long id =
                db.insert(
                        "owner_entrustment",
                        args(
                                "owner_id",
                                owner,
                                "house_id",
                                b.houseId(),
                                "start_date",
                                b.startDate(),
                                "end_date",
                                b.endDate(),
                                "is_exclusive",
                                b.exclusive(),
                                "commission_rate",
                                b.commissionRate(),
                                "remark",
                                b.remark(),
                                "status",
                                "ACTIVE",
                                "store_id",
                                person.get("storeId"),
                                "broker_id",
                                person.get("brokerId"),
                                "created_by",
                                scope.user().id(),
                                "deleted",
                                false,
                                "agreement_id",
                                agreement));
        if (agreement == null) db.update("owner_entrustment", id, args("agreement_id", id));
        reminders.refreshOne(id);
        return access.get("owner_entrustment", id, false);
    }

    @Transactional
    public void end(long owner, long entrustment, OwnerModels.End b) {
        access.get("owner", owner, false);
        var row = access.get("owner_entrustment", entrustment, true);
        Checks.require(id(row, "ownerId") == owner, "委托不属于该业主");
        Checks.require("ACTIVE".equals(row.get("status")), "委托已结束");
        db.update(
                "owner_entrustment",
                entrustment,
                args("status", "CANCELLED", "end_reason", b.reason()));
        db.execute(
                "UPDATE owner_reminder SET read_status='READ' WHERE entrustment_id=#{p.id}",
                args("id", entrustment));
    }

    public Object reminders() {
        var p = args();
        String condition = scope.sql("e", p);
        return db.list(
                "SELECT r.*,e.owner_id,e.house_id,e.status,e.end_date FROM owner_reminder r JOIN"
                    + " owner_entrustment e ON e.id=r.entrustment_id WHERE e.deleted=0 AND e.status"
                    + " IN ('ACTIVE','EXPIRED') AND "
                        + condition
                        + " ORDER BY r.due_date,r.id",
                p);
    }

    @Transactional
    public void readReminder(long id) {
        var reminder = db.one("SELECT * FROM owner_reminder WHERE id=#{p.id}", args("id", id));
        access.get("owner_entrustment", id(reminder, "entrustmentId"), false);
        db.update("owner_reminder", id, args("read_status", "READ", "read_by", scope.user().id()));
    }
}
