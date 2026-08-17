package online.db1k.safering.android.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PhoneNumberUtilsTest {
    @Test
    fun normalize_tenDigit() {
        assertEquals("+17275551212", PhoneNumberUtils.normalizeToE164("(727) 555-1212"))
    }

    @Test
    fun normalize_elevenDigit() {
        assertEquals("+17275551212", PhoneNumberUtils.normalizeToE164("1-727-555-1212"))
    }

    @Test
    fun extract_fromSmsBody() {
        val body = "USPS: Your package is held. Call 727-555-9999 or visit http://bit.ly/x now"
        val phones = PhoneNumberUtils.extractPhones(body)
        assertTrue(phones.contains("+17275559999"))
    }

    @Test
    fun pretty_us() {
        assertEquals("(727) 555-1212", PhoneNumberUtils.pretty("+17275551212"))
    }
}
