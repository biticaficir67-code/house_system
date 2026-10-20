package system.common;

import org.apache.ibatis.annotations.*;

import java.util.List;
import java.util.Map;

@Mapper
public interface DataMapper {
    class Sql {
        public static String statement(Map<String, Object> parameters) {
            return (String) parameters.get("sql");
        }
    }

    @SelectProvider(type = Sql.class, method = "statement")
    List<Map<String, Object>> query(
            @Param("sql") String sql, @Param("p") Map<String, Object> parameters);

    @UpdateProvider(type = Sql.class, method = "statement")
    int execute(@Param("sql") String sql, @Param("p") Map<String, Object> parameters);

    @InsertProvider(type = Sql.class, method = "statement")
    @Options(useGeneratedKeys = true, keyProperty = "p.id", keyColumn = "id")
    int insert(@Param("sql") String sql, @Param("p") Map<String, Object> parameters);
}
