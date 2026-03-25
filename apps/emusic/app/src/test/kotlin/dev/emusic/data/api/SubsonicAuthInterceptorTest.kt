package dev.emusic.data.api

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class SubsonicAuthInterceptorTest {

    @Test
    fun `md5 produces correct hash for known input`() {
        // MD5("sesame") = "c8dae1c50e092f3d877192fc555b1dcf"
        val hash = SubsonicAuthInterceptor.md5("sesame")
        assertEquals("c8dae1c50e092f3d877192fc555b1dcf", hash)
    }

    @Test
    fun `md5 token matches expected value for password plus salt`() {
        val password = "mypassword"
        val salt = "abcdef123456"
        val expected = SubsonicAuthInterceptor.md5("${password}${salt}")
        val actual = SubsonicAuthInterceptor.md5("mypasswordabcdef123456")
        assertEquals(expected, actual)
    }

    @Test
    fun `generateSalt produces hex string of expected length`() {
        val salt = SubsonicAuthInterceptor.generateSalt(12)
        // 12 bytes → 24 hex characters
        assertEquals(24, salt.length)
        // Verify it's valid hex
        assert(salt.all { it in '0'..'9' || it in 'a'..'f' })
    }

    @Test
    fun `generateSalt produces unique values`() {
        val salt1 = SubsonicAuthInterceptor.generateSalt()
        val salt2 = SubsonicAuthInterceptor.generateSalt()
        assertNotEquals(salt1, salt2)
    }

    @Test
    fun `md5 of empty string matches known hash`() {
        // MD5("") = "d41d8cd98f00b204e9800998ecf8427e"
        assertEquals("d41d8cd98f00b204e9800998ecf8427e", SubsonicAuthInterceptor.md5(""))
    }
}
