package com.example.di

import android.content.Context
import com.example.cv.pipeline.NailSpriteExtractor
import com.example.data.local.AppDatabase
import com.example.data.local.UserPreferences
import com.example.data.repository.ProductRepository
import com.example.data.repository.ProductRepositoryImpl

interface AppContainer {
    val database: AppDatabase
    val userPreferences: UserPreferences
    val productRepository: ProductRepository
    val spriteExtractor: NailSpriteExtractor
}

class DefaultAppContainer(private val context: Context) : AppContainer {
    override val database: AppDatabase by lazy {
        AppDatabase.getDatabase(context)
    }

    override val userPreferences: UserPreferences by lazy {
        UserPreferences(context)
    }

    override val productRepository: ProductRepository by lazy {
        ProductRepositoryImpl(context, database.adminProductDao())
    }

    override val spriteExtractor: NailSpriteExtractor by lazy {
        NailSpriteExtractor()
    }
}
