package cn.csu.housesystem.house.mapper;

import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class StringListTypeHandler extends BaseTypeHandler<List<String>> {
    private static final Pattern JSON_STRING = Pattern.compile("\"([^\"]*)\"");

    @Override
    public void setNonNullParameter(PreparedStatement statement, int index, List<String> value,
                                   JdbcType jdbcType) throws SQLException {
        statement.setString(index, "[" + value.stream().map(item -> "\"" + item + "\"")
                .reduce((left, right) -> left + "," + right).orElse("") + "]");
    }

    @Override
    public List<String> getNullableResult(ResultSet resultSet, String columnName) throws SQLException {
        return parse(resultSet.getString(columnName));
    }

    @Override
    public List<String> getNullableResult(ResultSet resultSet, int columnIndex) throws SQLException {
        return parse(resultSet.getString(columnIndex));
    }

    @Override
    public List<String> getNullableResult(CallableStatement statement, int columnIndex) throws SQLException {
        return parse(statement.getString(columnIndex));
    }

    private List<String> parse(String value) throws SQLException {
        if (value == null || value.isBlank()) return List.of();
        Matcher matcher = JSON_STRING.matcher(value);
        List<String> values = new ArrayList<>();
        while (matcher.find()) values.add(matcher.group(1));
        if (values.isEmpty() && !"[]".equals(value.trim())) {
            throw new SQLException("数据库中的房屋朝向 JSON 格式错误");
        }
        return values;
    }
}
