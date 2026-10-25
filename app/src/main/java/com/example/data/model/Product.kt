package com.example.data.model

data class Manifest(
    val aspectRatio: Float = 2.0f,
    val anchor: String = "base-center",
    val tipDirection: String = "up"
)

data class Variant(
    val id: String,
    val name: String,
    val thumbImage: String,
    val heroImage: String = thumbImage,
    val sprites: Map<String, String>, // "thumb", "index", "middle", "ring", "pinky"
    val manifest: Manifest = Manifest()
)

data class Product(
    val id: String,
    val name: String,
    val description: String,
    val variants: List<Variant>
)
