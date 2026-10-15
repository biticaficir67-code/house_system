package cn.csu.housesystem.house.model;

import jakarta.validation.constraints.NotBlank;

public record HouseStatusRequest(@NotBlank String status) {
}
