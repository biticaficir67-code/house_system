package system.deal;

import static system.common.DataStore.*;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import system.common.*;
import system.house.HouseService;
import system.security.AccessScope;

import java.time.LocalDateTime;

@Service
public class DealService {
    private final DataStore db;
    private final ResourceAccess access;
    private final AccessScope scope;
    private final HouseService houses;

    public DealService(
            DataStore db, ResourceAccess access, AccessScope scope, HouseService houses) {
        this.db = db;
        this.access = access;
        this.scope = scope;
        this.houses = houses;
    }

    public Object list(Long houseId, Boolean verified, int page, int size) {
        var p = args("house", houseId, "verified", verified);
        String filter = "";
        if (houseId != null) {
            access.get("house", houseId, false);
            filter += " AND house_id=#{p.house}";
        }
        if (verified != null) filter += " AND verified=#{p.verified}";
        return access.forPage("house_deal", filter, p, new PageQuery(page, size));
    }

    public Object detail(long id) {
        return access.get("house_deal", id, false);
    }

    @Transactional
    public Object create(DealModels.Create b) {
        var house = access.get("house", b.houseId(), true);
        var customer = access.get("customer", b.customerId(), true);
        access.sameStore(house, customer);
        Checks.require("ON_SALE".equals(house.get("status")), "只有在售房源可登记成交");
        Long broker = scope.broker(b.brokerId());
        Checks.require(broker != null, "成交经纪人不能为空");
        access.broker(id(house, "storeId"), broker);
        Checks.require(
                db.count(
                                "SELECT COUNT(*) AS value FROM house_deal WHERE house_id=#{p.id}"
                                        + " AND status='CONFIRMED' AND deleted=0",
                                args("id", b.houseId()))
                        == 0,
                "该房源已有有效成交记录");
        Checks.require(
                b.commissionYuan() == null
                        || b.commissionYuan().compareTo(b.transactionPriceYuan()) <= 0,
                "佣金不能高于成交金额");
        long id =
                db.insert(
                        "house_deal",
                        args(
                                "house_id",
                                b.houseId(),
                                "customer_id",
                                b.customerId(),
                                "broker_id",
                                broker,
                                "store_id",
                                house.get("storeId"),
                                "deal_time",
                                b.dealTime(),
                                "transaction_price_yuan",
                                b.transactionPriceYuan(),
                                "commission_yuan",
                                b.commissionYuan(),
                                "remark",
                                b.remark(),
                                "status",
                                "CONFIRMED",
                                "verified",
                                false,
                                "created_by",
                                scope.user().id(),
                                "version",
                                0L,
                                "deleted",
                                false));
        db.update(
                "house",
                b.houseId(),
                args(
                        "status",
                        "DEAL",
                        "status_change_time",
                        b.dealTime(),
                        "version",
                        id(house, "version") + 1));
        db.update(
                "customer",
                b.customerId(),
                args("status", "DEAL", "version", id(customer, "version") + 1));
        db.execute(
                "UPDATE house_show SET status='CANCELLED',cancel_reason='房源已成交',version=version+1"
                        + " WHERE house_id=#{p.id} AND status='SCHEDULED' AND deleted=0",
                args("id", b.houseId()));
        db.execute(
                "UPDATE owner_entrustment SET status='COMPLETED',end_reason='房源已成交' WHERE"
                        + " house_id=#{p.id} AND status='ACTIVE' AND deleted=0",
                args("id", b.houseId()));
        houses.log(b.houseId(), "ON_SALE", "DEAL", "DEAL", "登记成交 #" + id, id(house, "storeId"));
        return detail(id);
    }

    @Transactional
    public Object edit(long id, DealModels.Edit b) {
        var row = access.get("house_deal", id, true);
        HouseService.version(row, b.version());
        Checks.require(
                "CONFIRMED".equals(row.get("status")) && !truth(row.get("verified")),
                "只有未核实的有效成交可修改");
        Checks.require(
                b.commissionYuan() == null
                        || b.commissionYuan().compareTo(b.transactionPriceYuan()) <= 0,
                "佣金不能高于成交金额");
        db.update(
                "house_deal",
                id,
                args(
                        "deal_time",
                        b.dealTime(),
                        "transaction_price_yuan",
                        b.transactionPriceYuan(),
                        "commission_yuan",
                        b.commissionYuan(),
                        "remark",
                        b.remark(),
                        "version",
                        id(row, "version") + 1));
        return detail(id);
    }

    @Transactional
    public Object verify(long id, DealModels.Verification b) {
        var row = access.get("house_deal", id, true);
        HouseService.version(row, b.version());
        Checks.require("CONFIRMED".equals(row.get("status")), "已撤销成交不能核实");
        db.update(
                "house_deal",
                id,
                args(
                        "verified",
                        b.approved(),
                        "verified_by",
                        scope.user().id(),
                        "verified_at",
                        LocalDateTime.now(),
                        "verify_reason",
                        b.reason(),
                        "version",
                        id(row, "version") + 1));
        return detail(id);
    }

    @Transactional
    public Object cancel(long id, DealModels.Cancel b) {
        var pre = access.get("house_deal", id, false);
        var house = access.get("house", id(pre, "houseId"), true);
        var customer = access.get("customer", id(pre, "customerId"), true);
        var row = access.get("house_deal", id, true);
        HouseService.version(row, b.version());
        Checks.require("CONFIRMED".equals(row.get("status")), "成交已撤销");
        Checks.require(
                !truth(row.get("verified")) || scope.user().permissions().contains("deal:verify"),
                "已核实的成交须由管理员撤销");
        db.update(
                "house_deal",
                id,
                args(
                        "status",
                        "CANCELLED",
                        "cancel_reason",
                        b.reason(),
                        "verified",
                        false,
                        "version",
                        id(row, "version") + 1));
        db.update(
                "house",
                id(house, "id"),
                args(
                        "status",
                        "OFF",
                        "status_change_time",
                        LocalDateTime.now(),
                        "version",
                        id(house, "version") + 1));
        if (db.count(
                        "SELECT COUNT(*) AS value FROM house_deal WHERE customer_id=#{p.id} AND"
                                + " status='CONFIRMED' AND deleted=0",
                        args("id", id(customer, "id")))
                == 0)
            db.update(
                    "customer",
                    id(customer, "id"),
                    args("status", "ACTIVE", "version", id(customer, "version") + 1));
        houses.log(id(house, "id"), "DEAL", "OFF", "DEAL_CANCEL", b.reason(), id(house, "storeId"));
        return detail(id);
    }
}
