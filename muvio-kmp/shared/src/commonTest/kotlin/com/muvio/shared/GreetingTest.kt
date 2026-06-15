package com.muvio.shared

import kotlin.test.Test
import kotlin.test.assertTrue

class GreetingTest {
    @Test
    fun greetingMentionsPlatform() {
        assertTrue(Greeting().greet().startsWith("muvio KMP running on"))
    }
}
