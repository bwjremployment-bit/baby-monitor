package com.family.monitor.shared.state

/** The connection lifecycle shared by both apps so status + reconnection behave identically. */
enum class ConnectionState {
    DISCONNECTED,
    SIGNALING_CONNECTING,
    SIGNALING_READY,
    PEER_CONNECTING,
    LIVE,
    PEER_RECONNECTING,
    AUTH_FAILED,   // terminal until re-pair / retry
    FAILED,        // terminal until manual reconnect
}

/** Events that drive [ConnectionStateMachine]. */
enum class ConnectionEvent {
    CONNECT_SIGNALING,     // start connecting to the signalling server
    SIGNALING_OPEN,        // signalling socket ready + joined
    AUTH_REJECTED,         // token invalid/expired
    PEER_FOUND,            // the other peer is present; begin negotiation
    PEER_CONNECTED,        // media flowing
    PEER_DROPPED,          // media/ICE lost but we will try to recover
    NETWORK_LOST,          // transport gone
    FATAL,                 // unrecoverable error
    RETRY,                 // manual or automatic retry request
    DISCONNECT,            // explicit stop
}

/**
 * A small, deterministic state machine. Unknown transitions are rejected
 * (returns null) rather than silently mutating, so both apps can assert on it.
 */
class ConnectionStateMachine(initial: ConnectionState = ConnectionState.DISCONNECTED) {
    var state: ConnectionState = initial
        private set

    private val transitions: Map<Pair<ConnectionState, ConnectionEvent>, ConnectionState> = buildMap {
        put(ConnectionState.DISCONNECTED to ConnectionEvent.CONNECT_SIGNALING, ConnectionState.SIGNALING_CONNECTING)

        put(ConnectionState.SIGNALING_CONNECTING to ConnectionEvent.SIGNALING_OPEN, ConnectionState.SIGNALING_READY)
        put(ConnectionState.SIGNALING_CONNECTING to ConnectionEvent.AUTH_REJECTED, ConnectionState.AUTH_FAILED)
        put(ConnectionState.SIGNALING_CONNECTING to ConnectionEvent.NETWORK_LOST, ConnectionState.FAILED)
        put(ConnectionState.SIGNALING_CONNECTING to ConnectionEvent.FATAL, ConnectionState.FAILED)

        put(ConnectionState.SIGNALING_READY to ConnectionEvent.PEER_FOUND, ConnectionState.PEER_CONNECTING)
        put(ConnectionState.SIGNALING_READY to ConnectionEvent.NETWORK_LOST, ConnectionState.FAILED)
        put(ConnectionState.SIGNALING_READY to ConnectionEvent.FATAL, ConnectionState.FAILED)

        put(ConnectionState.PEER_CONNECTING to ConnectionEvent.PEER_CONNECTED, ConnectionState.LIVE)
        put(ConnectionState.PEER_CONNECTING to ConnectionEvent.PEER_DROPPED, ConnectionState.PEER_RECONNECTING)
        put(ConnectionState.PEER_CONNECTING to ConnectionEvent.NETWORK_LOST, ConnectionState.PEER_RECONNECTING)
        put(ConnectionState.PEER_CONNECTING to ConnectionEvent.FATAL, ConnectionState.FAILED)

        put(ConnectionState.LIVE to ConnectionEvent.PEER_DROPPED, ConnectionState.PEER_RECONNECTING)
        put(ConnectionState.LIVE to ConnectionEvent.NETWORK_LOST, ConnectionState.PEER_RECONNECTING)
        put(ConnectionState.LIVE to ConnectionEvent.FATAL, ConnectionState.FAILED)

        put(ConnectionState.PEER_RECONNECTING to ConnectionEvent.PEER_CONNECTED, ConnectionState.LIVE)
        put(ConnectionState.PEER_RECONNECTING to ConnectionEvent.SIGNALING_OPEN, ConnectionState.SIGNALING_READY)
        put(ConnectionState.PEER_RECONNECTING to ConnectionEvent.CONNECT_SIGNALING, ConnectionState.SIGNALING_CONNECTING)
        put(ConnectionState.PEER_RECONNECTING to ConnectionEvent.FATAL, ConnectionState.FAILED)

        // Recovery / teardown available from terminal-ish states.
        put(ConnectionState.FAILED to ConnectionEvent.RETRY, ConnectionState.SIGNALING_CONNECTING)
        put(ConnectionState.AUTH_FAILED to ConnectionEvent.RETRY, ConnectionState.SIGNALING_CONNECTING)
    }

    /** Applies [event]; returns the new state, or null if the transition is not allowed. */
    fun on(event: ConnectionEvent): ConnectionState? {
        if (event == ConnectionEvent.DISCONNECT) {
            state = ConnectionState.DISCONNECTED
            return state
        }
        val next = transitions[state to event] ?: return null
        state = next
        return next
    }

    fun isTerminal(): Boolean = state == ConnectionState.FAILED || state == ConnectionState.AUTH_FAILED
}
