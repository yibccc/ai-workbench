package com.aiworkbench.common;

import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;

/** Limits exactly one mapper select, then releases its thread-local scope before DTO mapping. */
public final class PageQueries {
    private PageQueries() {}

    public static <R, T> PageResponse<T> select(int page, int size, Supplier<List<R>> query,
                                               Function<R, T> map) {
        PageResponse.offset(page, size);
        PageInfo<R> result;
        try {
            PageHelper.startPage(page + 1, size, true).setReasonable(false);
            result = new PageInfo<>(query.get());
        } finally {
            PageHelper.clearPage();
        }
        return PageResponse.of(result.getList().stream().map(map).toList(), page, size, result.getTotal());
    }
}
