package system.house;

import static system.common.DataStore.*;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import system.common.*;
import system.security.AccessScope;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

@Service
public class HouseService {
    private final DataStore db;
    private final AccessScope scope;
    private final ResourceAccess access;
    private final ApplicationEventPublisher events;
    private final AuditService audit;

    public HouseService(
            DataStore db,
            AccessScope scope,
            ResourceAccess access,
            ApplicationEventPublisher events,
            AuditService audit) {
        this.db = db;
        this.scope = scope;
        this.access = access;
        this.events = events;
        this.audit = audit;
    }

    public PageResponse<Map<String, Object>> search(HouseModels.Search search) {
        PageQuery page =
                new PageQuery(
                        search.page() == null ? 1 : search.page(),
                        search.size() == null ? 20 : search.size());
        Map<String, Object> p = args("offset", page.offset(), "size", page.size());
        StringBuilder condition = new StringBuilder("h.deleted=0 AND " + scope.sql("h", p));
        filter(
                condition,
                p,
                "district",
                Checks.search(search.district()),
                "h.district=#{p.district}");
        filter(
                condition,
                p,
                "community",
                Checks.search(search.communityName()),
                "h.community_name LIKE CONCAT('%',#{p.community},'%')");
        if (search.status() != null) Checks.choice(search.status(), "ON_SALE", "OFF", "DEAL");
        filter(condition, p, "status", search.status(), "h.status=#{p.status}");
        if (search.verificationStatus() != null)
            Checks.choice(search.verificationStatus(), "PENDING", "VERIFIED", "REJECTED");
        filter(
                condition,
                p,
                "verification",
                search.verificationStatus(),
                "h.verification_status=#{p.verification}");
        String order =
                switch (Objects.toString(search.sortBy(), "CREATED_AT")) {
                    case "CREATED_AT" -> "h.create_time";
                    case "PRICE" -> "h.listing_price_yuan";
                    case "AREA" -> "h.building_area_sqm";
                    default -> throw new BusinessException(400, "sortBy 支持 CREATED_AT/PRICE/AREA");
                };
        String direction = Objects.toString(search.sortDirection(), "DESC");
        Checks.choice(direction, "ASC", "DESC");
        filter(condition, p, "store", search.storeId(), "h.store_id=#{p.store}");
        filter(condition, p, "broker", search.brokerId(), "h.broker_id=#{p.broker}");
        if (search.rooms() != null) Checks.require(search.rooms() > 0, "室数必须大于 0");
        filter(condition, p, "rooms", search.rooms(), "h.rooms=#{p.rooms}");
        range(search.minPrice(), search.maxPrice());
        range(search.minArea(), search.maxArea());
        filter(condition, p, "minPrice", search.minPrice(), "h.listing_price_yuan>=#{p.minPrice}");
        filter(condition, p, "maxPrice", search.maxPrice(), "h.listing_price_yuan<=#{p.maxPrice}");
        filter(condition, p, "minArea", search.minArea(), "h.building_area_sqm>=#{p.minArea}");
        filter(condition, p, "maxArea", search.maxArea(), "h.building_area_sqm<=#{p.maxArea}");
        return new PageResponse<>(
                db.count("SELECT COUNT(*) AS value FROM house h WHERE " + condition, p),
                page.page(),
                page.size(),
                db.list(
                        "SELECT h.*,b.name AS broker_name,(SELECT m.id FROM house_media m WHERE"
                            + " m.house_id=h.id AND m.deleted=0 AND m.media_type='IMAGE' ORDER BY"
                            + " m.is_cover DESC,m.sort_order,m.id LIMIT 1) AS cover_media_id FROM"
                            + " house h LEFT JOIN broker b ON b.id=h.broker_id WHERE "
                                + condition
                                + " ORDER BY "
                                + order
                                + " "
                                + direction
                                + ",h.id DESC LIMIT #{p.size} OFFSET #{p.offset}",
                        p));
    }

