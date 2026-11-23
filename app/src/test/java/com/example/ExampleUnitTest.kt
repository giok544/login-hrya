package com.example

import com.example.data.LocalAuthCrypto
import com.example.data.model.LoginSession
import com.example.data.model.SessionStatus
import com.example.data.model.UserProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleUnitTest {
    @Test
    fun sessionStatus_fromRaw_handlesKnownAndUnknownValues() {
        assertEquals(SessionStatus.ACTIVE, SessionStatus.fromRaw("active"))
        assertEquals(SessionStatus.VERIFIED, SessionStatus.fromRaw("verified"))
        assertEquals(SessionStatus.SIGNED_OUT, SessionStatus.fromRaw("signed_out"))
        assertEquals(SessionStatus.ACTIVE, SessionStatus.fromRaw("unknown_legacy"))
        assertEquals(SessionStatus.ACTIVE, SessionStatus.fromRaw(null))
    }

    @Test
    fun firestoreModels_haveSafeNoArgDefaults() {
        val profile = UserProfile()
        assertEquals("", profile.id)
        assertEquals("", profile.userId)
        assertTrue(profile.sessionAlertsEnabled)

        val session = LoginSession()
        assertEquals("", session.id)
        assertEquals("google", session.authProvider)
        assertEquals(SessionStatus.ACTIVE, session.parsedStatus)
    }

    @Test
    fun localAuthCrypto_hashesAndVerifiesPasswordsSafely() {
        val salt = LocalAuthCrypto.generateSalt()
        val hash = LocalAuthCrypto.hashPassword("rahasia123", salt)

        assertTrue(LocalAuthCrypto.verifyPassword("rahasia123", salt, hash))
        assertFalse(LocalAuthCrypto.verifyPassword("salah123", salt, hash))
    }

    @Test
    fun localAuthCrypto_validatesUsernameFormat() {
        assertTrue(LocalAuthCrypto.isValidUsername("budi.santoso_99"))
        assertFalse(LocalAuthCrypto.isValidUsername("ab"))
        assertFalse(LocalAuthCrypto.isValidUsername("budi santoso"))
        assertFalse(LocalAuthCrypto.isValidUsername("user@domain"))
    }
}
