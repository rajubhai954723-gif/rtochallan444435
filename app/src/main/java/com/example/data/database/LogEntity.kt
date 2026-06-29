package com.example.data.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "logs")
data class LogEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val sender: String,
    val messageBody: String,
    val matchedRuleId: Int? = null,
    val matchedRuleName: String? = null,
    val destinationType: String? = null,
    val destinationTarget: String? = null,
    val status: String, // "SUCCESS", "FAILED", "FILTERED", "PENDING"
    val statusMessage: String? = null
)