    private static void filter(
            StringBuilder sql, Map<String, Object> p, String key, Object value, String condition) {
        if (value != null) {
            p.put(key, value);
            sql.append(" AND ").append(condition);
        }
    }

    private static void range(BigDecimal min, BigDecimal max) {
        Checks.require(min == null || min.signum() >= 0, "范围下限不得为负");
        Checks.require(max == null || max.signum() >= 0, "范围上限不得为负");
        Checks.require(min == null || max == null || min.compareTo(max) <= 0, "下限不能大于上限");
    }

    public Map<String, Object> detail(long id) {
        var row = access.get("house", id, false);
        row.put(
                "media",
                db.list(
                        "SELECT"
                            + " id,house_id,media_type,original_name,content_type,file_size,created_at,is_cover,sort_order"
                            + " FROM house_media WHERE house_id=#{p.id} AND deleted=0 ORDER BY"
                            + " sort_order,id",
                        args("id", id)));
        var analysis =
                db.optional(
                                "SELECT * FROM house_text_analysis WHERE house_id=#{p.id}",
                                args("id", id))
                        .orElse(null);
        if (analysis != null)
            analysis.put(
                    "stale",
                    !Objects.equals(
                            analysis.get("inputHash"),
                            system.ai.AiFeatures.hash(db, system.ai.AiFeatures.text(row))));
        row.put("textAnalysis", analysis);
        row.put(
                "latestVerification",
                db.optional(
                                "SELECT * FROM house_status_log WHERE house_id=#{p.id} AND"
                                        + " event_type='VERIFY' ORDER BY id DESC LIMIT 1",
                                args("id", id))
                        .orElse(null));
        row.put(
                "entrustmentSummary",
                db.list(
                        "SELECT id,owner_id,start_date,end_date,status,agreement_id FROM"
                            + " owner_entrustment WHERE house_id=#{p.id} AND deleted=0 ORDER BY id"
                            + " DESC LIMIT 100",
                        args("id", id)));
        row.put("operations", audit.list("house", id, 1, 100));
        return row;
    }

