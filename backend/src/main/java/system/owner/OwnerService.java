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

    public Object list(String keyword, int page, int size) {
        var p = args("keyword", Checks.search(keyword));
        return access.forPage(
                "owner",
                p.get("keyword") == null
                        ? ""
                        : " AND (name LIKE CONCAT('%',#{p.keyword},'%') OR phone LIKE"
                              + " CONCAT('%',#{p.keyword},'%'))",
                p,
                new PageQuery(page, size));
    }

    public Object detail(long id) {
        var row = access.get("owner", id, false);
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
                                false));
        return detail(id);
    }

    @Transactional
    public Object update(long id, OwnerModels.Write b) {
        var row = access.get("owner", id, true);
        HouseService.version(row, b.version());
        Checks.require(b.storeId() == null || id(row, "storeId") == b.storeId(), "业主不能直接跨店迁移");
        Long broker =
                scope.broker(b.brokerId() == null ? (Long) row.get("brokerId") : b.brokerId());
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
        return detail(id);
    }

    @Transactional
    public void delete(long id) {
        access.get("owner", id, true);
        Checks.require(
                db.count(
                                "SELECT COUNT(*) AS value FROM owner_entrustment WHERE"
                                    + " owner_id=#{p.id} AND status='ACTIVE' AND deleted=0",
                                args("id", id))
                        == 0,
                "请先结束有效委托");
        db.update("owner", id, args("deleted", true));
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
        Checks.require(
                db.count(
                                "SELECT COUNT(*) AS value FROM owner_entrustment WHERE"
                                    + " house_id=#{p.house} AND status='ACTIVE' AND deleted=0 AND"
                                    + " start_date<=#{p.end} AND end_date>=#{p.start} AND"
                                    + " (is_exclusive=TRUE OR #{p.exclusive}=TRUE OR"
                                    + " owner_id=#{p.owner})",
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
                                        owner))
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
                                false));
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
