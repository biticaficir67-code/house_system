package system.common;

import static system.common.DataStore.*;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.*;
import org.springframework.web.multipart.MultipartFile;

import system.auth.AuthService;
import system.customer.*;
import system.house.*;
import system.owner.*;
import system.security.AccessScope;

import java.io.*;
import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class BusinessImportService {
    public record Column(
            String key, String label, boolean required, String help, String... aliases) {}

    private static final Duration LIFETIME = Duration.ofMinutes(15);
    private final DataStore db;
    private final AccessScope scope;
    private final ResourceAccess access;
    private final Payloads payloads;
    private final SpreadsheetRows sheets;
    private final HouseService houses;
    private final CustomerService customers;
    private final OwnerService owners;
    private final AccountLock lock;
    private final AuditService audit;
    private final Map<String, Batch> batches = new ConcurrentHashMap<>();
    private final SecureRandom random = new SecureRandom();

    private static class Item {
        int row;
        Map<String, Object> data = new LinkedHashMap<>();
        Object body;
        CustomerModels.Demand demand;
        final List<String> errors = new ArrayList<>(), warnings = new ArrayList<>();
        Long duplicateId;
        Integer duplicateRow;
        String identity;
        boolean duplicate;

        Map<String, Object> view() {
            return args(
                    "rowNumber",
                    row,
                    "values",
                    data,
                    "errors",
                    errors,
                    "warnings",
                    warnings,
                    "status",
                    !errors.isEmpty() ? "ERROR" : duplicate ? "DUPLICATE" : "VALID",
                    "duplicateId",
                    duplicateId,
                    "duplicateRowNumber",
                    duplicateRow);
        }
    }

    private static class Batch {
        long owner, store, broker;
        String kind;
        Instant expires;
        List<Item> rows;
        volatile boolean processing;
        volatile Map<String, Object> completed;
    }

    public BusinessImportService(
            DataStore db,
            AccessScope scope,
            ResourceAccess access,
            Payloads payloads,
            SpreadsheetRows sheets,
            HouseService houses,
            CustomerService customers,
            OwnerService owners,
            AccountLock lock,
            AuditService audit) {
        this.db = db;
        this.scope = scope;
        this.access = access;
        this.payloads = payloads;
        this.sheets = sheets;
        this.houses = houses;
        this.customers = customers;
        this.owners = owners;
        this.lock = lock;
        this.audit = audit;
    }

    private String kind(String value) {
        String kind = Objects.toString(value, "").toUpperCase(Locale.ROOT);
        Checks.choice(kind, "HOUSE", "CUSTOMER", "OWNER");
        String permission =
                kind.equals("HOUSE")
                        ? "house:write"
                        : kind.equals("CUSTOMER") ? "customer:write" : "owner:write";
        if (!scope.user().permissions().contains(permission))
            throw new BusinessException(403, "没有该类数据的导入权限");
        return kind;
    }

    private Column col(String key, String label, boolean required, String help, String... aliases) {
        return new Column(key, label, required, help, aliases);
    }

    public List<Column> columns(String requested) {
        String kind = kind(requested);
        var fields = new ArrayList<Column>();
        if (kind.equals("HOUSE")) {
            fields.addAll(
                    List.of(
                            col("communityName", "小区名称", true, "填写真实小区名", "小区"),
                            col("city", "城市", false, "不填默认杭州"),
                            col("district", "区域", true, "例如滨江、西湖", "行政区"),
                            col(
                                    "buildingAreaSqm",
                                    "建筑面积(㎡)",
                                    true,
                                    "大于0，最多2位小数",
                                    "面积",
                                    "建筑面积",
                                    "面积(平方米)"),
                            col("rooms", "室", false, "1～50；缺失时需补充后才能上架", "室数"),
                            col("halls", "厅", false, "0～50，允许填写0", "厅数"),
                            col("bathrooms", "卫", false, "0～50，允许填写0", "卫数"),
                            col("floorLevel", "楼层档位", false, "低/中/高/未知；默认未知", "楼层"),
                            col("totalFloors", "总楼层", true, "1～300的整数"),
                            col("orientation", "朝向", false, "东、南、西、北等，可用逗号分隔；默认未知"),
                            col("decoration", "装修", false, "毛坯/简装/精装/其他/未知；默认未知"),
                            col("propertyUsage", "用途", false, "普通住宅/商住/别墅/车库；默认普通住宅"),
                            col(
                                    "listingPriceYuan",
                                    "挂牌价(元)",
                                    false,
                                    "总价，单位元，不填写时只保存草稿",
                                    "挂牌价",
                                    "挂牌总价(元)",
                                    "总价(元)"),
                            col("listingDate", "挂牌日期", false, "yyyy-MM-dd，可使用Excel日期单元格"),
                            col("hasElevator", "有无电梯", false, "有/无/未知"),
                            col("longitude", "经度", false, "经纬度必须同时填写"),
                            col("latitude", "纬度", false, "经纬度必须同时填写"),
                            col("businessArea", "板块", false, "选填"),
                            col("buildingType", "建筑类型", false, "板楼/塔楼/板塔结合/平房/未知"),
                            col("layoutStructure", "户型结构", false, "平层/复式/错层/LOFT/其他/未知"),
                            col("constructionStructure", "建筑结构", false, "钢混/砖混/混合/框架/钢结构/砖木/未知"),
                            col("ownershipType", "权属", false, "商品房/已购公房/动迁安置/私产/未知"),
                            col("internalAreaSqm", "套内面积(㎡)", false, "不得大于建筑面积", "套内面积"),
                            col("coreSelling", "核心卖点", false, "最多20000字", "卖点"),
                            col("communityIntro", "小区介绍", false, "最多20000字"),
                            col("surrounding", "周边配套", false, "最多20000字"),
                            col("sourceListingId", "来源编号", false, "同一可见范围内用于去重，最多64字", "原始编号")));
            // Accept an explicit alternate price unit, without placing two prices in the template.
            fields.add(
                    col(
                            "listingPriceWan",
                            "挂牌价(万元)",
                            false,
                            "替代元字段，只能填写一种",
                            "总价(万元)",
                            "总价(万)",
                            "挂牌总价(万元)"));
        } else {
            fields.add(col("name", "姓名", true, "最多64字", kind.equals("OWNER") ? "业主姓名" : "客户姓名"));
            fields.add(col("phone", "电话", true, "建议按文本填写，保留前导0，最多32字", "手机号", "联系电话"));
            if (kind.equals("OWNER")) fields.add(col("address", "地址", false, "最多255字"));
            fields.add(col("remark", "备注", false, "最多5000字"));
            if (kind.equals("CUSTOMER"))
                fields.addAll(
                        List.of(
                                col("tags", "标签", false, "多个标签用逗号分隔；有电梯标签可参与匹配"),
                                col("districts", "意向区域", false, "多个区域用逗号分隔"),
                                col("minPrice", "最低预算(元)", false, "单位元，最多2位小数"),
                                col("maxPrice", "最高预算(元)", false, "单位元，最多2位小数"),
                                col("minArea", "最小面积(㎡)", false, "最多2位小数"),
                                col("maxArea", "最大面积(㎡)", false, "最多2位小数"),
                                col("minRooms", "最少室数", false, "1～50"),
                                col("maxRooms", "最多室数", false, "1～50"),
                                col("decoration", "装修偏好", false, "毛坯/简装/精装/其他/未知"),
                                col("expectedPurchaseDate", "预计购房日期", false, "yyyy-MM-dd"),
                                col("notes", "需求说明", false, "最多5000字")));
        }
        return fields;
    }

    public Object catalog() {
        return args(
                "maxRows",
                SpreadsheetRows.MAX_ROWS,
                "maxFileBytes",
                SpreadsheetRows.MAX_BYTES,
                "previewMinutes",
                LIFETIME.toMinutes(),
                "types",
                List.of(
                        args("kind", "HOUSE", "label", "房源", "columns", columns("HOUSE")),
                        args("kind", "CUSTOMER", "label", "客户与需求", "columns", columns("CUSTOMER")),
                        args("kind", "OWNER", "label", "业主", "columns", columns("OWNER"))));
    }

    public byte[] template(String requested, String format) {
        var fields =
                columns(requested).stream()
                        .filter(x -> !x.key().equals("listingPriceWan"))
                        .toList();
        Checks.choice(format, "xlsx", "csv");
        if (format.equals("csv")) {
            return ("\ufeff"
                            + String.join(",", fields.stream().map(Column::label).toList())
                            + "\r\n")
                    .getBytes(java.nio.charset.StandardCharsets.UTF_8);
        }
        try (var workbook = new XSSFWorkbook();
                var out = new ByteArrayOutputStream()) {
            var data = workbook.createSheet("导入数据");
            var header = data.createRow(0);
            var style = workbook.createCellStyle();
            style.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
            style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            var font = workbook.createFont();
            font.setBold(true);
            style.setFont(font);
            for (int i = 0; i < fields.size(); i++) {
                header.createCell(i).setCellValue(fields.get(i).label());
                header.getCell(i).setCellStyle(style);
                data.setColumnWidth(i, 21 * 256);
            }
            data.createFreezePane(0, 1);
            var help = workbook.createSheet("填写说明");
            help.createRow(0)
                    .createCell(0)
                    .setCellValue("只读取第一张数据表；每次最多1000条、5MB；公式请粘贴为值；导入不覆盖已有记录。");
            for (int i = 0; i < fields.size(); i++) {
                var row = help.createRow(i + 1);
                var f = fields.get(i);
                row.createCell(0).setCellValue(f.label());
                row.createCell(1).setCellValue(f.required() ? "必填" : "选填");
                row.createCell(2).setCellValue(f.help());
            }
            help.setColumnWidth(0, 25 * 256);
            help.setColumnWidth(1, 12 * 256);
            help.setColumnWidth(2, 75 * 256);
            workbook.write(out);
            return out.toByteArray();
        } catch (IOException error) {
            throw new BusinessException(500, "模板生成失败，请稍后重试");
        }
    }

    private String header(String value) {
        return value.replace("\ufeff", "")
                .replace('（', '(')
                .replace('）', ')')
                .replace("㎡", "m2")
                .replaceAll("[\\s_*]", "")
                .toLowerCase(Locale.ROOT);
    }

    private List<String> split(Object value) {
        String text = Objects.toString(value, "").trim();
        if (text.startsWith("[")) {
            try {
                Object decoded = db.parse(text);
                Checks.require(decoded instanceof List<?>, "多选字段须为文字数组");
                var list = (List<?>) decoded;
                Checks.require(list.stream().allMatch(x -> x instanceof String), "多选字段须为文字数组");
                return list.stream()
                        .map(x -> x.toString().trim())
                        .filter(x -> !x.isEmpty())
                        .distinct()
                        .toList();
            } catch (IllegalStateException error) {
                throw new BusinessException(400, "多选字段的JSON数组格式不正确");
            }
        }
        return Arrays.stream(text.split("[,，;；、|]"))
                .map(String::trim)
                .filter(x -> !x.isEmpty())
                .distinct()
                .toList();
    }

    private Object decimal(Map<String, String> raw, String key) {
        if (!raw.containsKey(key)) return null;
        String value = raw.get(key).replace(",", "");
        Checks.require(value.matches("[+-]?\\d+(\\.\\d+)?"), key + "必须为数字，单位按列标题填写");
        return new BigDecimal(value);
    }

    private Object integer(Map<String, String> raw, String key) {
        BigDecimal value = (BigDecimal) decimal(raw, key);
        if (value == null) return null;
        try {
            return value.intValueExact();
        } catch (ArithmeticException e) {
            throw new BusinessException(400, key + "必须为整数");
        }
    }

    private Object date(Map<String, String> raw, String key) {
        if (!raw.containsKey(key)) return null;
        try {
            return LocalDate.parse(
                    raw.get(key).replace('/', '-'),
                    DateTimeFormatter.ofPattern("uuuu-M-d")
                            .withResolverStyle(java.time.format.ResolverStyle.STRICT));
        } catch (Exception e) {
            throw new BusinessException(400, key + "日期请填写为yyyy-MM-dd");
        }
    }

    private String choice(String value, Map<String, String> aliases, String... allowed) {
        if (value == null) return null;
        String result = aliases.getOrDefault(value, value.toUpperCase(Locale.ROOT));
        Checks.choice(result, allowed);
        return result;
    }

    private String decoration(String value) {
        return choice(
                value,
                Map.of(
                        "精装", "FINE", "精装修", "FINE", "简装", "SIMPLE", "毛坯", "ROUGH", "其他", "OTHER",
                        "未知", "UNKNOWN", "不限", "UNKNOWN"),
                "FINE",
                "SIMPLE",
                "ROUGH",
                "OTHER",
                "UNKNOWN");
    }

    private void prepare(Item item, String kind, Map<String, String> raw, long store, long broker) {
        var data = new LinkedHashMap<String, Object>();
        data.put("storeId", store);
        data.put("brokerId", broker);
        if (kind.equals("HOUSE")) {
            for (String f :
                    List.of(
                            "city",
                            "district",
                            "communityName",
                            "businessArea",
                            "coreSelling",
                            "communityIntro",
                            "surrounding",
                            "sourceListingId")) data.put(f, raw.get(f));
            if (data.get("city") == null) data.put("city", "杭州");
            for (String f :
                    List.of(
                            "buildingAreaSqm",
                            "listingPriceYuan",
                            "internalAreaSqm",
                            "longitude",
                            "latitude")) data.put(f, decimal(raw, f));
            if (raw.containsKey("listingPriceWan")) {
                Checks.require(!raw.containsKey("listingPriceYuan"), "挂牌价(元)与挂牌价(万元)只填写一种");
                data.put(
                        "listingPriceYuan",
                        ((BigDecimal) decimal(raw, "listingPriceWan"))
                                .multiply(new BigDecimal("10000")));
            }
            for (String f : List.of("rooms", "halls", "bathrooms", "totalFloors"))
                data.put(f, integer(raw, f));
            data.put("listingDate", date(raw, "listingDate"));
            data.put(
                    "floorLevel",
                    choice(
                            raw.getOrDefault("floorLevel", "UNKNOWN"),
                            Map.of("低", "LOW", "中", "MIDDLE", "高", "HIGH", "未知", "UNKNOWN"),
                            "LOW",
                            "MIDDLE",
                            "HIGH",
                            "UNKNOWN"));
            data.put("decoration", decoration(raw.getOrDefault("decoration", "UNKNOWN")));
            data.put(
                    "propertyUsage",
                    choice(
                            raw.getOrDefault("propertyUsage", "RESIDENTIAL"),
                            Map.of(
                                    "普通住宅",
                                    "RESIDENTIAL",
                                    "住宅",
                                    "RESIDENTIAL",
                                    "商住",
                                    "MIXED_USE",
                                    "别墅",
                                    "VILLA",
                                    "车库",
                                    "GARAGE",
                                    "未知",
                                    "UNKNOWN"),
                            "RESIDENTIAL",
                            "MIXED_USE",
                            "VILLA",
                            "GARAGE",
                            "UNKNOWN"));
            var directions =
                    split(raw.getOrDefault("orientation", "UNKNOWN")).stream()
                            .map(
                                    v ->
                                            choice(
                                                    v,
                                                    Map.of(
                                                            "东", "E", "南", "S", "西", "W", "北", "N",
                                                            "东北", "NE", "西北", "NW", "东南", "SE",
                                                            "西南", "SW", "未知", "UNKNOWN"),
                                                    "E",
                                                    "S",
                                                    "W",
                                                    "N",
                                                    "NE",
                                                    "NW",
                                                    "SE",
                                                    "SW",
                                                    "UNKNOWN"))
                            .toList();
            data.put("orientation", directions);
            data.put(
                    "buildingType",
                    choice(
                            raw.get("buildingType"),
                            Map.of(
                                    "板楼",
                                    "SLAB",
                                    "塔楼",
                                    "TOWER",
                                    "板塔结合",
                                    "SLAB_TOWER",
                                    "平房",
                                    "BUNGALOW",
                                    "未知",
                                    "UNKNOWN"),
                            "SLAB",
                            "TOWER",
                            "SLAB_TOWER",
                            "BUNGALOW",
                            "UNKNOWN"));
            data.put(
                    "layoutStructure",
                    choice(
                            raw.get("layoutStructure"),
                            Map.of(
                                    "平层",
                                    "FLAT",
                                    "复式",
                                    "DUPLEX",
                                    "错层",
                                    "SPLIT_LEVEL",
                                    "其他",
                                    "OTHER",
                                    "未知",
                                    "UNKNOWN"),
                            "FLAT",
                            "DUPLEX",
                            "SPLIT_LEVEL",
                            "LOFT",
                            "OTHER",
                            "UNKNOWN"));
            data.put(
                    "constructionStructure",
                    choice(
                            raw.get("constructionStructure"),
                            Map.of(
                                    "钢混",
                                    "STEEL_CONCRETE",
                                    "砖混",
                                    "BRICK_CONCRETE",
                                    "混合",
                                    "MIXED",
                                    "框架",
                                    "FRAME",
                                    "钢结构",
                                    "STEEL",
                                    "砖木",
                                    "BRICK_WOOD",
                                    "未知",
                                    "UNKNOWN"),
                            "STEEL_CONCRETE",
                            "BRICK_CONCRETE",
                            "MIXED",
                            "FRAME",
                            "STEEL",
                            "BRICK_WOOD",
                            "UNKNOWN"));
            data.put(
                    "ownershipType",
                    choice(
                            raw.get("ownershipType"),
                            Map.of(
                                    "商品房",
                                    "COMMERCIAL",
                                    "已购公房",
                                    "PUBLIC_PURCHASED",
                                    "动迁安置",
                                    "RELOCATION",
                                    "私产",
                                    "PRIVATE",
                                    "未知",
                                    "UNKNOWN"),
                            "COMMERCIAL",
                            "PUBLIC_PURCHASED",
                            "RELOCATION",
                            "PRIVATE",
                            "UNKNOWN"));
            String lift = raw.get("hasElevator");
            if (lift == null || Set.of("未知", "UNKNOWN").contains(lift.toUpperCase(Locale.ROOT)))
                data.put("hasElevator", null);
            else {
                String value =
                        choice(
                                lift,
                                Map.of(
                                        "有", "TRUE", "是", "TRUE", "1", "TRUE", "无", "FALSE", "否",
                                        "FALSE", "0", "FALSE"),
                                "TRUE",
                                "FALSE");
                data.put("hasElevator", Boolean.parseBoolean(value));
            }
            if (data.get("sourceListingId") == null)
                data.put(
                        "sourceListingId",
                        "IMPORT-"
                                + AuthService.hash(db.json(new TreeMap<>(data))).substring(0, 56));
            var body = payloads.read(data, HouseModels.Write.class);
            houses.validateImport(body);
            item.body = body;
            if (body.listingPriceYuan() == null) item.warnings.add("缺挂牌价，后续需补充才能上架");
            if (body.rooms() == null || body.halls() == null || body.bathrooms() == null)
                item.warnings.add("户型资料未完整填写，后续需补充");
            if (body.listingDate() != null
                    && body.listingDate().isAfter(LocalDate.now(ZoneId.of("Asia/Shanghai"))))
                item.warnings.add("挂牌日期在未来，请核对");
            item.identity = "HOUSE:" + body.sourceListingId();
        } else {
            for (String f : List.of("name", "phone", "remark")) data.put(f, raw.get(f));
            if (kind.equals("OWNER")) {
                data.put("address", raw.get("address"));
                item.body = payloads.read(data, OwnerModels.Write.class);
            } else {
                data.put("tags", split(raw.get("tags")));
                item.body = payloads.read(data, CustomerModels.Write.class);
                var demand = new LinkedHashMap<String, Object>();
                demand.put("districts", split(raw.get("districts")));
                for (String f : List.of("minPrice", "maxPrice", "minArea", "maxArea"))
                    demand.put(f, decimal(raw, f));
                for (String f : List.of("minRooms", "maxRooms")) demand.put(f, integer(raw, f));
                demand.put("decoration", decoration(raw.get("decoration")));
                demand.put("expectedPurchaseDate", date(raw, "expectedPurchaseDate"));
                demand.put("notes", raw.get("notes"));
                if (demand.values().stream()
                        .anyMatch(
                                v ->
                                        v != null
                                                && (!(v instanceof List<?> list)
                                                        || !list.isEmpty()))) {
                    item.demand = payloads.read(demand, CustomerModels.Demand.class);
                    customers.validateDemand(item.demand);
                    data.put("demand", demand);
                }
            }
            item.identity = kind + ":" + raw.get("phone");
        }
        item.data = data;
    }

    private Long duplicate(Item item, String kind, long store) {
        var p = args("store", store);
        String own = scope.sql("r", p);
        if (kind.equals("HOUSE")) {
            p.put("source", item.data.get("sourceListingId"));
            return db.optional(
                            "SELECT r.id FROM house r WHERE r.deleted=0 AND"
                                    + " r.source_listing_id=#{p.source} AND "
                                    + own
                                    + " ORDER BY r.id LIMIT 1",
                            p)
                    .map(x -> id(x, "id"))
                    .orElse(null);
        }
        p.put("phone", item.data.get("phone"));
        String table = kind.equals("OWNER") ? "owner" : "customer";
        var found =
                db.optional(
                        "SELECT r.id,r.name FROM "
                                + table
                                + " r WHERE r.deleted=0 AND r.store_id=#{p.store} AND"
                                + " r.phone=#{p.phone} AND "
                                + own
                                + " ORDER BY r.id LIMIT 1",
                        p);
        if (found.isEmpty()) return null;
        Checks.require(
                Objects.equals(found.get().get("name"), item.data.get("name")),
                "同一可见门店内该电话已有其他姓名，请核对后再导入");
        return id(found.get(), "id");
    }

    public Object preview(
            String requested, MultipartFile file, Long requestedStore, Long requestedBroker) {
        String kind = kind(requested);
        long store = scope.store(requestedStore);
        Long broker = scope.broker(requestedBroker);
        Checks.require(broker != null, "请选择负责经纪人，导入数据才能进入对应人员的业务范围");
        access.store(store);
        access.broker(store, broker);
        var lines = sheets.read(file);
        int head = 0;
        while (head < lines.size() && lines.get(head).stream().allMatch(String::isBlank)) head++;
        Checks.require(head < lines.size(), "文件为空，请下载模板填写后再上传");
        var schema = columns(kind);
        var aliases = new HashMap<String, String>();
        for (var f : schema) {
            aliases.put(header(f.key()), f.key());
            aliases.put(header(f.label()), f.key());
            for (String alias : f.aliases()) aliases.put(header(alias), f.key());
        }
        var keys = new ArrayList<String>();
        var used = new HashSet<String>();
        var messages = new ArrayList<String>();
        for (String name : lines.get(head)) {
            String key = aliases.get(header(name));
            keys.add(key);
            if (key == null) {
                if (!name.isBlank()) messages.add("已忽略未识别列：" + name);
            } else Checks.require(used.add(key), "字段重复：" + name + "，请只保留一列");
        }
        for (var f : schema)
            if (f.required()) Checks.require(used.contains(f.key()), "缺少必填列：" + f.label());
        var rows = new ArrayList<Item>();
        var identities = new HashMap<String, Item>();
        for (int i = head + 1; i < lines.size(); i++) {
            var cells = lines.get(i);
            if (cells.stream().allMatch(String::isBlank)) continue;
            Checks.require(rows.size() < SpreadsheetRows.MAX_ROWS, "每次最多导入1000条，请分批上传");
            var item = new Item();
            item.row = i + 1;
            rows.add(item);
            try {
                var raw = new LinkedHashMap<String, String>();
                for (int c = 0; c < keys.size(); c++)
                    if (keys.get(c) != null && c < cells.size() && !cells.get(c).isBlank())
                        raw.put(keys.get(c), cells.get(c).trim());
                item.data.putAll(raw);
                prepare(item, kind, raw, store, broker);
                Item earlier = identities.get(item.identity);
                if (earlier != null) {
                    if (!kind.equals("HOUSE")
                            && !Objects.equals(earlier.data.get("name"), item.data.get("name"))) {
                        earlier.errors.add("本文件同一电话出现不同姓名，请核对");
                        throw new BusinessException(400, "本文件同一电话出现不同姓名，请核对");
                    }
                    item.duplicate = true;
                    item.duplicateRow = earlier.row;
                } else {
                    identities.put(item.identity, item);
                    item.duplicateId = duplicate(item, kind, store);
                    item.duplicate = item.duplicateId != null;
                }
                if (item.duplicate) item.warnings.add("明确重复记录将跳过，不覆盖原数据");
            } catch (BusinessException error) {
                item.errors.add(error.getMessage());
            }
        }
        Checks.require(!rows.isEmpty(), "模板只有标题，没有可导入的数据");
        Instant now = Instant.now();
        byte[] nonce = new byte[32];
        random.nextBytes(nonce);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(nonce);
        var batch = new Batch();
        batch.owner = scope.user().id();
        batch.kind = kind;
        batch.store = store;
        batch.broker = broker;
        batch.rows = rows;
        batch.expires = now.plus(LIFETIME);
        synchronized (batches) {
            batches.entrySet()
                    .removeIf(x -> x.getValue().expires.isBefore(now) && !x.getValue().processing);
            Checks.require(
                    batches.size() < 24
                            && batches.values().stream()
                                            .filter(
                                                    x ->
                                                            x.owner == scope.user().id()
                                                                    && x.completed == null)
                                            .count()
                                    < 4,
                    "待处理预览过多，请完成已有批次或15分钟后再试");
            batches.put(token, batch);
        }
        long valid = rows.stream().filter(x -> x.errors.isEmpty() && !x.duplicate).count();
        if (kind.equals("HOUSE")) messages.add("房源将保存为下架、待核验；上传图片并核验后再上架，AI分析可在详情按需生成");
        return args(
                "previewToken",
                token,
                "expiresAt",
                batch.expires,
                "kind",
                kind,
                "storeId",
                store,
                "brokerId",
                broker,
                "total",
                rows.size(),
                "validCount",
                valid,
                "duplicateCount",
                rows.stream().filter(x -> x.errors.isEmpty() && x.duplicate).count(),
                "errorCount",
                rows.stream().filter(x -> !x.errors.isEmpty()).count(),
                "messages",
                messages,
                "rows",
                rows.stream().map(Item::view).toList());
    }

    @Transactional
    public Object commit(String requested, String token) {
        String kind = kind(requested);
        Batch batch = batches.get(token);
        if (batch == null
                || batch.owner != scope.user().id()
                || !batch.kind.equals(kind)
                || Instant.now().isAfter(batch.expires))
            throw new BusinessException(409, "预览不存在或已过期，请重新上传校验");
        synchronized (batch) {
            if (batch.completed != null) return batch.completed;
            if (batch.processing) throw new BusinessException(409, "该批次正在导入，请稍后刷新结果");
            batch.processing = true;
        }
        final var outcome = new LinkedHashMap<String, Object>();
        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        synchronized (batch) {
                            batch.completed = outcome;
                        }
                    }

                    @Override
                    public void afterCompletion(int status) {
                        synchronized (batch) {
                            batch.processing = false;
                        }
                    }
                });
        lock.acquire();
        Checks.require(
                scope.store(batch.store) == batch.store
                        && Objects.equals(scope.broker(batch.broker), batch.broker),
                "导入归属已改变，请重新预览");
        access.store(batch.store);
        access.broker(batch.store, batch.broker);
        var created = new ArrayList<Map<String, Object>>();
        int duplicates = 0, errors = 0;
        for (Item item : batch.rows) {
            if (!item.errors.isEmpty()) {
                errors++;
                continue;
            }
            if (item.duplicate || duplicate(item, kind, batch.store) != null) {
                duplicates++;
                continue;
            }
            long id;
            if (kind.equals("HOUSE"))
                id = id(houses.createImported((HouseModels.Write) item.body), "id");
            else if (kind.equals("OWNER"))
                id = id((Map<String, Object>) owners.create((OwnerModels.Write) item.body), "id");
            else {
                id =
                        id(
                                (Map<String, Object>)
                                        customers.create((CustomerModels.Write) item.body),
                                "id");
                if (item.demand != null) customers.demand(id, item.demand);
            }
            created.add(args("rowNumber", item.row, "id", id));
        }
        Checks.require(!created.isEmpty() || duplicates > 0, "没有可导入的有效数据，请修正错误后重新上传");
        outcome.putAll(
                args(
                        "kind",
                        kind,
                        "total",
                        batch.rows.size(),
                        "importedCount",
                        created.size(),
                        "skippedDuplicateCount",
                        duplicates,
                        "skippedErrorCount",
                        errors,
                        "created",
                        created,
                        "message",
                        "仅导入有效新记录；重复与错误行已跳过，原数据未覆盖"));
        audit.record(
                "business_import",
                0,
                "IMPORT",
                kind + "：导入" + created.size() + "条，重复跳过" + duplicates + "条",
                null,
                args("imported", created.size(), "duplicates", duplicates, "errors", errors));
        return outcome;
    }

    public Object quality() {
        if (!scope.user().administrator()) throw new BusinessException(403, "仅管理员可查看全局数据质量");
        var now = LocalDateTime.now(ZoneId.of("Asia/Shanghai"));
        var p = args("now", now);
        return List.of(
                args(
                        "key",
                        "futureShowings",
                        "label",
                        "完成时间在未来的带看",
                        "count",
                        db.count(
                                "SELECT COUNT(*) AS value FROM house_show WHERE deleted=0 AND"
                                        + " status='COMPLETED' AND completed_time>#{p.now}",
                                p),
                        "advice",
                        "核对样例的实际带看日期，不自动改写"),
                args(
                        "key",
                        "futureDeals",
                        "label",
                        "成交时间在未来的成交",
                        "count",
                        db.count(
                                "SELECT COUNT(*) AS value FROM house_deal WHERE deleted=0 AND"
                                        + " status='CONFIRMED' AND deal_time>#{p.now}",
                                p),
                        "advice",
                        "核对成交记录，避免看板统计口径不一致"),
                args(
                        "key",
                        "missingDate",
                        "label",
                        "缺少挂牌日期的房源",
                        "count",
                        db.count(
                                "SELECT COUNT(*) AS value FROM house WHERE deleted=0 AND"
                                        + " listing_date IS NULL",
                                args()),
                        "advice",
                        "补充日期后才能纳入日期筛选的房源统计"),
                args(
                        "key",
                        "missingImages",
                        "label",
                        "缺少图片的未成交房源",
                        "count",
                        db.count(
                                "SELECT COUNT(*) AS value FROM house h WHERE h.deleted=0 AND"
                                    + " h.status<>'DEAL' AND NOT EXISTS(SELECT 1 FROM house_media m"
                                    + " WHERE m.house_id=h.id AND m.deleted=0 AND"
                                    + " m.media_type='IMAGE')",
                                args()),
                        "advice",
                        "在房源详情上传图片并核验后上架"));
    }
}