    private Map<String, Object> values(HouseModels.Write b) {
        Checks.choice(b.floorLevel(), "LOW", "MIDDLE", "HIGH", "UNKNOWN");
        Checks.choice(b.decoration(), "ROUGH", "SIMPLE", "FINE", "OTHER", "UNKNOWN");
        Checks.choice(b.propertyUsage(), "RESIDENTIAL", "MIXED_USE", "VILLA", "GARAGE", "UNKNOWN");
        Set<String> directions = Set.of("E", "S", "W", "N", "NE", "NW", "SE", "SW", "UNKNOWN");
        Checks.require(
                b.orientation().size() <= 8 && directions.containsAll(b.orientation()), "朝向枚举错误");
        Checks.require(
                !b.orientation().contains("UNKNOWN") || b.orientation().size() == 1,
                "UNKNOWN 朝向只能单独使用");
        Checks.require(new HashSet<>(b.orientation()).size() == b.orientation().size(), "朝向不能重复");
        Checks.require((b.longitude() == null) == (b.latitude() == null), "经纬度必须同时填写");
        Checks.require(
                b.internalAreaSqm() == null
                        || b.internalAreaSqm().compareTo(b.buildingAreaSqm()) <= 0,
                "套内面积不能大于建筑面积");
        if (b.buildingType() != null)
            Checks.choice(b.buildingType(), "SLAB", "TOWER", "SLAB_TOWER", "BUNGALOW", "UNKNOWN");
        if (b.layoutStructure() != null)
            Checks.choice(
                    b.layoutStructure(),
                    "FLAT",
                    "DUPLEX",
                    "SPLIT_LEVEL",
                    "LOFT",
                    "OTHER",
                    "UNKNOWN");
        if (b.constructionStructure() != null)
            Checks.choice(
                    b.constructionStructure(),
                    "STEEL_CONCRETE",
                    "BRICK_CONCRETE",
                    "MIXED",
                    "FRAME",
                    "STEEL",
                    "BRICK_WOOD",
                    "UNKNOWN");
        if (b.ownershipType() != null)
            Checks.choice(
                    b.ownershipType(),
                    "COMMERCIAL",
                    "PUBLIC_PURCHASED",
                    "RELOCATION",
                    "PRIVATE",
                    "UNKNOWN");
        return args(
                "city",
                b.city(),
                "district",
                b.district(),
                "business_area",
                b.businessArea(),
                "community_name",
                b.communityName(),
                "community_id",
                b.communityId(),
                "longitude",
                b.longitude(),
                "latitude",
                b.latitude(),
                "building_area_sqm",
                b.buildingAreaSqm(),
                "rooms",
                b.rooms(),
                "halls",
                b.halls(),
                "bathrooms",
                b.bathrooms(),
                "floor_level",
                b.floorLevel(),
                "total_floors",
                b.totalFloors(),
                "orientation",
                db.json(b.orientation()),
                "decoration",
                b.decoration(),
                "has_elevator",
                b.hasElevator(),
                "property_usage",
                b.propertyUsage(),
                "building_type",
                b.buildingType(),
                "layout_structure",
                b.layoutStructure(),
                "construction_structure",
                b.constructionStructure(),
                "ownership_type",
                b.ownershipType(),
                "internal_area_sqm",
                b.internalAreaSqm(),
                "listing_date",
                b.listingDate(),
                "listing_price_yuan",
                b.listingPriceYuan(),
                "core_selling",
                b.coreSelling(),
                "community_intro",
                b.communityIntro(),
                "surrounding",
                b.surrounding(),
                "source_listing_id",
                b.sourceListingId());
    }

    @Transactional
    public Map<String, Object> create(HouseModels.Write b) {
        long store = scope.store(b.storeId());
        Long broker = scope.broker(b.brokerId());
        access.store(store);
        access.broker(store, broker);
        var values = values(b);
        values.putAll(
                args(
                        "store_id",
                        store,
                        "broker_id",
                        broker,
                        "status",
                        "OFF",
                        "verification_status",
                        "PENDING",
                        "source",
                        "MANUAL",
                        "data_origin",
                        "BUSINESS",
                        "version",
                        0L,
                        "deleted",
                        false));
        long id = db.insert("house", values);
        log(id, null, "OFF", "CREATE", "录入房源，待核验", store);
        events.publishEvent(new HouseModels.Changed(id, scope.user().id()));
        return detail(id);
    }

    @Transactional
    public Map<String, Object> update(long id, HouseModels.Write b) {
        var old = access.get("house", id, true);
        version(old, b.version());
        Checks.require(!"DEAL".equals(old.get("status")), "已成交房源不能修改");
        Checks.require(
                b.storeId() == null || Objects.equals(old.get("storeId"), b.storeId()),
                "调整门店请使用交接接口");
        Checks.require(
                b.brokerId() == null || Objects.equals(old.get("brokerId"), b.brokerId()),
                "调整负责人请使用交接接口");
        var values = values(b);
        values.put("version", id(old, "version") + 1);
        // Published information must be reviewed again after editing.
        if ("ON_SALE".equals(old.get("status"))) {
            values.put("status", "OFF");
            values.put("status_change_time", LocalDateTime.now());
        }
        values.put("verification_status", "PENDING");
        db.update("house", id, values);
        audit.record(
                "house",
                id,
                "EDIT",
                "更新房源资料",
                args("listingPriceYuan", old.get("listingPriceYuan")),
                args("listingPriceYuan", b.listingPriceYuan()));
        log(
                id,
                (String) old.get("status"),
                "OFF".equals(values.get("status")) ? "OFF" : (String) old.get("status"),
                "EDIT",
                "更新资料，重新核验",
                id(old, "storeId"));
        if (!Objects.equals(old.get("coreSelling"), b.coreSelling())
                || !Objects.equals(old.get("communityIntro"), b.communityIntro())
                || !Objects.equals(old.get("surrounding"), b.surrounding())
                || !Objects.equals(old.get("district"), b.district()))
            events.publishEvent(new HouseModels.Changed(id, scope.user().id()));
        return detail(id);
    }

