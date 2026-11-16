package com.example.roy.data.offline

import android.content.Context
import com.example.roy.data.auth.AndroidSecureRecord
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal fun androidOfflineCache(context: Context): OfflineCache {
    val public = context.getSharedPreferences("roy-map-cache", Context.MODE_PRIVATE)
    val private = AndroidSecureRecord(context, "roy-chat-cache", "roy-chat-cache-key")
    return OfflineCache(
        pins = OfflineRecord(
            read = { withContext(Dispatchers.IO) { public.getString("pins", null) } },
            replace = { value -> withContext(Dispatchers.IO) { check(public.edit().putString("pins", value).commit()) } },
            clear = { withContext(Dispatchers.IO) { check(public.edit().remove("pins").commit()) } },
        ),
        chats = OfflineRecord(private::read, private::replace, private::clear),
    )
}
