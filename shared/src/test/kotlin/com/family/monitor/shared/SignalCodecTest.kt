package com.family.monitor.shared

import com.family.monitor.shared.signaling.IceCandidate
import com.family.monitor.shared.signaling.Role
import com.family.monitor.shared.signaling.SignalCodec
import com.family.monitor.shared.signaling.SignalMessage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SignalCodecTest {

    private fun roundTrip(msg: SignalMessage) {
        val text = SignalCodec.encode(msg)
        assertTrue(text.contains("\"type\""), "wire form must carry a type discriminator: $text")
        assertEquals(msg, SignalCodec.decode(text))
    }

    @Test fun join_round_trips() = roundTrip(SignalMessage.Join("room1", Role.VIEWER))
    @Test fun joined_round_trips() = roundTrip(SignalMessage.Joined(listOf(Role.CAMERA)))
    @Test fun auth_error_round_trips() = roundTrip(SignalMessage.AuthError("bad token"))
    @Test fun peer_event_round_trips() = roundTrip(SignalMessage.PeerEvent(Role.CAMERA, true))
    @Test fun offer_round_trips() = roundTrip(SignalMessage.Offer("v=0\r\no=- ...", "authA"))
    @Test fun answer_round_trips() = roundTrip(SignalMessage.Answer("v=0\r\no=- ...", "authB"))
    @Test fun ice_round_trips() =
        roundTrip(SignalMessage.Ice(IceCandidate("0", 0, "candidate:1 1 udp ...")))
    @Test fun start_request_round_trips() = roundTrip(SignalMessage.StartRequest("nonce1", "authX"))
    @Test fun bye_round_trips() = roundTrip(SignalMessage.Bye("done"))

    @Test fun decodes_camera_role_name() {
        val msg = SignalCodec.decode("""{"type":"join","room":"r","role":"camera"}""")
        assertEquals(SignalMessage.Join("r", Role.CAMERA), msg)
    }

    @Test fun tolerates_unknown_fields() {
        val msg = SignalCodec.decode("""{"type":"bye","reason":"x","extraFutureField":123}""")
        assertEquals(SignalMessage.Bye("x"), msg)
    }
}
