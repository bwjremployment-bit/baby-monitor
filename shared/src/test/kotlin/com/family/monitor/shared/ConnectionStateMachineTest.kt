package com.family.monitor.shared

import com.family.monitor.shared.state.ConnectionEvent
import com.family.monitor.shared.state.ConnectionState
import com.family.monitor.shared.state.ConnectionStateMachine
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ConnectionStateMachineTest {

    @Test fun happy_path_to_live() {
        val m = ConnectionStateMachine()
        assertEquals(ConnectionState.SIGNALING_CONNECTING, m.on(ConnectionEvent.CONNECT_SIGNALING))
        assertEquals(ConnectionState.SIGNALING_READY, m.on(ConnectionEvent.SIGNALING_OPEN))
        assertEquals(ConnectionState.PEER_CONNECTING, m.on(ConnectionEvent.PEER_FOUND))
        assertEquals(ConnectionState.LIVE, m.on(ConnectionEvent.PEER_CONNECTED))
        assertFalse(m.isTerminal())
    }

    @Test fun drop_then_recover() {
        val m = ConnectionStateMachine(ConnectionState.LIVE)
        assertEquals(ConnectionState.PEER_RECONNECTING, m.on(ConnectionEvent.PEER_DROPPED))
        assertEquals(ConnectionState.LIVE, m.on(ConnectionEvent.PEER_CONNECTED))
    }

    @Test fun reconnecting_can_restart_signaling() {
        val m = ConnectionStateMachine(ConnectionState.PEER_RECONNECTING)
        assertEquals(ConnectionState.SIGNALING_CONNECTING, m.on(ConnectionEvent.CONNECT_SIGNALING))
    }

    @Test fun auth_rejection_is_terminal_until_retry() {
        val m = ConnectionStateMachine(ConnectionState.SIGNALING_CONNECTING)
        assertEquals(ConnectionState.AUTH_FAILED, m.on(ConnectionEvent.AUTH_REJECTED))
        assertTrue(m.isTerminal())
        assertEquals(ConnectionState.SIGNALING_CONNECTING, m.on(ConnectionEvent.RETRY))
    }

    @Test fun invalid_transition_is_rejected_and_state_unchanged() {
        val m = ConnectionStateMachine(ConnectionState.DISCONNECTED)
        assertNull(m.on(ConnectionEvent.PEER_CONNECTED))
        assertEquals(ConnectionState.DISCONNECTED, m.state)
    }

    @Test fun disconnect_always_returns_to_disconnected() {
        val m = ConnectionStateMachine(ConnectionState.LIVE)
        assertEquals(ConnectionState.DISCONNECTED, m.on(ConnectionEvent.DISCONNECT))
    }

    @Test fun failed_recovers_on_retry() {
        val m = ConnectionStateMachine(ConnectionState.FAILED)
        assertTrue(m.isTerminal())
        assertEquals(ConnectionState.SIGNALING_CONNECTING, m.on(ConnectionEvent.RETRY))
        assertFalse(m.isTerminal())
    }
}
