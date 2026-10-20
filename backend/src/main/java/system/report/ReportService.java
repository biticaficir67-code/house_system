package system.report;

import static system.common.DataStore.*;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import system.common.*;
import system.security.AccessScope;

import java.time.LocalDate;
import java.util.*;

@Service
public class ReportService {
    public record Filter(
            @org.springframework.format.annotation.DateTimeFormat(
                            iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE)
                    LocalDate from,
            @org.springframework.format.annotation.DateTimeFormat(
                            iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE)
                    LocalDate to,
            Long storeId,
            String groupBy) {}

    private record Source(String sql, Map<String, Object> parameters) {}

    private final DataStore db;
    private final AccessScope scope;
    private final ObjectProvider<ReportExtension> extensions;

    public ReportService(
            DataStore db, AccessScope scope, ObjectProvider<ReportExtension> extensions) {
        this.db = db;
        this.scope = scope;
        this.extensions = extensions;
    }

    private Source source(String kind, Filter filter) {
        String table, date, extra, join = "";
        switch (kind) {
            case "inventory", "prices" -> {
                table = "house";
                date = "listing_date";
                extra = "r.deleted=0";
            }
            case "showings" -> {
                table = "house_show";
                date = "show_time";
                extra = "r.deleted=0 AND r.status='COMPLETED' AND r.data_origin='BUSINESS'";
                join = " JOIN house h ON h.id=r.house_id";
            }
            case "deals" -> {
                table = "house_deal";
                date = "deal_time";
                extra = "r.deleted=0 AND r.status='CONFIRMED' AND r.verified=1";
                join = " JOIN house h ON h.id=r.house_id";
            }
            case "ai" -> {
                table = "ai_request_log";
                date = "created_at";
                extra = "1=1";
                join = " JOIN house h ON h.id=r.house_id";
            }
            default -> throw new BusinessException(404, "报表类型不存在");
        }
        Checks.require(
                filter.from() == null || filter.to() == null || !filter.from().isAfter(filter.to()),
                "起始日期不能晚于结束日期");
        Checks.require(filter.storeId() == null || filter.storeId() > 0, "门店 ID 必须大于 0");
        var p =
                args(
                        "from",
                        filter.from(),
                        "until",
                        filter.to() == null ? null : filter.to().plusDays(1),
                        "store",
                        filter.storeId());
        String condition = scope.sql("r", p) + " AND " + extra;
        if (filter.from() != null) condition += " AND r." + date + ">=#{p.from}";
        if (filter.to() != null) condition += " AND r." + date + "<#{p.until}";
        if (filter.storeId() != null) condition += " AND r.store_id=#{p.store}";
        return new Source(
                "SELECT r.*"
                        + (join.isEmpty() ? "" : ",h.district,h.community_name")
                        + ",r."
                        + date
                        + " AS occurred_at FROM "
                        + table
                        + " r"
                        + join
                        + " WHERE "
                        + condition,
                p);
    }

    public Object query(String kind, Filter filter) {
        Source source = source(kind, filter);
        for (var extension : extensions.orderedStream().toList()) {
            var result =
                    extension.query(
                            kind,
                            filter,
                            source.sql(),
                            Collections.unmodifiableMap(source.parameters()));
            if (result.isPresent()) return result.get();
        }
        String group =
                filter.groupBy() == null
                        ? switch (kind) {
                            case "inventory" -> "DISTRICT";
                            case "prices" -> "DISTRICT";
                            case "ai" -> "STATUS";
                            default -> "MONTH";
                        }
                        : filter.groupBy();
        String groupSql =
                switch (group) {
                    case "DISTRICT" -> "district";
                    case "COMMUNITY" -> "community_name";
                    case "STORE" -> "store_id";
                    case "BROKER" -> "broker_id";
                    case "STATUS" -> "status";
                    case "MONTH" -> "CONCAT(YEAR(occurred_at),'-',LPAD(MONTH(occurred_at),2,'0'))";
                    default ->
                            throw new BusinessException(
                                    400, "groupBy 支持 DISTRICT/COMMUNITY/STORE/BROKER/STATUS/MONTH");
                };
        String metrics =
                switch (kind) {
                    case "prices" ->
                            "COUNT(listing_price_yuan) AS sample_count,AVG(listing_price_yuan) AS"
                                + " average_listing_price_yuan,AVG(listing_price_yuan/NULLIF(building_area_sqm,0))"
                                + " AS average_listing_unit_price_yuan_sqm";
                    case "deals" ->
                            "COUNT(*) AS deal_count,SUM(transaction_price_yuan) AS"
                                + " total_transaction_price_yuan,AVG(transaction_price_yuan) AS"
                                + " average_transaction_price_yuan";
                    default -> "COUNT(*) AS record_count";
                };
        var rows =
                db.list(
                        "SELECT "
                                + groupSql
                                + " AS group_key,"
                                + metrics
                                + " FROM ("
                                + source.sql()
                                + ") report_data GROUP BY "
                                + groupSql
                                + " ORDER BY group_key LIMIT 1000",
                        source.parameters());
        return args(
                "kind",
                kind,
                "groupBy",
                group,
                "from",
                filter.from(),
                "to",
                filter.to(),
                "records",
                rows,
                "basis",
                switch (kind) {
                    case "prices" -> "挂牌报价，不代表真实成交价";
                    case "deals" -> "仅统计有效且已核实成交";
                    case "showings" -> "仅统计新业务中已完成的实际带看，排除原始演示记录";
                    case "ai" -> "AI 请求记录，包含失败与不可估价";
                    default -> "房源库存，保留导入来源标记";
                });
    }

    public Object overview(Filter filter) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (String kind : List.of("inventory", "showings", "deals", "ai")) {
            Source source = source(kind, filter);
            result.put(
                    kind,
                    db.count(
                            "SELECT COUNT(*) AS value FROM (" + source.sql() + ") report_data",
                            source.parameters()));
        }
        result.put("basis", "成交仅计有效且已核实记录；实际带看排除 LEGACY_DEMO");
        return result;
    }

    public List<Map<String, Object>> exportHouses(Filter filter) {
        Source source = source("inventory", filter);
        return db.list(
                "SELECT"
                    + " id,city,district,community_name,building_area_sqm,listing_price_yuan,status,data_origin"
                    + " FROM ("
                        + source.sql()
                        + ") export_data ORDER BY id LIMIT 10000",
                source.parameters());
    }
}
