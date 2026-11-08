package com.sithum.wallet.accounts.internal;

import java.util.List;
import org.springframework.data.domain.Page;

/** One page of a list response. {@code page} is zero-based. */
record PageDto<T>(List<T> content, int page, int size, long totalElements, int totalPages) {

    static <T> PageDto<T> of(Page<T> page) {
        return new PageDto<>(page.getContent(), page.getNumber(), page.getSize(), page.getTotalElements(),
                page.getTotalPages());
    }
}
