package system.showing;

import static system.common.DataStore.*;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import system.common.*;
import system.house.HouseService;
import system.security.AccessScope;

import java.time.LocalDateTime;
import java.util.*;

@Service
public class ShowingService {
    private final DataStore db;
    private final ResourceAccess access;
    private final AccessScope scope;
    private final AuditService audit;

    public ShowingService(
            DataStore db, ResourceAccess access, AccessScope scope, AuditService audit) {
        this.db = db;
        this.access = access;
        this.scope = scope;
        this.audit = audit;
    }

    public Object list(
            Long houseId,
            Long customerId,
            String status,
            LocalDateTime from,
            LocalDateTime to,
            int page,
            int size) {
        var p =
                args(
                        "house",
                        houseId,
                        "customer",
                        customerId,
                        "status",
                        status,
                        "from",
                        from,
                        "to",
                        to);
        String filter = "";
        if (houseId != null) {
            access.get("house", houseId, false);
            filter += " AND house_id=#{p.house}";
        }
        if (customerId != null) {
            access.get("customer", customerId, false);
            filter += " AND customer_id=#{p.customer}";
        }
        if (status != null) {
            Checks.choice(status, "SCHEDULED", "COMPLETED", "CANCELLED");
            filter += " AND status=#{p.status}";
        }
        Checks.require(from == null || to == null || !from.isAfter(to), "起始时间不能晚于结束时间");
        if (from != null) filter += " AND show_time>=#{p.from}";
        if (to != null) filter += " AND show_time<=#{p.to}";
        return access.forPage("house_show", filter, p, new PageQuery(page, size));
    }

    public Object detail(long id) {
        var row = access.get("house_show", id, false);
        row.put(
                "followUps",
                db.list(
                        "SELECT * FROM showing_follow_up WHERE showing_id=#{p.id} ORDER BY id DESC",
                        args("id", id)));
        return row;
    }

    @Transactional
    public Object create(ShowingModels.Create b) {
        var house = access.get("house", b.houseId(), true);
        var customer = access.get("customer", b.customerId(), false);
        access.sameStore(house, customer);
        Checks.require("ACTIVE".equals(customer.get("status")), "仅跟进中的客户可以新增预约");
        Checks.require("ON_SALE".equals(house.get("status")), "只有在售房源可以预约带看");
        Long broker = scope.broker(b.brokerId());
        Checks.require(broker != null, "必须记录带看经纪人");
        access.broker(id(house, "storeId"), broker);
        db.one("SELECT id FROM broker WHERE id=#{p.id} FOR UPDATE", args("id", broker));
        // A broker cannot accept overlapping 30-minute appointment slots.
        Checks.require(
                db.count(
                                "SELECT COUNT(*) AS value FROM house_show WHERE"
                                    + " broker_id=#{p.broker} AND status='SCHEDULED' AND deleted=0"
                                    + " AND appointment_time>#{p.start} AND"
                                    + " appointment_time<#{p.end}",
                                args(
                                        "broker",
                                        broker,
                                        "start",
                                        b.appointmentTime().minusMinutes(30),
                                        "end",
                                        b.appointmentTime().plusMinutes(30)))
                        == 0,
                "该经纪人此时段已有预约");
        long id =
                db.insert(
                        "house_show",
                        args(
                                "house_id",
                                b.houseId(),
                                "customer_id",
                                b.customerId(),
                                "broker_id",
                                broker,
                                "store_id",
                                house.get("storeId"),
                                "customer_name",
                                customer.get("name"),
                                "appointment_time",
                                b.appointmentTime(),
                                "show_time",
                                b.appointmentTime(),
                                "status",
                                "SCHEDULED",
                                "remark",
                                b.remark(),
                                "data_origin",
                                "BUSINESS",
                                "version",
                                0L,
                                "deleted",
                                false));
        return detail(id);
    }

    @Transactional
    public Object reschedule(long id, ShowingModels.Reschedule b) {
        var pre = access.get("house_show", id, false);
        var house = access.get("house", id(pre, "houseId"), true);
        Checks.require("ON_SALE".equals(house.get("status")), "仅在售房源可以改期");
        db.one(
                "SELECT id FROM broker WHERE id=#{p.id} FOR UPDATE",
                args("id", pre.get("brokerId")));
        var row = access.get("house_show", id, true);
        HouseService.version(row, b.version());
        Checks.require("SCHEDULED".equals(row.get("status")), "只有预约中的记录可改期");
        db.one(
                "SELECT id FROM broker WHERE id=#{p.id} FOR UPDATE",
                args("id", row.get("brokerId")));
        Checks.require(
                db.count(
                                "SELECT COUNT(*) AS value FROM house_show WHERE id<>#{p.id} AND"
                                    + " broker_id=#{p.broker} AND status='SCHEDULED' AND deleted=0"
                                    + " AND appointment_time>#{p.start} AND"
                                    + " appointment_time<#{p.end}",
                                args(
                                        "id",
                                        id,
                                        "broker",
                                        row.get("brokerId"),
                                        "start",
                                        b.appointmentTime().minusMinutes(30),
                                        "end",
                                        b.appointmentTime().plusMinutes(30)))
                        == 0,
                "该时段已有预约");
        db.update(
                "house_show",
                id,
                args(
                        "appointment_time",
                        b.appointmentTime(),
                        "show_time",
                        b.appointmentTime(),
                        "remark",
                        b.remark(),
                        "version",
                        id(row, "version") + 1));
        return detail(id);
    }

