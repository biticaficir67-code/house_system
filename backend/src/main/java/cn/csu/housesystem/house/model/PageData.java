package cn.csu.housesystem.house.model;

import java.util.List;

public record PageData<T>(List<T> records, long total, int page, int size) {
}
