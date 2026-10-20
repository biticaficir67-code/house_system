package system.common;

public record PageQuery(int page, int size) {
    public PageQuery {
        if (page < 1 || page > 1_000_000 || size < 1 || size > 100)
            throw new BusinessException(400, "page 范围为 1-1000000，size 范围为 1-100");
    }

    public int offset() {
        return Math.multiplyExact(page - 1, size);
    }
}