    public static void version(Map<String, Object> row, Long version) {
        if (version == null || version != id(row, "version"))
            throw new BusinessException(409, "记录已被修改，请刷新后重试");
    }

    @Transactional
    public Map<String, Object> status(long id, HouseModels.StatusChange b) {
        Checks.choice(b.status(), "ON_SALE", "OFF");
        var old = access.get("house", id, true);
        version(old, b.version());
        Checks.require(!"DEAL".equals(old.get("status")), "成交状态只能通过成交记录流转");
        Checks.require(!Objects.equals(old.get("status"), b.status()), "房源已处于该状态");
        if ("ON_SALE".equals(b.status())) {
            var missing = new ArrayList<String>();
            if (old.get("storeId") == null) missing.add("所属门店");
            if (old.get("brokerId") == null) missing.add("负责经纪人");
            if (!(old.get("listingPriceYuan") instanceof Number price) || price.doubleValue() <= 0)
                missing.add("大于零的挂牌价格");
            if (!"VERIFIED".equals(old.get("verificationStatus"))) missing.add("核验通过");
            for (String field :
                    List.of(
                            "communityName",
                            "district",
                            "buildingAreaSqm",
                            "rooms",
                            "halls",
                            "bathrooms"))
                if (old.get(field) == null
                        || old.get(field) instanceof String text && text.isBlank())
                    missing.add(field);
            if (db.count(
                            "SELECT COUNT(*) AS value FROM house_media WHERE house_id=#{p.id} AND"
                                    + " media_type='IMAGE' AND deleted=0",
                            args("id", id))
                    == 0) missing.add("至少一张图片");
            Checks.require(missing.isEmpty(), "上架缺少：" + String.join("、", missing));
            access.store(id(old, "storeId"));
            access.broker(
                    id(old, "storeId"), old.get("brokerId") == null ? null : id(old, "brokerId"));
            Checks.require("VERIFIED".equals(old.get("verificationStatus")), "核验通过后才能上架");
            Checks.require(
                    old.get("listingPriceYuan") instanceof Number n && n.doubleValue() > 0,
                    "挂牌价格必须大于 0");
            Checks.require(old.get("brokerId") != null, "上架前须指定负责人");
        } else {
            Checks.require(
                    db.count(
                                    "SELECT COUNT(*) AS value FROM house_show WHERE"
                                        + " house_id=#{p.id} AND status='SCHEDULED' AND deleted=0",
                                    args("id", id))
                            == 0,
                    "存在待执行预约，请先取消、完成或处理预约后再下架");
        }
        db.update(
                "house",
                id,
                args(
                        "status",
                        b.status(),
                        "version",
                        id(old, "version") + 1,
                        "status_change_time",
                        LocalDateTime.now()));
        log(id, (String) old.get("status"), b.status(), "STATUS", b.reason(), id(old, "storeId"));
        return detail(id);
    }

    @Transactional
    public Map<String, Object> verify(long id, HouseModels.Verification b) {
        var old = access.get("house", id, true);
        version(old, b.version());
        Checks.require("OFF".equals(old.get("status")), "只有已下架房源可以核验");
        Checks.require("PENDING".equals(old.get("verificationStatus")), "该房源已核验，资料变更后可重新提交");
        db.update(
                "house",
                id,
                args(
                        "verification_status",
                        b.approved() ? "VERIFIED" : "REJECTED",
                        "version",
                        id(old, "version") + 1));
        log(
                id,
                (String) old.get("status"),
                (String) old.get("status"),
                "VERIFY",
                b.reason(),
                id(old, "storeId"));
        return detail(id);
    }

