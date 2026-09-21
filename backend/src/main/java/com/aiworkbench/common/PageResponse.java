package com.aiworkbench.common;

import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

public record PageResponse<T>(List<T> items, int page, int size, long totalElements, int totalPages) {
    public static <T> PageResponse<T> of(List<T> items, int page, int size, long totalElements) {
        return new PageResponse<>(items, page, size, totalElements,
                totalElements == 0 ? 0 : (int) ((totalElements + size - 1) / size));
    }

    public static int offset(int page, int size) {
        if (page < 0) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "页码不能小于 0");
        if (size != 10 && size != 20 && size != 50) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "每页数量只支持 10、20 或 50");
        }
        long offset = (long) page * size;
        if (offset > Integer.MAX_VALUE) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "页码过大");
        }
        return (int) offset;
    }
}
