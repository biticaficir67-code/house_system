package cn.csu.housesystem.house.service;

import cn.csu.housesystem.common.BusinessException;
import cn.csu.housesystem.house.mapper.HouseMapper;
import cn.csu.housesystem.house.model.House;
import cn.csu.housesystem.house.model.HouseUpsertRequest;
import cn.csu.housesystem.house.model.PageData;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

@Service
public class HouseService {
    private static final Set<String> STATUSES = Set.of("PENDING_REVIEW", "ON_SALE", "OFF_SALE", "SOLD");
    private final HouseMapper houseMapper;

    public HouseService(HouseMapper houseMapper) {
        this.houseMapper = houseMapper;
    }

    public PageData<House> findPage(int page, int size, String keyword, String status) {
        validateStatusFilter(status);
        String normalizedKeyword = keyword == null ? null : keyword.trim();
        long offset = (long) (page - 1) * size;
        return new PageData<>(houseMapper.findPage(HouseMapper.COLUMNS, normalizedKeyword, status,
                size, offset), houseMapper.count(normalizedKeyword, status), page, size);
    }

    public House findById(long id) {
        House house = houseMapper.findById(id);
        if (house == null) throw notFound(id);
        return house;
    }

    @Transactional
    public House create(HouseUpsertRequest request) {
        LocalDateTime now = LocalDateTime.now();
        House house = fromRequest(request);
        house.setStatus("PENDING_REVIEW");
        house.setCreatedAt(now);
        house.setUpdatedAt(now);
        houseMapper.insert(house);
        return findById(house.getId());
    }

    @Transactional
    public House update(long id, HouseUpsertRequest request) {
        House existing = findById(id);
        House house = fromRequest(request);
        house.setId(id);
        house.setStatus(existing.getStatus());
        house.setCreatedAt(existing.getCreatedAt());
        house.setUpdatedAt(LocalDateTime.now());
        houseMapper.update(house);
        return findById(id);
    }

    @Transactional
    public House changeStatus(long id, String targetStatus) {
        if (targetStatus == null || !STATUSES.contains(targetStatus)) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, 40002, "房源状态不合法");
        }
        House existing = findById(id);
        if (!canTransition(existing.getStatus(), targetStatus)) {
            throw new BusinessException(HttpStatus.CONFLICT, 40901,
                    "不允许从 " + existing.getStatus() + " 流转到 " + targetStatus);
        }
        houseMapper.updateStatus(id, targetStatus, LocalDateTime.now());
        return findById(id);
    }

    @Transactional
    public void delete(long id) {
        findById(id);
        houseMapper.delete(id);
    }

    private House fromRequest(HouseUpsertRequest request) {
        validateOrientation(request.orientation());
        House house = new House();
        house.setOwnerId(request.ownerId());
        house.setTitle(request.title().trim());
        house.setCity(request.city().trim());
        house.setDistrict(trimToNull(request.district()));
        house.setCommunityName(request.communityName().trim());
        house.setBusinessArea(trimToNull(request.businessArea()));
        house.setBuildingAreaSqm(request.buildingAreaSqm());
        house.setRooms(request.rooms());
        house.setHalls(request.halls());
        house.setBathrooms(request.bathrooms());
        house.setFloorLevel(trimToNull(request.floorLevel()));
        house.setTotalFloors(request.totalFloors());
        house.setOrientation(request.orientation() == null ? List.of() : List.copyOf(request.orientation()));
        house.setDecoration(trimToNull(request.decoration()));
        house.setBuildingType(trimToNull(request.buildingType()));
        house.setHasElevator(request.hasElevator());
        house.setPropertyUsage(trimToNull(request.propertyUsage()));
        house.setListingPriceYuan(request.listingPriceYuan());
        house.setCoreSelling(trimToNull(request.coreSelling()));
        house.setCommunityIntro(trimToNull(request.communityIntro()));
        house.setSurrounding(trimToNull(request.surrounding()));
        return house;
    }

    private void validateOrientation(List<String> values) {
        if (values == null || values.isEmpty()) return;
        Set<String> allowed = Set.of("E", "S", "W", "N", "NE", "NW", "SE", "SW", "UNKNOWN");
        if ((values.contains("UNKNOWN") && values.size() > 1)
                || values.stream().anyMatch(v -> v == null || !allowed.contains(v))) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, 40002, "朝向必须使用 E/S/W/N/NE/NW/SE/SW；UNKNOWN 只能单独使用");
        }
    }

    private void validateStatusFilter(String status) {
        if (status != null && !status.isBlank() && !STATUSES.contains(status)) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, 40002, "房源状态不合法");
        }
    }

    private boolean canTransition(String from, String to) {
        return switch (from) {
            case "PENDING_REVIEW" -> Set.of("ON_SALE", "OFF_SALE").contains(to);
            case "ON_SALE" -> Set.of("OFF_SALE", "SOLD").contains(to);
            case "OFF_SALE" -> "ON_SALE".equals(to);
            default -> false;
        };
    }

    private String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private BusinessException notFound(long id) {
        return new BusinessException(HttpStatus.NOT_FOUND, 40401, "房源不存在：" + id);
    }
}
