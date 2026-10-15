package cn.csu.housesystem.house.mapper;

import cn.csu.housesystem.house.model.House;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Result;
import org.apache.ibatis.annotations.Results;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface HouseMapper {
    String COLUMNS = "id, owner_id, title, city, district, community_name, business_area, " +
            "building_area_sqm, rooms, halls, bathrooms, floor_level, total_floors, orientation, " +
            "decoration, building_type, has_elevator, property_usage, listing_price_yuan, status, " +
            "core_selling, community_intro, surrounding, analysis_summary, analysis_advantages, " +
            "analysis_disadvantages, analysis_word_freq, created_at, updated_at";

    @Insert("""
            INSERT INTO house (owner_id, title, city, district, community_name, business_area,
              building_area_sqm, rooms, halls, bathrooms, floor_level, total_floors, orientation,
              decoration, building_type, has_elevator, property_usage, listing_price_yuan, status,
              core_selling, community_intro, surrounding, created_at, updated_at)
            VALUES (#{ownerId}, #{title}, #{city}, #{district}, #{communityName}, #{businessArea},
              #{buildingAreaSqm}, #{rooms}, #{halls}, #{bathrooms}, #{floorLevel}, #{totalFloors},
              #{orientation,typeHandler=cn.csu.housesystem.house.mapper.StringListTypeHandler},
              #{decoration}, #{buildingType}, #{hasElevator}, #{propertyUsage}, #{listingPriceYuan},
              #{status}, #{coreSelling}, #{communityIntro}, #{surrounding}, #{createdAt}, #{updatedAt})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(House house);

    @Select("SELECT " + COLUMNS + " FROM house WHERE id = #{id}")
    @Results(id = "houseResult", value = {
            @Result(column = "orientation", property = "orientation",
                    typeHandler = StringListTypeHandler.class)
    })
    House findById(@Param("id") long id);

    @Select("""
            <script>
            SELECT ${columns} FROM house
            <where>
              <if test="keyword != null and keyword != ''">
                AND (title LIKE CONCAT('%', #{keyword}, '%')
                  OR community_name LIKE CONCAT('%', #{keyword}, '%')
                  OR district LIKE CONCAT('%', #{keyword}, '%'))
              </if>
              <if test="status != null and status != ''">AND status = #{status}</if>
            </where>
            ORDER BY created_at DESC, id DESC LIMIT #{limit} OFFSET #{offset}
            </script>
            """)
    @Results(id = "houseListResult", value = {
            @Result(column = "orientation", property = "orientation",
                    typeHandler = StringListTypeHandler.class)
    })
    List<House> findPage(@Param("columns") String columns, @Param("keyword") String keyword,
                         @Param("status") String status, @Param("limit") int limit,
                         @Param("offset") long offset);

    @Select("""
            <script>
            SELECT COUNT(*) FROM house
            <where>
              <if test="keyword != null and keyword != ''">
                AND (title LIKE CONCAT('%', #{keyword}, '%')
                  OR community_name LIKE CONCAT('%', #{keyword}, '%')
                  OR district LIKE CONCAT('%', #{keyword}, '%'))
              </if>
              <if test="status != null and status != ''">AND status = #{status}</if>
            </where>
            </script>
            """)
    long count(@Param("keyword") String keyword, @Param("status") String status);

    @Update("""
            UPDATE house SET owner_id=#{ownerId}, title=#{title}, city=#{city}, district=#{district},
              community_name=#{communityName}, business_area=#{businessArea},
              building_area_sqm=#{buildingAreaSqm}, rooms=#{rooms}, halls=#{halls}, bathrooms=#{bathrooms},
              floor_level=#{floorLevel}, total_floors=#{totalFloors},
              orientation=#{orientation,typeHandler=cn.csu.housesystem.house.mapper.StringListTypeHandler},
              decoration=#{decoration}, building_type=#{buildingType}, has_elevator=#{hasElevator},
              property_usage=#{propertyUsage}, listing_price_yuan=#{listingPriceYuan}, status=#{status},
              core_selling=#{coreSelling}, community_intro=#{communityIntro}, surrounding=#{surrounding},
              updated_at=#{updatedAt}
            WHERE id=#{id}
            """)
    int update(House house);

    @Update("UPDATE house SET status=#{status}, updated_at=#{updatedAt} WHERE id=#{id}")
    int updateStatus(@Param("id") long id, @Param("status") String status,
                     @Param("updatedAt") java.time.LocalDateTime updatedAt);

    @org.apache.ibatis.annotations.Delete("DELETE FROM house WHERE id=#{id}")
    int delete(@Param("id") long id);
}
