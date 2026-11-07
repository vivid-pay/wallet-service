package com.sithum.wallet.accounts;

import java.util.UUID;

/**
 * Who the caller is. The single seam for identity: today an implementation reads the temporary
 * {@code X-User-Id} header (ADR-0006); when Keycloak/JWT arrives it is replaced by one that reads the
 * token, and nothing else changes.
 */
public interface CurrentOwner {

    /**
     * @return the id of the authenticated caller
     * @throws UnauthenticatedException if the request carries no usable identity
     */
    UUID ownerId();
}
