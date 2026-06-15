package com.muvio.shared.util

import platform.Foundation.NSDate
import platform.Foundation.NSUUID
import platform.Foundation.timeIntervalSince1970

actual fun currentTimeMillis(): Long =
    (NSDate().timeIntervalSince1970 * 1000.0).toLong()

actual fun randomUUID(): String = NSUUID().UUIDString
