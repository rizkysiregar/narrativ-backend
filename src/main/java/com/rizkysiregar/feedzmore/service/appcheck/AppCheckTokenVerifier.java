package com.rizkysiregar.feedzmore.service.appcheck;

/**
 * Checks an App Check token that a client sent in a request header.
 */
@FunctionalInterface
public interface AppCheckTokenVerifier {

    /**
     * @return the Firebase app id the token was issued to
     * @throws InvalidTokenException the token is malformed, forged, expired, or issued for another project
     * @throws UnavailableException  the token could not be checked at all (Google's keys are unreachable)
     */
    String verify(String token) throws InvalidTokenException, UnavailableException;

    class InvalidTokenException extends Exception {
        public InvalidTokenException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    class UnavailableException extends Exception {
        public UnavailableException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
