package cn.csu.housesystem.house.controller;

import cn.csu.housesystem.common.ApiResult;
import cn.csu.housesystem.house.model.House;
import cn.csu.housesystem.house.model.HouseUpsertRequest;
import cn.csu.housesystem.house.model.HouseStatusRequest;
import cn.csu.housesystem.house.model.PageData;
import cn.csu.housesystem.house.service.HouseService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/houses")
public class HouseController {
    private final HouseService houseService;

    public HouseController(HouseService houseService) {
        this.houseService = houseService;
    }

    @GetMapping
    public ApiResult<PageData<House>> findPage(
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "10") @Min(1) @Max(100) int size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status) {
        return ApiResult.ok(houseService.findPage(page, size, keyword, status));
    }

    @GetMapping("/{id}")
    public ApiResult<House> findById(@PathVariable @Min(1) long id) {
        return ApiResult.ok(houseService.findById(id));
    }

    @PostMapping
    public ApiResult<House> create(@Valid @RequestBody HouseUpsertRequest request) {
        return ApiResult.ok(houseService.create(request));
    }

    @PutMapping("/{id}")
    public ApiResult<House> update(@PathVariable @Min(1) long id,
                                   @Valid @RequestBody HouseUpsertRequest request) {
        return ApiResult.ok(houseService.update(id, request));
    }

    @PatchMapping("/{id}/status")
    public ApiResult<House> changeStatus(@PathVariable @Min(1) long id,
                                         @Valid @RequestBody HouseStatusRequest request) {
        return ApiResult.ok(houseService.changeStatus(id, request.status()));
    }

    @DeleteMapping("/{id}")
    public ApiResult<Void> delete(@PathVariable @Min(1) long id) {
        houseService.delete(id);
        return ApiResult.ok();
    }
}
