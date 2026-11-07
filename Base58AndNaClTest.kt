package com.alphatrade.ai.phantom

import com.iwebpp.crypto.TweetNaclFast
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class Base58AndNaClTest {
    @Test fun base58MatchesKnownVector() {
        assertEquals("JxF12TrwUP45BMd", Base58.encode("Hello World".toByteArray(Charsets.UTF_8)))
        assertEquals("Hello World", String(Base58.decode("JxF12TrwUP45BMd"), Charsets.UTF_8))
    }

    @Test fun base58RoundTripPreservesLeadingZeroBytes() {
        val original = byteArrayOf(0, 0, 1, 2, 3, 0, -1)
        assertArrayEquals(original, Base58.decode(Base58.encode(original)))
    }

    @Test fun base58RoundTripForAllZeroBytes() {
        val original = byteArrayOf(0, 0, 0)
        assertEquals("111", Base58.encode(original))
        assertArrayEquals(original, Base58.decode("111"))
    }

    @Test fun ed25519SignatureCanBeVerifiedByPublicKey() {
        val keyPair = TweetNaclFast.Signature.keyPair()
        val message = "ALPHA TRADE AI ownership proof".toByteArray(Charsets.UTF_8)
        val signature = TweetNaclFast.Signature(keyPair.publicKey, keyPair.secretKey).detached(message)
        val valid = TweetNaclFast.Signature(keyPair.publicKey, ByteArray(64)).detached_verify(message, signature)
        org.junit.Assert.assertTrue(valid)
    }

    @Test fun naclBoxRoundTripBetweenTwoEphemeralPeers() {
        val alice = TweetNaclFast.Box.keyPair()
        val bob = TweetNaclFast.Box.keyPair()
        val nonce = TweetNaclFast.makeBoxNonce()
        val message = "ALPHA TRADE AI phantom handshake test".toByteArray(Charsets.UTF_8)
        val cipher = TweetNaclFast.Box(bob.publicKey, alice.secretKey).box(message, nonce)
        val plain = TweetNaclFast.Box(alice.publicKey, bob.secretKey).open(cipher, nonce)
        assertArrayEquals(message, plain)
    }
}
