package com.cujammu.pulse.data.api

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class ApiEndpointPolicyTest {
    @Test
    fun debugBuildAllowsOnlyTheEmulatorLoopbackHttpEndpoint() {
        assertEquals(
            "http://10.0.2.2:8080/",
            ApiEndpointPolicy.validate("http://10.0.2.2:8080/", debugBuild = true)
        )
        assertThrows(IllegalStateException::class.java) {
            ApiEndpointPolicy.validate("http://192.168.1.10:8080/", debugBuild = true)
        }
        assertThrows(IllegalStateException::class.java) {
            ApiEndpointPolicy.validate("http://10.0.2.2:8081/", debugBuild = true)
        }
    }

    @Test
    fun releaseBuildRequiresHttpsAndRejectsEmbeddedCredentialsOrUrlState() {
        assertEquals(
            "https://api.university.example/",
            ApiEndpointPolicy.validate("https://api.university.example/", debugBuild = false)
        )
        listOf(
            "http://api.university.example/",
            "http://10.0.2.2:8080/",
            "https://user:password@api.university.example/",
            "https://api.university.example/?token=secret",
            "https://api.university.example/#fragment",
            ""
        ).forEach { endpoint ->
            assertThrows("Rejected unsafe release endpoint: $endpoint", IllegalStateException::class.java) {
                ApiEndpointPolicy.validate(endpoint, debugBuild = false)
            }
        }
    }
}
