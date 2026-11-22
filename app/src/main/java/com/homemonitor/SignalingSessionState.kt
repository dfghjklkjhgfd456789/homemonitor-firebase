package com.google.android.system

/**
 * SignalingSessionState
 *
 * Defines the canonical state machine transitions for WebRTC signaling and P2P connection lifecycle.
 */
enum class SignalingSessionState {
    CREATED,
    REQUESTED,
    ACCEPTED,
    OFFER_CREATED,
    OFFER_PUBLISHED,
    ANSWER_CREATED,
    ANSWER_PUBLISHED,
    ICE_CHECKING,
    PEER_CONNECTED,
    DATA_CHANNEL_OPEN,
    READY,
    CLOSING,
    CLOSED,

    // Failure / Diagnostic States
    AUTH_FAILED,
    DEVICE_UNAPPROVED,
    SESSION_EXPIRED,
    OFFER_FAILED,
    ANSWER_FAILED,
    ICE_FAILED,
    PEER_CONNECTION_FAILED,
    DATA_CHANNEL_FAILED,
    PROVIDER_UNAVAILABLE
}
