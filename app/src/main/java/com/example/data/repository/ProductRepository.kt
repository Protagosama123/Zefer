package com.example.data.repository

import android.content.Context
import com.example.data.local.AdminProductDao
import com.example.data.local.AdminProductEntity
import com.example.data.model.Manifest
import com.example.data.model.Product
import com.example.data.model.Variant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

interface ProductRepository {
    fun getProducts(): Flow<List<Product>>
    fun getProductById(id: String): Flow<Product?>
    suspend fun saveAdminProduct(name: String, description: String, variants: List<Variant>): String
    suspend fun deleteAdminProduct(id: String)
}

class ProductRepositoryImpl(
    private val context: Context,
    private val adminDao: AdminProductDao
) : ProductRepository {

    private fun loadBundledProducts(): List<Product> {
        return try {
            val jsonString = context.assets.open("products.json").bufferedReader().use { it.readText() }
            val jsonArray = JSONArray(jsonString)
            val list = mutableListOf<Product>()
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                list.add(parseProduct(obj))
            }
            list
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun parseProduct(obj: JSONObject): Product {
        val id = obj.getString("id")
        val name = obj.getString("name")
        val desc = obj.optString("description", "")
        val variantsJson = obj.getJSONArray("variants")
        val variants = mutableListOf<Variant>()

        for (j in 0 until variantsJson.length()) {
            val vObj = variantsJson.getJSONObject(j)
            val vId = vObj.getString("id")
            val vName = vObj.getString("name")
            val thumb = vObj.optString("thumbImage", "")
            val hero = vObj.optString("heroImage", thumb)

            val spritesObj = vObj.optJSONObject("sprites")
            val spritesMap = mutableMapOf<String, String>()
            if (spritesObj != null) {
                val keys = spritesObj.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    spritesMap[key] = spritesObj.getString(key)
                }
            }

            val manifestObj = vObj.optJSONObject("manifest")
            val manifest = if (manifestObj != null) {
                Manifest(
                    aspectRatio = manifestObj.optDouble("aspectRatio", 2.0).toFloat(),
                    anchor = manifestObj.optString("anchor", "base-center"),
                    tipDirection = manifestObj.optString("tipDirection", "up")
                )
            } else {
                Manifest()
            }

            variants.add(
                Variant(
                    id = vId,
                    name = vName,
                    thumbImage = thumb,
                    heroImage = hero,
                    sprites = spritesMap,
                    manifest = manifest
                )
            )
        }

        return Product(id = id, name = name, description = desc, variants = variants)
    }

    override fun getProducts(): Flow<List<Product>> {
        return adminDao.getAllProducts().combine(flow { emit(loadBundledProducts()) }) { adminList, bundledList ->
            val adminProducts = adminList.mapNotNull { entity ->
                try {
                    val obj = JSONObject().apply {
                        put("id", entity.id)
                        put("name", entity.name)
                        put("description", entity.description)
                        put("variants", JSONArray(entity.variantsJson))
                    }
                    parseProduct(obj)
                } catch (e: Exception) {
                    null
                }
            }
            adminProducts + bundledList
        }.flowOn(Dispatchers.IO)
    }

    override fun getProductById(id: String): Flow<Product?> {
        return getProducts().combine(flow { emit(id) }) { products, targetId ->
            products.firstOrNull { it.id == targetId }
        }
    }

    override suspend fun saveAdminProduct(
        name: String,
        description: String,
        variants: List<Variant>
    ): String = withContext(Dispatchers.IO) {
        val id = "admin_${System.currentTimeMillis()}"
        val variantsJson = JSONArray().apply {
            variants.forEach { v ->
                val vObj = JSONObject().apply {
                    put("id", v.id)
                    put("name", v.name)
                    put("thumbImage", v.thumbImage)
                    put("heroImage", v.heroImage)
                    put("sprites", JSONObject(v.sprites))
                    put("manifest", JSONObject().apply {
                        put("aspectRatio", v.manifest.aspectRatio.toDouble())
                        put("anchor", v.manifest.anchor)
                        put("tipDirection", v.manifest.tipDirection)
                    })
                }
                put(vObj)
            }
        }.toString()

        val entity = AdminProductEntity(
            id = id,
            name = name,
            description = description,
            variantsJson = variantsJson
        )
        adminDao.insertProduct(entity)
        id
    }

    override suspend fun deleteAdminProduct(id: String) = withContext(Dispatchers.IO) {
        val entity = AdminProductEntity(id = id, name = "", description = "", variantsJson = "")
        adminDao.deleteProduct(entity)
    }
}
