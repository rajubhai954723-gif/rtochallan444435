package com.example.data.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "rules")
data class RuleEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val senderPattern: String = "", // empty or "*" for any sender
    val keywordPattern: String = "", // empty or "*" for any keyword
    val destinationType: String, // "WEBHOOK", "EMAIL", "TELEGRAM", "DISCORD", "SLACK"
    val isActive: Boolean = true,
    
    // Config fields
    val webhookUrl: String? = null,
    val emailHost: String? = null,
    val emailPort: String? = null,
    val emailUser: String? = null,
    val emailPass: String? = null,
    val emailTo: String? = null,
    val telegramToken: String? = null,
    val telegramChatId: String? = null,
    val discordWebhookUrl: String? = null,
    val slackWebhookUrl: String? = null
)
