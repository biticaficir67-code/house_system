package system;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.*;

import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.*;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.*;
import org.springframework.context.annotation.*;
import org.springframework.core.task.SyncTaskExecutor;
import org.springframework.http.*;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.client.*;
import org.springframework.test.web.servlet.*;
import org.springframework.web.client.RestTemplate;

import system.common.DataStore;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(BackendIntegrationTest.SynchronousAi.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class BackendIntegrationTest {
    @TestConfiguration(proxyBeanMethods = false)
    static class SynchronousAi {
        @Bean(name = "aiExecutor")
        SyncTaskExecutor synchronousAi() {
            return new SyncTaskExecutor();
        }
    }

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired DataStore db;
    @Autowired system.owner.OwnerReminderJob reminderJob;

    @Autowired
    @Qualifier("textRestTemplate")
    RestTemplate textHttp;

    @Autowired
    @Qualifier("priceRestTemplate")
    RestTemplate priceHttp;

    MockRestServiceServer textServer, priceServer;
    String admin;
    long store;
    final Map<String, Long> roles = new HashMap<>();
    final AtomicInteger names = new AtomicInteger();

    record User(String token, long id, long broker, long store) {}

    final String password = "TestPassword123!";

    @BeforeAll
    void initialize() throws Exception {
        textServer = MockRestServiceServer.bindTo(textHttp).ignoreExpectOrder(true).build();
        priceServer = MockRestServiceServer.bindTo(priceHttp).build();
        mvc.perform(
                        post("/api/auth/bootstrap")
                                .header("X-Bootstrap-Key", "integration-test-only")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        json.writeValueAsString(
                                                Map.of(
                                                        "username",
                                                        "admin",
                                                        "password",
                                                        password,
                                                        "displayName",
                                                        "测试管理员",
                                                        "storeName",
                                                        "总部"))))
                .andExpect(status().isOk());
        admin = login("admin", password);
        store = ok(HttpMethod.GET, "/api/auth/me", null, admin).get("storeId").asLong();
        for (JsonNode role : ok(HttpMethod.GET, "/api/roles", null, admin))
            roles.put(role.get("code").asText(), role.get("id").asLong());
    }

    @BeforeEach
    void resetHttp() {
        textServer.reset();
        priceServer.reset();
        textServer
                .expect(ExpectedCount.manyTimes(), requestTo("http://127.0.0.1:5001/ai/analyze"))
                .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));
    }

    String login(String username, String secret) throws Exception {
        return ok(
                        HttpMethod.POST,
                        "/api/auth/login",
                        Map.of("username", username, "password", secret),
                        null)
                .get("token")
                .asText();
    }

    JsonNode ok(HttpMethod method, String path, Object body, String token) throws Exception {
        var request = request(method, path);
        if (token != null) request.header("Authorization", "Bearer " + token);
        if (body != null)
            request.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body));
        MvcResult result = mvc.perform(request).andExpect(status().isOk()).andReturn();
        return json.readTree(result.getResponse().getContentAsString()).get("data");
    }

    User user(String role, long targetStore) throws Exception {
        String username = "test" + names.incrementAndGet();
        long id =
                ok(
                                HttpMethod.POST,
                                "/api/users",
                                Map.of(
                                        "username",
                                        username,
                                        "password",
                                        password,
                                        "displayName",
                                        username,
                                        "storeId",
                                        targetStore,
                                        "roleIds",
                                        List.of(roles.get(role))),
                                admin)
                        .asLong();
        String token = login(username, password);
        JsonNode me = ok(HttpMethod.GET, "/api/auth/me", null, token);
        return new User(token, id, me.get("brokerId").asLong(), targetStore);
    }

    long newStore() throws Exception {
        return ok(
                        HttpMethod.POST,
                        "/api/stores",
                        Map.of("name", "门店" + names.incrementAndGet(), "enabled", true),
                        admin)
                .asLong();
    }

    Map<String, Object> houseBody(User user) {
        var body = new LinkedHashMap<String, Object>();
        body.putAll(
                Map.of(
                        "city",
                        "杭州",
                        "district",
                        "滨江",
                        "communityName",
                        "集成测试小区",
                        "buildingAreaSqm",
                        new BigDecimal("89.60"),
                        "rooms",
                        3,
                        "halls",
                        2,
                        "bathrooms",
                        1,
                        "floorLevel",
                        "MIDDLE",
                        "totalFloors",
                        18,
                        "orientation",
                        List.of("S")));
        body.putAll(
                Map.of(
                        "decoration",
                        "FINE",
                        "hasElevator",
                        true,
                        "propertyUsage",
                        "RESIDENTIAL",
                        "listingPriceYuan",
                        new BigDecimal("3680000"),
                        "storeId",
                        user.store(),
                        "brokerId",
                        user.broker(),
                        "coreSelling",
                        "近地铁精装修",
                        "communityIntro",
                        "人车分流",
                        "surrounding",
                        "公园"));
        return body;
    }

    JsonNode house(User user) throws Exception {
        return ok(HttpMethod.POST, "/api/houses", houseBody(user), admin);
    }

    JsonNode publish(JsonNode house) throws Exception {
        String path = "/api/houses/" + house.get("id").asText();
        byte[] picture =
                java.util.Base64.getDecoder()
                        .decode(
                                "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+/p9sAAAAASUVORK5CYII=");
        mvc.perform(
                        multipart(path + "/media")
                                .file(
                                        new MockMultipartFile(
                                                "file", "cover.png", "image/png", picture))
                                .header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk());
        house = ok(HttpMethod.GET, path, null, admin);
        JsonNode verified =
                ok(
                        HttpMethod.PUT,
                        path + "/verification",
                        Map.of(
                                "approved",
                                true,
                                "reason",
                                "信息核验通过",
                                "version",
                                house.get("version").asLong()),
                        admin);
        return ok(
                HttpMethod.PUT,
                path + "/status",
                Map.of(
                        "status",
                        "ON_SALE",
                        "reason",
                        "挂牌销售",
                        "version",
                        verified.get("version").asLong()),
                admin);
    }

    JsonNode customer(User user) throws Exception {
        return ok(
                HttpMethod.POST,
                "/api/customers",
                Map.of(
                        "name",
                        "测试客户",
                        "phone",
                        "13000000000",
                        "tags",
                        List.of("刚需", "首套"),
                        "storeId",
                        user.store(),
                        "brokerId",
                        user.broker()),
                admin);
    }

    @Test
    void authenticationRevocationAndTraceId() throws Exception {
        mvc.perform(get("/api/houses"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401));
        User user = user("BROKER", store);
        ok(HttpMethod.GET, "/api/auth/me", null, user.token());
        ok(HttpMethod.POST, "/api/auth/logout", null, user.token());
        mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + user.token()))
                .andExpect(status().isUnauthorized());
        mvc.perform(
                        post("/api/auth/login")
                                .header("X-Request-Id", "trace-test")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"username\":\"no-user\",\"password\":\"invalid\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("X-Request-Id", "trace-test"))
                .andExpect(jsonPath("$.requestId").value("trace-test"));
        mvc.perform(
                        post("/api/auth/bootstrap")
                                .header("X-Bootstrap-Key", "integration-test-only")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        json.writeValueAsString(
                                                Map.of(
                                                        "username",
                                                        "other",
                                                        "password",
                                                        password,
                                                        "displayName",
                                                        "重复初始化",
                                                        "storeName",
                                                        "新店"))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void storeAndBrokerIsolationAndPermissionEnforcement() throws Exception {
        User own = user("BROKER", store),
                other = user("BROKER", store),
                remote = user("BROKER", newStore());
        JsonNode ownHouse = house(own), otherHouse = house(other), remoteHouse = house(remote);
        JsonNode page = ok(HttpMethod.GET, "/api/houses", null, own.token());
        assertThat(page.get("records"))
                .allSatisfy(
                        row -> assertThat(row.get("brokerId").asLong()).isEqualTo(own.broker()));
        mvc.perform(
                        get("/api/houses/" + otherHouse.get("id").asText())
                                .header("Authorization", "Bearer " + own.token()))
                .andExpect(status().isNotFound());
        mvc.perform(
                        get("/api/houses/" + remoteHouse.get("id").asText())
                                .header("Authorization", "Bearer " + own.token()))
                .andExpect(status().isNotFound());
        mvc.perform(
                        put("/api/houses/" + ownHouse.get("id").asText() + "/verification")
                                .header("Authorization", "Bearer " + own.token())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"approved\":true,\"reason\":\"非法核验\",\"version\":0}"))
                .andExpect(status().isForbidden());
        JsonNode brokerPage =
                ok(HttpMethod.GET, "/api/houses?storeId=" + remote.store(), null, own.token());
        assertThat(brokerPage.get("total").asLong()).isZero();
        mvc.perform(get("/api/users").header("Authorization", "Bearer " + own.token()))
                .andExpect(status().isForbidden());
        mvc.perform(
                        post("/api/stores")
                                .header("Authorization", "Bearer " + own.token())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"name\":\"非法门店\",\"enabled\":true}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void fullBusinessLifecycleMaintainsSeparatePricesAndAuditHistory() throws Exception {
        User broker = user("BROKER", store);
        JsonNode draft = house(broker);
        long houseId = draft.get("id").asLong();
        assertThat(draft.get("status").asText()).isEqualTo("OFF");
        assertThat(
                        db.count(
                                "SELECT COUNT(*) AS value FROM ai_request_log WHERE"
                                        + " house_id=#{p.id} AND status='FAILED'",
                                DataStore.args("id", houseId)))
                .isPositive();
        JsonNode published = publish(draft);
        JsonNode owner =
                ok(
                        HttpMethod.POST,
                        "/api/owners",
                        Map.of(
                                "name",
                                "业主",
                                "phone",
                                "13900000000",
                                "storeId",
                                store,
                                "brokerId",
                                broker.broker()),
                        admin);
        long ownerId = owner.get("id").asLong();
        JsonNode entrust =
                ok(
                        HttpMethod.POST,
                        "/api/owners/" + ownerId + "/entrustments",
                        Map.of(
                                "houseId",
                                houseId,
                                "startDate",
                                LocalDate.now(),
                                "endDate",
                                LocalDate.now().plusDays(2),
                                "exclusive",
                                true),
                        admin);
        assertThat(entrust.get("status").asText()).isEqualTo("ACTIVE");
        JsonNode reminders = ok(HttpMethod.GET, "/api/owners/reminders", null, broker.token());
        assertThat(reminders)
                .anySatisfy(
                        row ->
                                assertThat(row.get("entrustmentId").asLong())
                                        .isEqualTo(entrust.get("id").asLong()));
        ok(
                HttpMethod.POST,
                "/api/owners/" + ownerId + "/follow-ups",
                Map.of("type", "PHONE", "content", "核对委托信息"),
                broker.token());
        JsonNode customer = customer(broker);
        long customerId = customer.get("id").asLong();
        ok(
                HttpMethod.PUT,
                "/api/customers/" + customerId + "/demand",
                Map.of(
                        "districts",
                        List.of("滨江"),
                        "minPrice",
                        3000000,
                        "maxPrice",
                        4000000,
                        "minRooms",
                        3),
                broker.token());
        assertThat(
                        ok(
                                HttpMethod.GET,
                                "/api/customers/" + customerId + "/matches",
                                null,
                                broker.token()))
                .anySatisfy(row -> assertThat(row.get("id").asLong()).isEqualTo(houseId));
        JsonNode showing =
                ok(
                        HttpMethod.POST,
                        "/api/showings",
                        Map.of(
                                "houseId",
                                houseId,
                                "customerId",
                                customerId,
                                "brokerId",
                                broker.broker(),
                                "appointmentTime",
                                LocalDateTime.now().plusDays(1)),
                        broker.token());
        long showingId = showing.get("id").asLong();
        showing =
                ok(
                        HttpMethod.PUT,
                        "/api/showings/" + showingId + "/complete",
                        Map.of("actualTime", LocalDateTime.now().minusSeconds(1), "version", 0),
                        broker.token());
        showing =
                ok(
                        HttpMethod.POST,
                        "/api/showings/" + showingId + "/follow-ups",
                        Map.of(
                                "content",
                                "客户有意向",
                                "intention",
                                "HIGH",
                                "version",
                                showing.get("version").asLong()),
                        broker.token());
        assertThat(showing.get("followUps")).hasSize(1);
        JsonNode deal =
                ok(
                        HttpMethod.POST,
                        "/api/deals",
                        Map.of(
                                "houseId",
                                houseId,
                                "customerId",
                                customerId,
                                "brokerId",
                                broker.broker(),
                                "dealTime",
                                LocalDateTime.now().minusSeconds(1),
                                "transactionPriceYuan",
                                3500000),
                        broker.token());
        assertThat(deal.get("verified").asBoolean()).isFalse();
        assertThat(
                        ok(HttpMethod.GET, "/api/houses/" + houseId, null, admin)
                                .get("listingPriceYuan")
                                .decimalValue())
                .isEqualByComparingTo("3680000");
        assertThat(deal.get("transactionPriceYuan").decimalValue()).isEqualByComparingTo("3500000");
        assertThat(ok(HttpMethod.GET, "/api/houses/" + houseId, null, admin).get("status").asText())
                .isEqualTo("DEAL");
        long dealId = deal.get("id").asLong();
        deal =
                ok(
                        HttpMethod.PUT,
                        "/api/deals/" + dealId + "/verification",
                        Map.of("approved", true, "reason", "合同核验", "version", 0),
                        admin);
        JsonNode report =
                ok(
                        HttpMethod.GET,
                        "/api/reports/deals?storeId=" + store + "&groupBy=STORE",
                        null,
                        admin);
        assertThat(report.get("records")).isNotEmpty();
        ok(
                HttpMethod.PUT,
                "/api/deals/" + dealId + "/cancel",
                Map.of("reason", "合同撤销", "version", deal.get("version").asLong()),
                admin);
        assertThat(ok(HttpMethod.GET, "/api/houses/" + houseId, null, admin).get("status").asText())
                .isEqualTo("OFF");
        JsonNode logs = ok(HttpMethod.GET, "/api/houses/" + houseId + "/status-logs", null, admin);
        assertThat(logs)
                .anySatisfy(
                        row -> assertThat(row.get("eventType").asText()).isEqualTo("DEAL_CANCEL"));
    }

    @Test
    void staleVersionInvalidRangesAndUnknownFieldsAreRejected() throws Exception {
        User user = user("BROKER", store);
        JsonNode house = house(user);
        String path = "/api/houses/" + house.get("id").asText();
        var body = houseBody(user);
        body.put("version", 0);
        ok(HttpMethod.PUT, path, body, user.token());
        mvc.perform(
                        put(path)
                                .header("Authorization", "Bearer " + user.token())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(json.writeValueAsString(body)))
                .andExpect(status().isConflict());
        mvc.perform(get("/api/houses?page=0").header("Authorization", "Bearer " + admin))
                .andExpect(status().isBadRequest());
        mvc.perform(
                        get("/api/houses?minPrice=10&maxPrice=1")
                                .header("Authorization", "Bearer " + admin))
                .andExpect(status().isBadRequest());
        body.put("passwordHash", "bad");
        mvc.perform(
                        post("/api/houses")
                                .header("Authorization", "Bearer " + admin)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(json.writeValueAsString(body)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void mediaContentIsValidatedAndScopeProtected() throws Exception {
        User owner = user("BROKER", store), other = user("BROKER", store);
        JsonNode house = house(owner);
        String path = "/api/houses/" + house.get("id").asText() + "/media";
        mvc.perform(
                        multipart(path)
                                .file(
                                        new MockMultipartFile(
                                                "file",
                                                "fake.png",
                                                "image/png",
                                                "<script>bad</script>".getBytes()))
                                .header("Authorization", "Bearer " + owner.token()))
                .andExpect(status().isBadRequest());
        byte[] png =
                Base64.getDecoder()
                        .decode(
                                "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAusB9Y9Zl1sAAAAASUVORK5CYII=");
        MvcResult upload =
                mvc.perform(
                                multipart(path)
                                        .file(
                                                new MockMultipartFile(
                                                        "file", "safe.png", "image/png", png))
                                        .header("Authorization", "Bearer " + owner.token()))
                        .andExpect(status().isOk())
                        .andReturn();
        long media =
                json.readTree(upload.getResponse().getContentAsString())
                        .path("data")
                        .path("id")
                        .asLong();
        mvc.perform(
                        get(path + "/" + media + "/content")
                                .header("Authorization", "Bearer " + other.token()))
                .andExpect(status().isNotFound());
        mvc.perform(
                        get(path + "/" + media + "/content")
                                .header("Authorization", "Bearer " + owner.token()))
                .andExpect(status().isOk())
                .andExpect(content().bytes(png));
        ok(HttpMethod.DELETE, path + "/" + media, null, owner.token());
        mvc.perform(
                        get(path + "/" + media + "/content")
                                .header("Authorization", "Bearer " + owner.token()))
                .andExpect(status().isNotFound());
    }

    @Test
    void aiSnapshotsKeepUnavailableNullAndDetectOutdatedPredictions() throws Exception {
        User broker = user("BROKER", store);
        JsonNode house = house(broker);
        long id = house.get("id").asLong();
        String path = "/api/ai/houses/" + id + "/price-predictions";
        Map<String, Object> result = new LinkedHashMap<>();
        result.putAll(
                Map.of(
                        "status",
                        "UNAVAILABLE",
                        "usage",
                        "HISTORICAL_DEMO",
                        "price_basis",
                        "LISTING_UNVERIFIED",
                        "model_version",
                        "test-model",
                        "data_version",
                        "test-data",
                        "generated_at",
                        OffsetDateTime.now().toString(),
                        "quality_flags",
                        List.of("INSUFFICIENT_REFERENCE_SAMPLES"),
                        "explanations",
                        List.of("样本不足")));
        result.put("estimated_unit_price_yuan_sqm", null);
        result.put("estimated_total_price_yuan", null);
        result.put("price_interval", null);
        priceServer
                .expect(requestTo("http://127.0.0.1:8001/ai/predict/price"))
                .andExpect(
                        request -> {
                            String requestBody =
                                    ((org.springframework.mock.http.client.MockClientHttpRequest)
                                                    request)
                                            .getBodyAsString();
                            JsonNode payload = json.readTree(requestBody);
                            assertThat(payload.has("listing_price_yuan")).isFalse();
                            assertThat(payload.path("orientation").isArray()).isTrue();
                            assertThat(payload.path("house_id").asText())
                                    .isEqualTo(Long.toString(id));
                        })
                .andRespond(
                        withSuccess(
                                json.writeValueAsString(Map.of("code", 0, "data", result)),
                                MediaType.APPLICATION_JSON));
        JsonNode prediction = ok(HttpMethod.POST, path, null, broker.token());
        assertThat(prediction.get("status").asText()).isEqualTo("UNAVAILABLE");
        assertThat(prediction.get("estimatedTotalPriceYuan").isNull()).isTrue();
        assertThat(prediction.get("stale").asBoolean()).isFalse();
        var changed = houseBody(broker);
        changed.put("rooms", 4);
        changed.put("version", 0);
        ok(HttpMethod.PUT, "/api/houses/" + id, changed, broker.token());
        assertThat(ok(HttpMethod.GET, path, null, broker.token()).get(0).get("stale").asBoolean())
                .isTrue();
        priceServer.verify();
        priceServer.reset();
        priceServer
                .expect(requestTo("http://127.0.0.1:8001/ai/predict/price"))
                .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));
        JsonNode failed = ok(HttpMethod.POST, path, null, broker.token());
        assertThat(failed.get("status").asText()).isEqualTo("FAILED");
        assertThat(failed.get("estimatedTotalPriceYuan").isNull()).isTrue();
        priceServer.verify();
    }

    @Test
    void concurrentAppointmentsCannotDoubleBookABroker() throws Exception {
        User broker = user("BROKER", store);
        JsonNode a = publish(house(broker)),
                b = publish(house(broker)),
                customer = customer(broker);
        LocalDateTime time = LocalDateTime.now().plusDays(3);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Integer>> results = new ArrayList<>();
        try {
            for (JsonNode house : List.of(a, b))
                results.add(
                        executor.submit(
                                () -> {
                                    start.await();
                                    return mvc.perform(
                                                    post("/api/showings")
                                                            .header(
                                                                    "Authorization",
                                                                    "Bearer " + broker.token())
                                                            .contentType(MediaType.APPLICATION_JSON)
                                                            .content(
                                                                    json.writeValueAsString(
                                                                            Map.of(
                                                                                    "houseId",
                                                                                    house.get("id")
                                                                                            .asLong(),
                                                                                    "customerId",
                                                                                    customer.get(
                                                                                                    "id")
                                                                                            .asLong(),
                                                                                    "brokerId",
                                                                                    broker.broker(),
                                                                                    "appointmentTime",
                                                                                    time))))
                                            .andReturn()
                                            .getResponse()
                                            .getStatus();
                                }));
            start.countDown();
            assertThat(
                            List.of(
                                    results.get(0).get(20, TimeUnit.SECONDS),
                                    results.get(1).get(20, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(200, 400);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void passwordResetInvalidatesTokensAndCorsIsConfigured() throws Exception {
        User user = user("BROKER", store);
        ok(
                HttpMethod.PUT,
                "/api/users/" + user.id() + "/password",
                Map.of("password", "NewTestPassword123!"),
                admin);
        mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + user.token()))
                .andExpect(status().isUnauthorized());
        mvc.perform(
                        options("/api/houses")
                                .header("Origin", "http://127.0.0.1:5174")
                                .header("Access-Control-Request-Method", "POST")
                                .header(
                                        "Access-Control-Request-Headers",
                                        "authorization,content-type"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://127.0.0.1:5174"));
    }

    @Test
    void crossStoreRelationsFailWithoutChangingHouseState() throws Exception {
        User first = user("BROKER", store), second = user("BROKER", newStore());
        JsonNode house = publish(house(first)), customer = customer(second);
        long id = house.get("id").asLong();
        var body =
                Map.of(
                        "houseId",
                        id,
                        "customerId",
                        customer.get("id").asLong(),
                        "brokerId",
                        first.broker(),
                        "dealTime",
                        LocalDateTime.now().minusSeconds(1),
                        "transactionPriceYuan",
                        3500000);
        mvc.perform(
                        post("/api/deals")
                                .header("Authorization", "Bearer " + admin)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(json.writeValueAsString(body)))
                .andExpect(status().isBadRequest());
        assertThat(ok(HttpMethod.GET, "/api/houses/" + id, null, admin).get("status").asText())
                .isEqualTo("ON_SALE");
        assertThat(
                        db.count(
                                "SELECT COUNT(*) AS value FROM house_deal WHERE house_id=#{p.id}",
                                DataStore.args("id", id)))
                .isZero();
        mvc.perform(
                        post("/api/showings")
                                .header("Authorization", "Bearer " + admin)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        json.writeValueAsString(
                                                Map.of(
                                                        "houseId",
                                                        id,
                                                        "customerId",
                                                        customer.get("id").asLong(),
                                                        "brokerId",
                                                        first.broker(),
                                                        "appointmentTime",
                                                        LocalDateTime.now().plusDays(5)))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void reminderSweepExpiresContractsAndDoesNotDuplicateReminders() throws Exception {
        User broker = user("BROKER", store);
        JsonNode house = house(broker);
        JsonNode owner =
                ok(
                        HttpMethod.POST,
                        "/api/owners",
                        Map.of(
                                "name",
                                "待到期业主",
                                "phone",
                                "13800000000",
                                "storeId",
                                store,
                                "brokerId",
                                broker.broker()),
                        admin);
        long ownerId = owner.get("id").asLong();
        JsonNode entrust =
                ok(
                        HttpMethod.POST,
                        "/api/owners/" + ownerId + "/entrustments",
                        Map.of(
                                "houseId",
                                house.get("id").asLong(),
                                "startDate",
                                LocalDate.now().minusDays(3),
                                "endDate",
                                LocalDate.now().plusDays(1),
                                "exclusive",
                                false),
                        admin);
        long id = entrust.get("id").asLong();
        db.update(
                "owner_entrustment", id, DataStore.args("end_date", LocalDate.now().minusDays(1)));
        reminderJob.refresh();
        reminderJob.refresh();
        assertThat(
                        db.one(
                                        "SELECT status FROM owner_entrustment WHERE id=#{p.id}",
                                        DataStore.args("id", id))
                                .get("status"))
                .isEqualTo("EXPIRED");
        assertThat(
                        db.count(
                                "SELECT COUNT(*) AS value FROM owner_reminder WHERE"
                                        + " entrustment_id=#{p.id}",
                                DataStore.args("id", id)))
                .isEqualTo(1);
    }

    @Test
    void successfulAiResultsArePersistedAndReadsDoNotCallPython() throws Exception {
        textServer.reset();
        var analysis =
                Map.of(
                        "summary",
                        "近地铁",
                        "advantages",
                        List.of("近地铁"),
                        "disadvantages",
                        List.of(),
                        "wordFreq",
                        List.of(Map.of("name", "地铁", "value", 1)),
                        "topic",
                        Map.of("internal", "hidden"),
                        "method",
                        "test");
        textServer
                .expect(ExpectedCount.once(), requestTo("http://127.0.0.1:5001/ai/analyze"))
                .andRespond(
                        withSuccess(
                                json.writeValueAsString(Map.of("code", 0, "data", analysis)),
                                MediaType.APPLICATION_JSON));
        User broker = user("BROKER", store);
        JsonNode house = house(broker);
        long id = house.get("id").asLong();
        JsonNode saved =
                ok(HttpMethod.GET, "/api/ai/houses/" + id + "/text-analysis", null, broker.token());
        assertThat(saved.get("status").asText()).isEqualTo("SUCCESS");
        assertThat(saved.get("advantages").get(0).asText()).isEqualTo("近地铁");
        assertThat(saved.has("topic")).isFalse();
        ok(HttpMethod.GET, "/api/houses/" + id, null, broker.token());
        ok(HttpMethod.GET, "/api/houses/" + id, null, broker.token());
        textServer.verify();
        var prediction =
                Map.ofEntries(
                        Map.entry("status", "DEMO_ONLY"),
                        Map.entry("usage", "HISTORICAL_DEMO"),
                        Map.entry("price_basis", "LISTING_UNVERIFIED"),
                        Map.entry("estimated_unit_price_yuan_sqm", 40000),
                        Map.entry("estimated_total_price_yuan", 3584000),
                        Map.entry(
                                "price_interval",
                                Map.of(
                                        "lower_total_price_yuan",
                                        3000000,
                                        "upper_total_price_yuan",
                                        4000000,
                                        "nominal_coverage",
                                        0.9)),
                        Map.entry("model_version", "test"),
                        Map.entry("data_version", "test"),
                        Map.entry("generated_at", OffsetDateTime.now().toString()),
                        Map.entry("quality_flags", List.of("HISTORICAL_DATA")),
                        Map.entry("explanations", List.of("历史参考")));
        priceServer
                .expect(requestTo("http://127.0.0.1:8001/ai/predict/price"))
                .andRespond(
                        withSuccess(
                                json.writeValueAsString(Map.of("code", 0, "data", prediction)),
                                MediaType.APPLICATION_JSON));
        JsonNode result =
                ok(
                        HttpMethod.POST,
                        "/api/ai/houses/" + id + "/price-predictions",
                        null,
                        broker.token());
        assertThat(result.get("status").asText()).isEqualTo("DEMO_ONLY");
        assertThat(result.get("usage").asText()).isEqualTo("HISTORICAL_DEMO");
        assertThat(result.get("priceInterval").get("lower_total_price_yuan").asInt())
                .isEqualTo(3000000);
        assertThat(
                        ok(HttpMethod.GET, "/api/houses/" + id, null, broker.token())
                                .get("listingPriceYuan")
                                .decimalValue())
                .isEqualByComparingTo("3680000");
        priceServer.verify();
    }

    @Test
    void swaggerAndScopedReportsAreUsable() throws Exception {
        mvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/houses']").exists())
                .andExpect(jsonPath("$.components.securitySchemes.bearerToken").exists());
        JsonNode overview =
                ok(
                        HttpMethod.GET,
                        "/api/reports/overview?from="
                                + LocalDate.now().minusDays(1)
                                + "&to="
                                + LocalDate.now()
                                + "&storeId="
                                + store,
                        null,
                        admin);
        assertThat(overview.has("inventory")).isTrue();
        ok(HttpMethod.GET, "/api/reports/showings?groupBy=MONTH", null, admin);
        mvc.perform(get("/api/reports/houses/export").header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk())
                .andExpect(
                        header().string("Content-Disposition", "attachment; filename=houses.csv"));
        mvc.perform(
                        get("/api/reports/prices?groupBy=malicious")
                                .header("Authorization", "Bearer " + admin))
                .andExpect(status().isBadRequest());
    }
}
