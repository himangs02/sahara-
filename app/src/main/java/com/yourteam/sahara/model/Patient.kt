package com.yourteam.sahara.model

import java.util.UUID

data class Patient(
    val syncId: String = "patient_001",
    val id: String = "patient_001",
    val name: String = "Kamala Devi",
    val age: Int = 74,
    val region: String = "Assam",
    val language: String = "Assamese"
)
