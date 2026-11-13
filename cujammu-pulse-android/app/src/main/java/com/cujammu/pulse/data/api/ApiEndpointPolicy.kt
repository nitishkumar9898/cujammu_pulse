package com.cujammu.pulse.data.api

import java.net.URI

/** Restricts local HTTP to the Android emulator host and requires HTTPS in release builds. */
internal object ApiEndpointPolicy {
    fun validate(raw: String, debugBuild: Boolean): String {
        val uri = runCatching { URI(raw) }
            .getOrElse { throw IllegalStateException("The API endpoint is not configured for this build") }
        val isLocalEmulatorDebug = debugBuild && uri.scheme == "http"
            && uri.host == "10.0.2.2" && uri.port == 8080
            && uri.userInfo == null && uri.query == null && uri.fragment == null
            && uri.rawPath == "/"
        val isSecureRelease = !debugBuild && uri.scheme == "https"
            && !uri.host.isNullOrBlank() && uri.userInfo == null
            && uri.query == null && uri.fragment == null && raw.endsWith("/")
        check(isLocalEmulatorDebug || isSecureRelease) {
            "The API endpoint is not configured for this build"
        }
        return raw
    }
}
