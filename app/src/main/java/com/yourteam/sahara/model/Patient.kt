package com.yourteam.sahara.model

import java.util.UUID

data class Patient(
    val syncId: String = java.util.UUID.randomUUID().toString(),
    val id: String = syncId,
    val name: String = "",
    val age: Int = 0,
    val region: String = "",
    val language: String = "English"
)
