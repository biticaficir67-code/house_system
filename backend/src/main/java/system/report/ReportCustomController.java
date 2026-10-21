package system.report;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import system.common.ApiResponse;
import system.common.DataStore;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/report/custom")
public class ReportCustomController {

    private final DataStore db;

    public ReportCustomController(DataStore db) {
        this.db = db;
    }

    // ==================== 一、核心经营概览（报表首页首屏展示） ====================

    /**
     * 1. 带看-成交转化漏斗图：整体业务转化效率
     */
    @GetMapping("/conversion-funnel")
    public ApiResponse<List<Map<String, Object>>> getConversionFunnel() {
        String sql = "SELECT " +
                "SUM(CASE WHEN status='ON_SALE' THEN 1 ELSE 0 END) AS on_sale_house, " +
                "SUM(CASE WHEN id IN (SELECT DISTINCT house_id FROM house_show WHERE deleted=0) THEN 1 ELSE 0 END) AS has_show_house, " +
                "SUM(CASE WHEN status='DEAL' THEN 1 ELSE 0 END) AS deal_house " +
                "FROM house WHERE deleted=0";
        return ApiResponse.success(db.list(sql, Map.of()));
    }

    /**
     * 2. 门店业绩对比柱状图：两家门店的库存、带看、成交横向对比
     */
    @GetMapping("/store-performance")
    public ApiResponse<List<Map<String, Object>>> getStorePerformance() {
        String sql = "SELECT s.name AS store_name, " +
                "COUNT(DISTINCT CASE WHEN h.status='ON_SALE' THEN h.id END) AS on_sale_count, " +
                "COUNT(DISTINCT sh.id) AS show_count, " +
                "COUNT(DISTINCT CASE WHEN h.status='DEAL' THEN h.id END) AS deal_count " +
                "FROM store s " +
                "LEFT JOIN house h ON s.id = h.store_id AND h.deleted=0 " +
                "LEFT JOIN house_show sh ON s.id = (SELECT store_id FROM broker WHERE broker.id=sh.broker_id) AND sh.deleted=0 " +
                "GROUP BY s.id, s.name " +
                "ORDER BY store_name";
        return ApiResponse.success(db.list(sql, Map.of()));
    }

    /**
     * 3. 房源库存占比饼图：支持按门店/区域/小区三种维度切换
     */
    @GetMapping("/house-stock")
    public ApiResponse<List<Map<String, Object>>> getHouseStock(
            @RequestParam(defaultValue = "store") String groupType) {

        String groupField;
        String join = "";
        String groupBy;
        if ("district".equals(groupType)) {
            groupField = "h.district";
            groupBy = "h.district";
        } else if ("community".equals(groupType)) {
            groupField = "h.community_name";
            groupBy = "h.community_name";
        } else {
            groupField = "s.name";
            join = " JOIN store s ON h.store_id = s.id";
            groupBy = "s.id, s.name";
        }

        String sql = "SELECT " + groupField + " AS group_name, " +
                "COUNT(h.id) AS house_count, " +
                "ROUND(COUNT(h.id)*100.0/(SELECT COUNT(*) FROM house WHERE deleted=0 AND status='ON_SALE'),2) AS ratio " +
                "FROM house h " + join +
                " WHERE h.deleted=0 AND h.status='ON_SALE' " +
                "GROUP BY " + groupBy + " ORDER BY house_count DESC";

        return ApiResponse.success(db.list(sql, Map.of()));
    }

    // ==================== 二、房源结构分析（库存维度二级页面） ====================

    /**
     * 4. 房源库龄分布直方图：滞销房源占比
     */
    @GetMapping("/house-age-distribution")
    public ApiResponse<List<Map<String, Object>>> getHouseAgeDistribution() {
        String sql = "SELECT " +
                "CASE " +
                "  WHEN DATEDIFF((SELECT MAX(listing_date) FROM house), listing_date) <= 30 THEN '0-30天' " +
                "  WHEN DATEDIFF((SELECT MAX(listing_date) FROM house), listing_date) <= 60 THEN '31-60天' " +
                "  WHEN DATEDIFF((SELECT MAX(listing_date) FROM house), listing_date) <= 90 THEN '61-90天' " +
                "  ELSE '90天以上' END AS age_bucket, " +
                "COUNT(*) AS house_count " +
                "FROM house " +
                "WHERE deleted=0 AND status='ON_SALE' AND listing_date IS NOT NULL " +
                "GROUP BY age_bucket " +
                "ORDER BY age_bucket";
        return ApiResponse.success(db.list(sql, Map.of()));
    }

