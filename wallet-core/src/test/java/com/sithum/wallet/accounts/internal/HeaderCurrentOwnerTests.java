package com.sithum.wallet.accounts.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sithum.wallet.accounts.UnauthenticatedException;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

class HeaderCurrentOwnerTests {

    private final HeaderCurrentOwner owner = new HeaderCurrentOwner();

    @AfterEach
    void clear() {
        RequestContextHolder.resetRequestAttributes();
    }

    private void requestWith(String header) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        if (header != null) {
            request.addHeader("X-User-Id", header);
        }
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }

    @Test
    void readsTheOwnerFromTheHeader() {
        UUID id = UUID.randomUUID();
        requestWith(" " + id + " ");

        assertThat(owner.ownerId()).isEqualTo(id);
    }

    @Test
    void missingBlankOrMalformedHeaderIsUnauthenticated() {
        for (String bad : new String[] {null, "", "  ", "not-a-uuid"}) {
            requestWith(bad);
            assertThatThrownBy(owner::ownerId).as("header %s", bad).isInstanceOf(UnauthenticatedException.class);
        }
    }

    @Test
    void noRequestInScopeIsUnauthenticated() {
        assertThatThrownBy(owner::ownerId).isInstanceOf(UnauthenticatedException.class);
    }
}
