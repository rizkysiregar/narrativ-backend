package com.rizkysiregar.narrativ.model.response;

/**
 * What the web and Android apps read at start to decide which features to show. Public, nothing secret in it.
 */
public record ClientConfigResponse(Features features) {

    /** {@code chat}: show the messages menu, the message button on profiles and chat notifications. */
    public record Features(boolean chat) {
    }
}
