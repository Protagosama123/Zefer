package com.example.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface AdminProductDao {
    @Query("SELECT * FROM admin_products ORDER BY createdAt DESC")
    fun getAllProducts(): Flow<List<AdminProductEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProduct(product: AdminProductEntity)

    @Delete
    suspend fun deleteProduct(product: AdminProductEntity)
}