    @Transactional
    public Map<String, Object> assign(long id, HouseModels.Assignment b) {
        var old = access.get("house", id, true);
        version(old, b.version());
        access.store(b.storeId());
        access.broker(b.storeId(), b.brokerId());
        Checks.require(
                !Objects.equals(old.get("brokerId"), b.brokerId())
                        || !Objects.equals(old.get("storeId"), b.storeId()),
                "负责人与门店未改变");
        if (!Objects.equals(old.get("storeId"), b.storeId())) {
            Checks.require(scope.user().administrator(), "跨门店交接需要管理员权限");
            for (String table : List.of("owner_entrustment", "house_show", "house_deal"))
                Checks.require(
                        db.count(
                                        "SELECT COUNT(*) AS value FROM "
                                                + table
                                                + " WHERE house_id=#{p.id} AND deleted=0",
                                        args("id", id))
                                == 0,
                        "存在关联业务，暂不支持跨店交接");
        }
        db.update(
                "house",
                id,
                args(
                        "store_id",
                        b.storeId(),
                        "broker_id",
                        b.brokerId(),
                        "version",
                        id(old, "version") + 1));
        audit.record(
                "house",
                id,
                "ASSIGN",
                b.reason(),
                args("storeId", old.get("storeId"), "brokerId", old.get("brokerId")),
                args("storeId", b.storeId(), "brokerId", b.brokerId()));
        log(
                id,
                (String) old.get("status"),
                (String) old.get("status"),
                "ASSIGN",
                b.reason(),
                b.storeId());
        return detail(id);
    }

    @Transactional
    public void delete(long id) {
        var old = access.get("house", id, true);
        Checks.require("OFF".equals(old.get("status")), "房源下架后才可归档");
        Checks.require(
                db.count(
                                "SELECT COUNT(*) AS value FROM owner_entrustment WHERE"
                                        + " house_id=#{p.id} AND status='ACTIVE' AND deleted=0",
                                args("id", id))
                        == 0,
                "请先结束有效委托");
        Checks.require(
                db.count(
                                "SELECT COUNT(*) AS value FROM house_show WHERE house_id=#{p.id}"
                                        + " AND status='SCHEDULED' AND deleted=0",
                                args("id", id))
                        == 0,
                "请先取消未完成预约");
        db.update("house", id, args("deleted", true, "version", id(old, "version") + 1));
        log(id, "OFF", "OFF", "ARCHIVE", "归档房源", id(old, "storeId"));
    }

    public long mediaChanged(long id, String event) {
        var old = access.get("house", id, true);
        Checks.require(!"DEAL".equals(old.get("status")), "已成交房源不能调整媒体");
        long version = id(old, "version") + 1;
        db.update(
                "house",
                id,
                args(
                        "status",
                        "OFF",
                        "verification_status",
                        "PENDING",
                        "version",
                        version,
                        "status_change_time",
                        LocalDateTime.now()));
        log(id, (String) old.get("status"), "OFF", event, "媒体变更，重新核验", id(old, "storeId"));
        return version;
    }

    public Object logs(long id) {
        access.get("house", id, false);
        return db.list(
                "SELECT * FROM house_status_log WHERE house_id=#{p.id} ORDER BY id DESC",
                args("id", id));
    }

    public void log(long house, String from, String to, String type, String reason, long store) {
        db.insert(
                "house_status_log",
                args(
                        "house_id",
                        house,
                        "from_status",
                        from,
                        "to_status",
                        to,
                        "event_type",
                        type,
                        "reason",
                        reason,
                        "changed_by",
                        scope.user().id(),
                        "store_id",
                        store,
                        "changed_at",
                        LocalDateTime.now()));
    }
}
