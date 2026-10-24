package system.ai;

import static system.common.DataStore.*;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.slf4j.*;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.*;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.client.RestTemplate;

import system.common.*;
import system.config.AiIntegrationProperties;
import system.security.AccessScope;

import java.io.*;
import java.net.URI;
import java.time.*;
import java.util.*;

@Service
public class AiService {
    private static final Logger log = LoggerFactory.getLogger(AiService.class);
    private final DataStore db;
    private final ResourceAccess access;
    private final AccessScope scope;
    private final RestTemplate priceHttp, textHttp;
    private final ObjectMapper json;
    private final AiIntegrationProperties addresses;
    private final TransactionTemplate tx;

    public AiService(
            DataStore db,
            ResourceAccess access,
            AccessScope scope,
            @Qualifier("priceRestTemplate") RestTemplate priceHttp,
            @Qualifier("textRestTemplate") RestTemplate textHttp,
            ObjectMapper json,
            AiIntegrationProperties addresses,
            PlatformTransactionManager manager) {
        this.db = db;
        this.access = access;
        this.scope = scope;
        this.priceHttp = priceHttp;
        this.textHttp = textHttp;
        this.json = json;
        this.addresses = addresses;
        this.tx = new TransactionTemplate(manager);
        this.tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> call(
            String base, String path, Map<String, Object> input, int seconds) throws Exception {
        URI uri = URI.create(base.replaceAll("/+$", "") + path);
        if (!List.of("http", "https").contains(uri.getScheme()) || uri.getHost() == null)
            throw new IllegalStateException("AI URL configuration is invalid");
        RestTemplate client = seconds > 5 ? priceHttp : textHttp;
        return client.execute(
                uri,
                HttpMethod.POST,
                request -> {
                    request.getHeaders().setContentType(MediaType.APPLICATION_JSON);
                    request.getHeaders().setAccept(List.of(MediaType.APPLICATION_JSON));
                    json.writeValue(request.getBody(), input);
                },
                response -> {
                    byte[] bytes;
                    try (InputStream stream = response.getBody()) {
                        bytes = stream.readNBytes(1024 * 1024 + 1);
                    }
                    if (bytes.length > 1024 * 1024) throw new IOException("AI response too large");
                    if (response.getStatusCode().value() != 200)
                        throw new IOException("AI HTTP " + response.getStatusCode());
                    Map<String, Object> body = json.readValue(bytes, new TypeReference<>() {});
                    if (!(body.get("code") instanceof Number code)
                            || code.intValue() != 0
                            || !(body.get("data") instanceof Map<?, ?>))
                        throw new IOException("Invalid AI response");
                    return (Map<String, Object>) body.get("data");
                });
    }

    public Object text(long house) {
        var current = access.get("house", house, false);
        var result =
                db.optional(
                                "SELECT * FROM house_text_analysis WHERE house_id=#{p.id}",
                                args("id", house))
                        .orElse(null);
        if (result != null)
            result.put(
                    "stale",
                    !Objects.equals(
                            result.get("inputHash"),
                            AiFeatures.hash(db, AiFeatures.text(current))));
        return result;
    }

    public Object analyze(long house) {
        access.get("house", house, false);
        analyzeInternal(house, scope.user().id());
        return text(house);
    }

    public void analyzeInternal(long houseId, long actor) {
        var house =
                db.one("SELECT * FROM house WHERE id=#{p.id} AND deleted=0", args("id", houseId));
        var input = AiFeatures.text(house);
        String hash = AiFeatures.hash(db, input), request = UUID.randomUUID().toString();
        Long logId =
                tx.execute(
                        status -> {
                            var locked =
                                    db.one(
                                            "SELECT * FROM house WHERE id=#{p.id} AND deleted=0 FOR"
                                                    + " UPDATE",
                                            args("id", houseId));
                            if (!hash.equals(AiFeatures.hash(db, AiFeatures.text(locked))))
                                return null;
                            var old =
                                    db.optional(
                                            "SELECT * FROM house_text_analysis WHERE"
                                                    + " house_id=#{p.id} FOR UPDATE",
                                            args("id", houseId));
                            if (old.isPresent()
                                    && Objects.equals(hash, old.get().get("inputHash"))) {
                                var cached = old.get();
                                boolean running =
                                        "PENDING".equals(cached.get("status"))
                                                && cached.get("updatedAt")
                                                        instanceof LocalDateTime started
                                                && started.isAfter(
                                                        LocalDateTime.now().minusSeconds(30));
                                // Saving a house and the page retry may arrive together. Reuse one
                                // completed/in-flight analysis; failures and expired work can
                                // retry.
                                if ("SUCCESS".equals(cached.get("status")) || running) return null;
                            }
                            var values =
                                    args(
                                            "house_id",
                                            houseId,
                                            "request_id",
                                            request,
                                            "input_hash",
                                            hash,
                                            "status",
                                            "PENDING",
                                            "summary",
                                            null,
                                            "advantages",
                                            db.json(List.of()),
                                            "disadvantages",
                                            db.json(List.of()),
                                            "word_freq",
                                            db.json(List.of()),
                                            "error_message",
                                            null,
                                            "updated_at",
                                            LocalDateTime.now());
                            if (old.isEmpty()) db.insert("house_text_analysis", values);
                            else db.update("house_text_analysis", id(old.get(), "id"), values);
                            return requestLog(house, actor, request, "TEXT", input, hash);
                        });
        if (logId == null) return;
        Map<String, Object> output = null;
        String error = null;
        try {
            output = call(addresses.textBaseUrl(), "/ai/analyze", input, 5);
            if (!(output.get("summary") instanceof String)
                    || !(output.get("advantages") instanceof List<?>)
                    || !(output.get("disadvantages") instanceof List<?>)
                    || !(output.get("wordFreq") instanceof List<?>))
                throw new IOException("Invalid text analysis fields");
        } catch (Exception ex) {
            if (ex instanceof InterruptedException) Thread.currentThread().interrupt();
            error = "文案服务暂不可用，可稍后重试";
            log.warn("AI text failed house={} request={}", houseId, request, ex);
        }
        final Map<String, Object> result = output;
        final String failure = error;
        tx.executeWithoutResult(
                status -> {
                    var latest =
                            db.one(
                                    "SELECT * FROM house WHERE id=#{p.id} FOR UPDATE",
                                    args("id", houseId));
                    boolean stale =
                            truth(latest.get("deleted"))
                                    || !hash.equals(AiFeatures.hash(db, AiFeatures.text(latest)));
                    db.update(
                            "ai_request_log",
                            logId,
                            args(
                                    "status",
                                    stale ? "STALE" : failure == null ? "SUCCESS" : "FAILED",
                                    "result_snapshot",
                                    result == null ? null : db.json(result),
                                    "error_message",
                                    failure));
                    if (!stale) {
                        var p =
                                args(
                                        "house",
                                        houseId,
                                        "request",
                                        request,
                                        "hash",
                                        hash,
                                        "status",
                                        failure == null ? "SUCCESS" : "FAILED",
                                        "summary",
                                        result == null ? null : result.get("summary"),
                                        "advantages",
                                        db.json(
                                                result == null
                                                        ? List.of()
                                                        : result.get("advantages")),
                                        "disadvantages",
                                        db.json(
                                                result == null
                                                        ? List.of()
                                                        : result.get("disadvantages")),
                                        "words",
                                        db.json(
                                                result == null
                                                        ? List.of()
                                                        : result.get("wordFreq")),
                                        "error",
                                        failure,
                                        "now",
                                        LocalDateTime.now());
                        db.execute(
                                "UPDATE house_text_analysis SET"
                                    + " status=#{p.status},summary=#{p.summary},advantages=#{p.advantages},disadvantages=#{p.disadvantages},word_freq=#{p.words},error_message=#{p.error},updated_at=#{p.now}"
                                    + " WHERE house_id=#{p.house} AND request_id=#{p.request} AND"
                                    + " input_hash=#{p.hash}",
                                p);
                    }
                });
    }

    private long requestLog(
            Map<String, Object> house,
            long actor,
            String request,
            String kind,
            Object input,
            String hash) {
        return db.insert(
                "ai_request_log",
                args(
                        "request_id",
                        request,
                        "house_id",
                        id(house, "id"),
                        "kind",
                        kind,
                        "status",
                        "PENDING",
                        "input_snapshot",
                        db.json(input),
                        "feature_hash",
                        hash,
                        "requested_by",
                        actor,
                        "store_id",
                        house.get("storeId"),
                        "broker_id",
                        house.get("brokerId"),
                        "house_version",
                        house.get("version")));
    }

    public Object predict(long houseId) {
        var house = access.get("house", houseId, false);
        var features = AiFeatures.price(house);
        AiFeatures.validatePrice(features);
        String hash = AiFeatures.hash(db, features), request = UUID.randomUUID().toString();
        var input = new LinkedHashMap<>(features);
        input.put("schema_version", "1.0");
        input.put("request_id", request);
        input.put("house_id", Long.toString(houseId));
        long actor = scope.user().id();
        long logId = tx.execute(status -> requestLog(house, actor, request, "PRICE", input, hash));
        try {
            var result = call(addresses.priceBaseUrl(), "/ai/predict/price", input, 10);
            PricePredictionContract.validate(result);
            long record =
                    tx.execute(
                            status -> {
                                long id =
                                        db.insert(
                                                "house_price_pred",
                                                args(
                                                        "house_id",
                                                        houseId,
                                                        "request_id",
                                                        request,
                                                        "requested_by",
                                                        actor,
                                                        "schema_version",
                                                        "1.0",
                                                        "input_snapshot",
                                                        db.json(input),
                                                        "feature_hash",
                                                        hash,
                                                        "status",
                                                        result.get("status"),
                                                        "pred_usage",
                                                        result.get("usage"),
                                                        "price_basis",
                                                        result.get("price_basis"),
                                                        "estimated_unit_price_yuan_sqm",
                                                        result.get("estimated_unit_price_yuan_sqm"),
                                                        "estimated_total_price_yuan",
                                                        result.get("estimated_total_price_yuan"),
                                                        "price_interval",
                                                        result.get("price_interval") == null
                                                                ? null
                                                                : db.json(
                                                                        result.get(
                                                                                "price_interval")),
                                                        "reference_level",
                                                        result.get("reference_level"),
                                                        "comparable_sample_count",
                                                        result.get("comparable_sample_count"),
                                                        "quality_flags",
                                                        db.json(result.get("quality_flags")),
                                                        "explanations",
                                                        db.json(result.get("explanations")),
                                                        "model_version",
                                                        result.get("model_version"),
                                                        "data_version",
                                                        result.get("data_version"),
                                                        "data_observed_at",
                                                        date(result.get("data_observed_at")),
                                                        "generated_at",
                                                        date(result.get("generated_at")),
                                                        "notice",
                                                        result.get("notice")));
                                db.update(
                                        "ai_request_log",
                                        logId,
                                        args(
                                                "status",
                                                result.get("status"),
                                                "result_snapshot",
                                                db.json(result),
                                                "record_id",
                                                id));
                                return id;
                            });
            var saved =
                    db.one("SELECT * FROM house_price_pred WHERE id=#{p.id}", args("id", record));
            saved.put("usage", saved.remove("predUsage"));
            saved.put(
                    "stale",
                    !hash.equals(
                            AiFeatures.hash(
                                    db, AiFeatures.price(access.get("house", houseId, false)))));
            return saved;
        } catch (Exception ex) {
            if (ex instanceof InterruptedException) Thread.currentThread().interrupt();
            log.warn("AI price failed house={} request={}", houseId, request, ex);
            tx.executeWithoutResult(
                    status ->
                            db.update(
                                    "ai_request_log",
                                    logId,
                                    args("status", "FAILED", "error_message", "估价服务暂不可用，请人工核验")));
            return args(
                    "requestId",
                    request,
                    "status",
                    "FAILED",
                    "estimatedUnitPriceYuanSqm",
                    null,
                    "estimatedTotalPriceYuan",
                    null,
                    "priceInterval",
                    null,
                    "message",
                    "估价服务暂不可用，请人工核验");
        }
    }

    private static LocalDateTime date(Object value) {
        if (value == null) return null;
        String text = value.toString();
        try {
            return OffsetDateTime.parse(text)
                    .atZoneSameInstant(ZoneId.of("Asia/Shanghai"))
                    .toLocalDateTime();
        } catch (java.time.format.DateTimeParseException ignored) {
            return LocalDateTime.parse(text.replace(" ", "T"));
        }
    }

    public Object history(long house) {
        var current = access.get("house", house, false);
        String hash = AiFeatures.hash(db, AiFeatures.price(current));
        var rows =
                db.list(
                        "SELECT * FROM house_price_pred WHERE house_id=#{p.id} ORDER BY id DESC"
                                + " LIMIT 100",
                        args("id", house));
        rows.forEach(
                row -> {
                    row.put("usage", row.remove("predUsage"));
                    row.put("stale", !Objects.equals(row.get("featureHash"), hash));
                });
        return rows;
    }

    public Object requests(long house, String kind) {
        access.get("house", house, false);
        if (kind != null) Checks.choice(kind, "PRICE", "TEXT");
        return db.list(
                "SELECT * FROM ai_request_log WHERE house_id=#{p.id}"
                        + (kind == null ? "" : " AND kind=#{p.kind}")
                        + " ORDER BY id DESC LIMIT 100",
                args("id", house, "kind", kind));
    }
}
