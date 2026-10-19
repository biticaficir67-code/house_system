package system.house;

import system.common.BusinessException;
import system.common.PageResponse;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class HouseService {
    private static final int MAX_PAGE_SIZE = 100;
    private final HouseMapper houseMapper;

    public HouseService(HouseMapper houseMapper) {
        this.houseMapper = houseMapper;
    }

    public PageResponse<HouseSummary> search(String district, String communityName, String status,
                                             int page, int size) {
        if (page < 1 || page > 1_000_000 || size < 1 || size > MAX_PAGE_SIZE) {
            throw new BusinessException(400, "page 范围为 1-1000000，size 范围为 1-100");
        }
        String normalizedStatus = StringUtils.hasText(status) ? status.trim().toUpperCase() : null;
        if (normalizedStatus != null && !List.of("ON_SALE", "OFF", "DEAL").contains(normalizedStatus)) {
            throw new BusinessException(400, "status 仅支持 ON_SALE、OFF、DEAL");
        }
        String normalizedDistrict = StringUtils.hasText(district) ? district.trim() : null;
        String normalizedCommunity = StringUtils.hasText(communityName) ? communityName.trim() : null;
        int offset = Math.multiplyExact(page - 1, size);
        List<HouseSummary> records = houseMapper.search(
                normalizedDistrict, normalizedCommunity, normalizedStatus, offset, size);
        long total = houseMapper.count(normalizedDistrict, normalizedCommunity, normalizedStatus);
        return new PageResponse<>(total, page, size, records);
    }

    public HouseSummary findById(long id) {
        if (id < 1) {
            throw new BusinessException(400, "房源 ID 必须为正数");
        }
        HouseSummary house = houseMapper.findById(id);
        if (house == null) {
            throw new BusinessException(404, "房源不存在");
        }
        return house;
    }
}