    /**
     * 5. 房源户型占比环形图
     */
    @GetMapping("/house-room-type")
    public ApiResponse<List<Map<String, Object>>> getHouseRoomType() {
        String sql = "SELECT CASE rooms " +
                "WHEN 1 THEN '1室' WHEN 2 THEN '2室' WHEN 3 THEN '3室' " +
                "ELSE '4室及以上' END AS room_type, COUNT(*) AS house_count " +
                "FROM house WHERE deleted=0 AND status='ON_SALE' " +
                "GROUP BY room_type ORDER BY house_count DESC";
        return ApiResponse.success(db.list(sql, Map.of()));
    }

    /**
     * 6. 各户型均价分布：不同户型的价格区间对比
     */
    @GetMapping("/house-price-range")
    public ApiResponse<List<Map<String, Object>>> getHousePriceRange() {
        String sql = "SELECT CASE rooms " +
                "WHEN 1 THEN '1室' WHEN 2 THEN '2室' WHEN 3 THEN '3室' " +
                "ELSE '4室及以上' END AS room_type, " +
                "COUNT(*) AS house_count, " +
                "ROUND(AVG(listing_price_yuan)/10000,1) AS avg_price_wan, " +
                "ROUND(MIN(listing_price_yuan)/10000,1) AS min_price_wan, " +
                "ROUND(MAX(listing_price_yuan)/10000,1) AS max_price_wan " +
                "FROM house WHERE deleted=0 AND status='ON_SALE' AND listing_price_yuan IS NOT NULL " +
                "GROUP BY room_type ORDER BY avg_price_wan DESC";
        return ApiResponse.success(db.list(sql, Map.of()));
    }

    /**
     * 7. 房源装修状态分布
     */
    @GetMapping("/house-decoration")
    public ApiResponse<List<Map<String, Object>>> getHouseDecoration() {
        String sql = "SELECT decoration AS decoration_type, COUNT(*) AS house_count " +
                "FROM house WHERE deleted=0 AND status='ON_SALE' AND decoration IS NOT NULL " +
                "GROUP BY decoration_type ORDER BY house_count DESC";
        return ApiResponse.success(db.list(sql, Map.of()));
    }

    // ==================== 三、业务趋势分析（时间维度二级页面） ====================

    /**
     * 8. 月度成交趋势折线图
     */
    @GetMapping("/deal-trend")
    public ApiResponse<List<Map<String, Object>>> getDealTrend() {
        String sql = "SELECT DATE_FORMAT(h.status_change_time, '%Y-%m') AS deal_month, " +
                "COUNT(h.id) AS deal_count " +
                "FROM house h " +
                "WHERE h.deleted=0 AND h.status='DEAL' AND h.status_change_time IS NOT NULL " +
                "GROUP BY deal_month ORDER BY deal_month ASC";
        return ApiResponse.success(db.list(sql, Map.of()));
    }

    /**
     * 9. 月度带看量趋势折线图
     */
    @GetMapping("/show-trend")
    public ApiResponse<List<Map<String, Object>>> getShowTrend() {
        String sql = "SELECT DATE_FORMAT(completed_time, '%Y-%m') AS show_month, " +
                "COUNT(*) AS show_count " +
                "FROM house_show " +
                "WHERE deleted=0 AND completed_time IS NOT NULL " +
                "GROUP BY show_month ORDER BY show_month ASC";
        return ApiResponse.success(db.list(sql, Map.of()));
    }

