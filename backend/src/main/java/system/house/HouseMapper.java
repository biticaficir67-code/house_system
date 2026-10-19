package system.house;

import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface HouseMapper {
    List<HouseSummary> search(@Param("district") String district,
                              @Param("communityName") String communityName,
                              @Param("status") String status,
                              @Param("offset") int offset,
                              @Param("size") int size);

    long count(@Param("district") String district,
               @Param("communityName") String communityName,
               @Param("status") String status);

    HouseSummary findById(@Param("id") long id);
}
