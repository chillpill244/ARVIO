package com.muvio.shared

/** Sanity object proving commonMain compiles and resolves the platform seam. */
class Greeting {
    fun greet(): String = "muvio KMP running on ${platform().name}"
}
