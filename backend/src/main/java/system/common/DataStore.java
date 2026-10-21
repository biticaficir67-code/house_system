package system.common;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.stereotype.Repository;

import java.nio.charset.StandardCharsets;
import java.sql.Clob;
import java.sql.Timestamp;
import java.util.*;
import java.util.stream.Collectors;

/** Internal parameterized SQL adapter. SQL identifiers never come from HTTP requests. */
@Repository
public class DataStore {
    private static final Set<String> JSON_COLUMNS =
            Set.of(
                    "orientation",
                    "tags",
                    "permissions",
                    "before_snapshot",
                    "after_snapshot",
                    "districts",
                    "price_interval",
                    "input_snapshot",
                    "result_snapshot",
                    "quality_flags",
                    "explanations",
                    "advantages",
                    "disadvantages",
                    "word_freq");
    private final DataMapper mapper;
    private final ObjectMapper json;

    public DataStore(DataMapper mapper, ObjectMapper json) {
        this.mapper = mapper;
        this.json = json;
    }

    public static Map<String, Object> args(Object... entries) {
        Map<String, Object> values = new LinkedHashMap<>();
        if (entries.length % 2 != 0) throw new IllegalArgumentException("Unpaired SQL parameters");
        for (int i = 0; i < entries.length; i += 2) values.put((String) entries[i], entries[i + 1]);
        return values;
    }

    public List<Map<String, Object>> list(String sql, Map<String, Object> parameters) {
        return mapper.query(sql, parameters).stream().map(this::normalize).toList();
    }

    public Map<String, Object> one(String sql, Map<String, Object> parameters) {
        List<Map<String, Object>> result = list(sql, parameters);
        if (result.isEmpty()) throw new BusinessException(404, "记录不存在或无权访问");
        return result.get(0);
    }

    public Optional<Map<String, Object>> optional(String sql, Map<String, Object> parameters) {
        return list(sql, parameters).stream().findFirst();
    }

    public long count(String sql, Map<String, Object> parameters) {
        return ((Number) one(sql, parameters).get("value")).longValue();
    }

    public int execute(String sql, Map<String, Object> parameters) {
        return mapper.execute(sql, parameters);
    }

    public long insert(String table, Map<String, Object> values) {
        identifier(table);
        values.keySet().forEach(DataStore::identifier);
        String columns = String.join(",", values.keySet());
        String placeholders =
                values.keySet().stream()
                        .map(key -> "#{p." + key + "}")
                        .collect(Collectors.joining(","));
        Map<String, Object> parameters = new LinkedHashMap<>(values);
        mapper.insert(
                "INSERT INTO " + table + " (" + columns + ") VALUES (" + placeholders + ")",
                parameters);
        return ((Number) parameters.get("id")).longValue();
    }

    public int update(String table, long id, Map<String, Object> values) {
        identifier(table);
        values.keySet().forEach(DataStore::identifier);
        String assignments =
                values.keySet().stream()
                        .map(key -> key + "=#{p." + key + "}")
                        .collect(Collectors.joining(","));
        Map<String, Object> parameters = new LinkedHashMap<>(values);
        parameters.put("_id", id);
        return execute(
                "UPDATE " + table + " SET " + assignments + " WHERE id=#{p._id}", parameters);
    }

    private static void identifier(String name) {
        if (!name.matches("[a-z][a-z0-9_]*"))
            throw new IllegalArgumentException("Invalid internal SQL identifier");
    }

    public String json(Object value) {
        try {
            return json.writeValueAsString(value);
        } catch (Exception ex) {
            throw new IllegalArgumentException("Invalid JSON value", ex);
        }
    }

    public Object parse(String value) {
        if (value == null) return null;
        try {
            return json.readValue(value, new TypeReference<Object>() {});
        } catch (Exception ex) {
            throw new IllegalStateException("Stored JSON is invalid", ex);
        }
    }

    private Map<String, Object> normalize(Map<String, Object> source) {
        Map<String, Object> row = new LinkedHashMap<>();
        source.forEach(
                (key, value) -> {
                    String column = key.toLowerCase(Locale.ROOT);
                    Object converted = value;
                    if (converted instanceof byte[] bytes)
                        converted = new String(bytes, StandardCharsets.UTF_8);
                    if (converted instanceof Clob clob) {
                        try {
                            converted = clob.getSubString(1, Math.toIntExact(clob.length()));
                        } catch (Exception ex) {
                            throw new IllegalStateException(ex);
                        }
                    }
                    if (JSON_COLUMNS.contains(column) && converted instanceof String text)
                        converted = parse(text);
                    if ("has_elevator".equals(column) && converted != null)
                        converted = truth(converted);
                    if (converted instanceof Timestamp timestamp)
                        converted = timestamp.toLocalDateTime();
                    if (converted instanceof java.sql.Date date) converted = date.toLocalDate();
                    StringBuilder camel = new StringBuilder();
                    boolean uppercase = false;
                    for (char character : column.toCharArray()) {
                        if (character == '_') {
                            uppercase = true;
                            continue;
                        }
                        camel.append(uppercase ? Character.toUpperCase(character) : character);
                        uppercase = false;
                    }
                    row.put(camel.toString(), converted);
                });
        return row;
    }

    public static long id(Map<String, Object> row, String field) {
        return ((Number) row.get(field)).longValue();
    }

    public static boolean truth(Object value) {
        return Boolean.TRUE.equals(value)
                || value instanceof Number number && number.intValue() != 0;
    }
}
