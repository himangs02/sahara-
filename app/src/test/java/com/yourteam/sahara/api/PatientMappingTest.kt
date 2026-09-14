package com.yourteam.sahara.api

import com.yourteam.sahara.api.model.PatientDto
import com.yourteam.sahara.model.Patient
import org.junit.Assert.assertEquals
import org.junit.Test

class PatientMappingTest {

    @Test fun `domain patient to create dto uses syncId as the client id`() {
        val patient = Patient(syncId = "abc-123", id = "abc-123", name = "Kamala Devi", age = 74, region = "Assam", language = "Assamese")

        val dto = patient.toCreateDto()

        assertEquals("abc-123", dto.id)
        assertEquals("Kamala Devi", dto.name)
        assertEquals(74, dto.age)
        assertEquals("Assamese", dto.preferredLanguage)
        assertEquals("Assam", dto.region)
    }

    @Test fun `backend patient dto maps back to domain with matching id and syncId`() {
        val dto = PatientDto(
            id = "server-id",
            name = "Ramesh Babu",
            age = 65,
            preferredLanguage = "Hindi",
            region = "Kerala",
            createdAt = "2026-09-14T00:00:00Z",
            updatedAt = "2026-09-14T00:00:00Z"
        )

        val patient = dto.toDomain()

        assertEquals("server-id", patient.id)
        assertEquals("server-id", patient.syncId)
        assertEquals("Hindi", patient.language)
        assertEquals("Kerala", patient.region)
    }
}
