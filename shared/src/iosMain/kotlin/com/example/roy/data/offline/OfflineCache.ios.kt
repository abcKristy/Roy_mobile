package com.example.roy.data.offline

import com.example.roy.data.auth.IosSecureRecord
import platform.Foundation.NSUserDefaults

internal fun iosOfflineCache(): OfflineCache {
    val public = NSUserDefaults.standardUserDefaults
    val private = IosSecureRecord("com.example.roy.offline", "chat-cache")
    return OfflineCache(
        pins = OfflineRecord(
            read = { public.stringForKey("roy-pin-cache") },
            replace = { public.setObject(it, forKey = "roy-pin-cache") },
            clear = { public.removeObjectForKey("roy-pin-cache") },
        ),
        chats = OfflineRecord(private::read, private::replace, private::clear),
    )
}
