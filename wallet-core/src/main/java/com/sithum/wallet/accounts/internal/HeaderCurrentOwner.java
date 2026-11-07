package com.sithum.wallet.accounts.internal;

import com.sithum.wallet.accounts.CurrentOwner;
import com.sithum.wallet.accounts.UnauthenticatedException;
import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/** Temporary identity: the caller's id comes from the {@code X-User-Id} header (ADR-0006). */
@Component
class HeaderCurrentOwner implements CurrentOwner {

    static final String HEADER = "X-User-Id";

    @Override
    public UUID ownerId() {
        if (!(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes)) {
            throw new UnauthenticatedException("No request in scope");
        }
        HttpServletRequest request = attributes.getRequest();
        String value = request.getHeader(HEADER);
        if (value == null || value.isBlank()) {
            throw new UnauthenticatedException("Missing " + HEADER + " header");
        }
        try {
            return UUID.fromString(value.trim());
        } catch (IllegalArgumentException e) {
            throw new UnauthenticatedException("Invalid " + HEADER + " header");
        }
    }
}
