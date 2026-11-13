package com.cujammu.pulse.data.model

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HelpdeskApiContractTest {
    private val gson = Gson()

    @Test
    fun paginatedTicketResponseKeepsStringPublicIdsAndServerAuthorizationFlags() {
        val json = """{
            "items":[{
                "id":"f4747136-2c39-45d2-a922-a5ab3149dcec",
                "version":3,
                "ticketNumber":"CUJ-TKT-202610-0001",
                "subject":"Scanner issue",
                "description":"Entry scan did not register",
                "category":"TECH_SUPPORT",
                "priority":"MEDIUM",
                "requestedPriority":"HIGH",
                "status":"OPEN",
                "assignedOffice":null,
                "assignedStaffId":null,
                "escalated":false,
                "resolutionNotes":null,
                "createdAt":"2026-10-19T10:00:00",
                "resolvedAt":null,
                "canManage":false,
                "canReopen":false
            }],
            "page":0,
            "hasNext":true
        }"""

        val type = object : TypeToken<HelpdeskPageDto<HelpdeskTicketDto>>() {}.type
        val result = gson.fromJson<HelpdeskPageDto<HelpdeskTicketDto>>(json, type)
        assertEquals(0, result.page)
        assertTrue(result.hasNext)
        assertEquals(1, result.items.size)
        val ticket = result.items.single()
        assertEquals("f4747136-2c39-45d2-a922-a5ab3149dcec", ticket.id)
        assertEquals(3L, ticket.version)
        assertEquals(false, ticket.canManage)
        assertEquals("2026-10-19T10:00:00", ticket.createdAt)
    }

    @Test
    fun createRequestUsesBackendCommandFieldNames() {
        val body = CreateHelpdeskTicketRequest(
            requestKey = "f4747136-2c39-45d2-a922-a5ab3149dcec",
            subject = "Scanner issue",
            description = "Entry scan did not register",
            category = "TECH_SUPPORT",
            requestedPriority = "HIGH"
        )

        val json = gson.fromJson(gson.toJson(body), Map::class.java)
        assertEquals(setOf("requestKey", "subject", "description", "category", "requestedPriority"), json.keys)
        assertEquals("HIGH", json["requestedPriority"])
        assertTrue(!json.containsKey("priority"))
    }
}
