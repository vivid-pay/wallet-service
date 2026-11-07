package com.sithum.wallet.accounts;

/** The request carries no usable caller identity (mapped to 401). */
public class UnauthenticatedException extends RuntimeException {

    public UnauthenticatedException(String message) {
        super(message);
    }
}