    /**
     * 10. 成交周期分布直方图：从挂牌到成交的去化效率
     */
    @GetMapping("/deal-cycle")
    public ApiResponse<List<Map<String, Object>>> getDealCycle() {
        String sql = "SELECT CASE " +
                "WHEN DATEDIFF(status_change_time, listing_date)<=30 THEN '30天内成交' " +
                "WHEN DATEDIFF(status_change_time, listing_date)<=60 THEN '31-60天成交' " +
                "WHEN DATEDIFF(status_change_time, listing_date)<=90 THEN '61-90天成交' " +
                "ELSE '90天以上成交' END AS cycle_bucket, " +
                "COUNT(*) AS deal_count, ROUND(AVG(DATEDIFF(status_change_time, listing_date)),1) AS avg_cycle_days " +
                "FROM house WHERE deleted=0 AND status='DEAL' " +
                "AND status_change_time IS NOT NULL AND listing_date IS NOT NULL " +
                "GROUP BY cycle_bucket ORDER BY cycle_bucket";
        return ApiResponse.success(db.list(sql, Map.of()));
    }

    // ==================== 四、人效与客户转化分析（员工/客户维度二级页面） ====================

    /**
     * 11. 经纪人带看/成交对比柱状图：所有人产能对比
     */
    @GetMapping("/broker-performance")
    public ApiResponse<List<Map<String, Object>>> getBrokerPerformance() {
        String sql = "SELECT b.name AS broker_name, s.name AS store_name, " +
                "COUNT(DISTINCT sh.id) AS show_count, " +
                "COUNT(DISTINCT h.id) AS deal_count, " +
                "CASE WHEN COUNT(DISTINCT sh.id)=0 THEN 0 " +
                "ELSE ROUND(COUNT(DISTINCT h.id)*100.0/COUNT(DISTINCT sh.id),2) END AS conversion_rate " +
                "FROM broker b " +
                "LEFT JOIN store s ON b.store_id = s.id " +
                "LEFT JOIN house_show sh ON b.id = sh.broker_id AND sh.deleted=0 " +
                "LEFT JOIN house h ON b.id = h.broker_id AND h.deleted=0 AND h.status='DEAL' " +
                "WHERE b.account_status=1 " +
                "GROUP BY b.id, b.name, s.name " +
                "ORDER BY deal_count DESC, show_count DESC";
        return ApiResponse.success(db.list(sql, Map.of()));
    }

    /**
     * 12. 经纪人业绩排行TOP10横向柱状图
     */
    @GetMapping("/broker-ranking")
    public ApiResponse<List<Map<String, Object>>> getBrokerRanking() {
        String sql = "SELECT b.name AS broker_name, s.name AS store_name, " +
                "COUNT(DISTINCT sh.id) AS show_count, COUNT(DISTINCT h.id) AS deal_count, " +
                "ROUND(AVG(h.listing_price_yuan)/10000,1) AS avg_deal_price_wan " +
                "FROM broker b " +
                "LEFT JOIN store s ON b.store_id = s.id " +
                "LEFT JOIN house_show sh ON b.id=sh.broker_id AND sh.deleted=0 " +
                "LEFT JOIN house h ON b.id=h.broker_id AND h.deleted=0 AND h.status='DEAL' " +
                "WHERE b.account_status=1 " +
                "GROUP BY b.id, b.name, s.name " +
                "ORDER BY deal_count DESC, show_count DESC LIMIT 10";
        return ApiResponse.success(db.list(sql, Map.of()));
    }

    /**
     * 13. 客户复看率分布：客户带看次数分层
     */
    @GetMapping("/customer-revisit")
    public ApiResponse<List<Map<String, Object>>> getCustomerRevisit() {
        String sql = "SELECT CASE " +
                "WHEN show_cnt=1 THEN '只看1次' WHEN show_cnt=2 THEN '看2次' " +
                "ELSE '看3次及以上' END AS revisit_type, COUNT(*) AS customer_count " +
                "FROM (SELECT customer_id, COUNT(*) AS show_cnt FROM house_show " +
                "WHERE deleted=0 AND customer_id IS NOT NULL GROUP BY customer_id) t " +
                "GROUP BY revisit_type ORDER BY customer_count DESC";
        return ApiResponse.success(db.list(sql, Map.of()));
    }


    //==================== 非报表接口补充 ====================
    /**
     * 操作日志列表（管理员用）
     */
    @GetMapping("/oper-log")
    public ApiResponse<List<Map<String, Object>>> getOperLog() {
        String sql = "SELECT * FROM sys_oper_log ORDER BY create_time DESC LIMIT 100";
        return ApiResponse.success(db.list(sql, Map.of()));
    }
}