    @Transactional
    public Object complete(long id, ShowingModels.Complete b) {
        var pre = access.get("house_show", id, false);
        var house = access.get("house", id(pre, "houseId"), true);
        var row = access.get("house_show", id, true);
        HouseService.version(row, b.version());
        Checks.require(
                "SCHEDULED".equals(row.get("status")) && "ON_SALE".equals(house.get("status")),
                "预约或房源状态不允许完成带看");
        db.update(
                "house_show",
                id,
                args(
                        "status",
                        "COMPLETED",
                        "completed_time",
                        b.actualTime(),
                        "show_time",
                        b.actualTime(),
                        "remark",
                        b.remark(),
                        "version",
                        id(row, "version") + 1));
        return detail(id);
    }

    @Transactional
    public void cancel(long id, ShowingModels.Cancel b) {
        var row = access.get("house_show", id, true);
        HouseService.version(row, b.version());
        Checks.require("SCHEDULED".equals(row.get("status")), "只有预约中的记录可取消");
        db.update(
                "house_show",
                id,
                args(
                        "status",
                        "CANCELLED",
                        "cancel_reason",
                        b.reason(),
                        "version",
                        id(row, "version") + 1));
    }

    @Transactional
    public Object feedback(long id, ShowingModels.Feedback b) {
        var row = access.get("house_show", id, true);
        HouseService.version(row, b.version());
        Checks.require("COMPLETED".equals(row.get("status")), "完成带看后才能回访");
        Checks.choice(b.intention(), "HIGH", "MEDIUM", "LOW", "UNKNOWN");
        db.insert(
                "showing_follow_up",
                args(
                        "showing_id",
                        id,
                        "content",
                        b.content(),
                        "intention",
                        b.intention(),
                        "next_contact_at",
                        b.nextContactAt(),
                        "created_by",
                        scope.user().id()));
        db.update(
                "house_show",
                id,
                args(
                        "feedback",
                        b.content(),
                        "intention",
                        b.intention(),
                        "follow_up_time",
                        LocalDateTime.now(),
                        "next_contact_at",
                        b.nextContactAt(),
                        "version",
                        id(row, "version") + 1));
        return detail(id);
    }

    @Transactional
    public Object assign(long id, FollowUpService.Assignment b) {
        var pre = access.get("house_show", id, false);
        var house = access.get("house", id(pre, "houseId"), true);
        access.broker(id(house, "storeId"), b.brokerId());
        var brokerIds = new TreeSet<Long>(List.of(id(pre, "brokerId"), b.brokerId()));
        for (long broker : brokerIds)
            db.one("SELECT id FROM broker WHERE id=#{p.id} FOR UPDATE", args("id", broker));
        var row = access.get("house_show", id, true);
        HouseService.version(row, b.version());
        Checks.require("SCHEDULED".equals(row.get("status")), "仅待执行预约可以交接，历史执行人不可更改");
        Checks.require(!Objects.equals(row.get("brokerId"), b.brokerId()), "执行人未改变");
        var time = (LocalDateTime) row.get("appointmentTime");
        Checks.require(
                db.count(
                                "SELECT COUNT(*) AS value FROM house_show WHERE id<>#{p.id} AND"
                                    + " broker_id=#{p.broker} AND status='SCHEDULED' AND deleted=0"
                                    + " AND appointment_time>#{p.start} AND"
                                    + " appointment_time<#{p.end}",
                                args(
                                        "id",
                                        id,
                                        "broker",
                                        b.brokerId(),
                                        "start",
                                        time.minusMinutes(30),
                                        "end",
                                        time.plusMinutes(30)))
                        == 0,
                "接收经纪人该时段已有预约");
        db.update(
                "house_show",
                id,
                args("broker_id", b.brokerId(), "version", id(row, "version") + 1));
        audit.record(
                "showing",
                id,
                "ASSIGN",
                b.reason(),
                args("brokerId", row.get("brokerId")),
                args("brokerId", b.brokerId()));
        return detail(id);
    }

    public Object logs(long id, int page, int size) {
        detail(id);
        return audit.list("showing", id, page, size);
    }
}
