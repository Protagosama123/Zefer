package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "admin_products")
data class AdminProductEntity(
    @PrimaryKey val id: String,
    val name: String,
    val description: String,
    val variantsJson: String,
    val createdAt: Long = System.currentTimeMillis()
)
