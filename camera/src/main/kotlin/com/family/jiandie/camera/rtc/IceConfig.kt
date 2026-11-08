package com.family.jiandie.camera.rtc

import com.family.jiandie.camera.data.SecurePrefs
import org.webrtc.PeerConnection

object IceConfig {
    private val PUBLIC_STUN = listOf(
        "stun:stun.l.google.com:19302",
        "stun:stun1.l.google.com:19302",
    )

    /** Free public STUN always; plus the user's TURN relay if they configured one. */
    fun servers(prefs: SecurePrefs): List<PeerConnection.IceServer> {
        val list = PUBLIC_STUN.map { PeerConnection.IceServer.builder(it).createIceServer() }.toMutableList()
        val turn = prefs.turnUrl
        if (turn.startsWith("turn:") || turn.startsWith("turns:")) {
            list += PeerConnection.IceServer.builder(turn)
                .setUsername(prefs.turnUser)
                .setPassword(prefs.turnPass)
                .createIceServer()
        }
        return list
    }
}
