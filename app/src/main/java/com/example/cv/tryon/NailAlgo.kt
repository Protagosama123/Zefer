package com.example.cv.tryon

import kotlin.math.acos
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.math.sqrt

data class Point3D(
    val x: Float,
    val y: Float,
    val z: Float = 0f
) {
    operator fun plus(other: Point3D) = Point3D(x + other.x, y + other.y, z + other.z)
    operator fun minus(other: Point3D) = Point3D(x - other.x, y - other.y, z - other.z)
    operator fun times(s: Float) = Point3D(x * s, y * s, z * s)
    fun norm2D(): Float = hypot(x.toDouble(), y.toDouble()).toFloat()
    fun norm3D(): Float = sqrt((x * x + y * y + z * z).toDouble()).toFloat()
}

val HAND_CONNECTIONS = listOf(
    Pair(0, 1), Pair(1, 2), Pair(2, 3), Pair(3, 4),
    Pair(0, 5), Pair(5, 6), Pair(6, 7), Pair(7, 8),
    Pair(5, 9), Pair(9, 10), Pair(10, 11), Pair(11, 12),
    Pair(9, 13), Pair(13, 14), Pair(14, 15), Pair(15, 16),
    Pair(13, 17), Pair(17, 18), Pair(18, 19), Pair(19, 20),
    Pair(0, 17)
)

val FINGERS: Map<String, IntArray> = mapOf(
    "thumb" to intArrayOf(1, 2, 3, 4),
    "index" to intArrayOf(5, 6, 7, 8),
    "middle" to intArrayOf(9, 10, 11, 12),
    "ring" to intArrayOf(13, 14, 15, 16),
    "pinky" to intArrayOf(17, 18, 19, 20)
)

val NAIL_WIDTH_REL: Map<String, Float> = mapOf(
    "thumb" to 0.195f,
    "index" to 0.172f,
    "middle" to 0.172f,
    "ring" to 0.161f,
    "pinky" to 0.138f
)

val TYPICAL_DIPTIP_REL: Map<String, Float> = mapOf(
    "thumb" to 0.30f,
    "index" to 0.24f,
    "middle" to 0.26f,
    "ring" to 0.25f,
    "pinky" to 0.20f
)

data class Params(
    val nailBackRel: Float = 0.14f,
    val nailLenRel: Float = 0.30f,
    val labelMirrored: Boolean = false,
    val posMinCut: Float = 1.5f,
    val posBeta: Float = 0.02f,
    val angMinCut: Float = 1.5f,
    val angBeta: Float = 1.0f,
    val sizeMinCut: Float = 0.8f,
    val sizeBeta: Float = 0.0f,
    val flexHideStartDeg: Float = 60.0f,
    val flexHideEndDeg: Float = 100.0f,
    val backShowStart: Float = 0.10f,
    val backShowEnd: Float = 0.35f,
    val fitScale: Float = 1.0f,
    val geoBlend: Float = 0.3f,
    val minWidthForeshorten: Float = 0.35f,
    val minLenForeshorten: Float = 0.45f,
    val holdMs: Float = 150.0f,
    val reacquireGapMs: Float = 300.0f
)

data class ManualAdjustments(
    val size: Float = 1.0f,
    val length: Float = 1.0f,
    val offset: Float = 0.0f,
    val rotDeg: Float = 0.0f,
    val shadow: Float = 0.28f,
    val lightMatch: Float = 0.5f,
    val facingOverride: String = "auto" // "auto", "back", "palm"
)

data class RawFingerPose(
    val rawX: Float,
    val rawY: Float,
    val rawAng: Float,
    val rawW: Float,
    val rawH: Float,
    val flexDeg: Float,
    val proj: Float,
    val targetAlpha: Float,
    val widthRel: Float
)

fun smoothstep(e0: Float, e1: Float, x: Float): Float {
    if (e1 == e0) return if (x >= e1) 1.0f else 0.0f
    val t = ((x - e0) / (e1 - e0)).coerceIn(0.0f, 1.0f)
    return t * t * (3.0f - 2.0f * t)
}

fun wrapPi(a: Double): Double {
    return atan2(sin(a), cos(a))
}

fun handGeometry(px: List<Point3D>): Pair<Float, Float> {
    val palm9 = (px[9] - px[0]).norm2D()
    val palm5 = 0.93f * (px[5] - px[0]).norm2D()
    val palm13 = 0.95f * (px[13] - px[0]).norm2D()
    val palm = maxOf(palm9, maxOf(palm5, palm13))

    val width = (px[5] - px[17]).norm2D()
    val unit = maxOf(palm, maxOf(width / 0.80f, 1e-3f))
    val yawCos = minOf(1.0f, width / (0.80f * maxOf(palm, 1e-3f)))
    return Pair(unit, yawCos)
}

fun backConfidenceFromImage(
    px: List<Point3D>,
    world: List<Point3D>?,
    label: String,
    labelMirrored: Boolean = false
): Pair<Float, Boolean> {
    val (_, mag) = handGeometry(px)
    val v1 = px[5] - px[0]
    val v2 = px[17] - px[0]
    val cross = v1.x * v2.y - v1.y * v2.x
    val realRight = (label == "Right") != labelMirrored
    val sign = (if (cross > 0f) 1.0f else -1.0f) * (if (realRight) 1.0f else -1.0f)
    return Pair(sign * mag, realRight)
}

fun rawFingerPoses(
    px: List<Point3D>,
    world: List<Point3D>?,
    backConf: Float,
    unit: Float,
    p: Params,
    adj: ManualAdjustments? = null,
    lastTheta: MutableMap<String, Float>? = null
): Map<String, RawFingerPose> {
    val size = adj?.size ?: 1.0f
    val length = adj?.length ?: 1.0f
    val offset = adj?.offset ?: 0.0f
    val rot = Math.toRadians((adj?.rotDeg ?: 0.0f).toDouble()).toFloat()

    val visBack = smoothstep(p.backShowStart, p.backShowEnd, backConf)
    val widthFs = maxOf(p.minWidthForeshorten, minOf(Math.abs(backConf), 1.0f))
    val out = mutableMapOf<String, RawFingerPose>()

    for ((name, indices) in FINGERS) {
        val mcp = indices[0]
        val pip = indices[1]
        val dip = indices[2]
        val tip = indices[3]

        val d3 = px[tip] - px[dip]
        val l2 = hypot(d3.x.toDouble(), d3.y.toDouble()).toFloat()
        val l3 = d3.norm3D()
        val proj = if (l3 > 1e-6f) l2 / l3 else 1.0f

        val theta = if (proj < 0.25f || l2 < 1e-3f) {
            lastTheta?.get(name) ?: 0.0f
        } else {
            (atan2(d3.y.toDouble(), d3.x.toDouble()) + Math.PI / 2.0).toFloat()
        }
        lastTheta?.put(name, theta)

        val thetaAdj = theta + rot
        val ux = sin(thetaAdj.toDouble()).toFloat()
        val uy = -cos(thetaAdj.toDouble()).toFloat()

        val constVal = (NAIL_WIDTH_REL[name] ?: 0.17f) * p.fitScale
        val widthRelBase = if (proj > 0.7f) {
            val typical = TYPICAL_DIPTIP_REL[name] ?: 0.25f
            val geo = constVal * (l2 / unit) / typical
            ((1.0f - p.geoBlend) * constVal + p.geoBlend * geo).coerceIn(0.8f * constVal, 1.25f * constVal)
        } else {
            constVal
        }
        val widthRel = widthRelBase * size

        val backPx = p.nailBackRel * unit
        val baseX = px[tip].x - ux * backPx + ux * offset * widthRel * unit
        val baseY = px[tip].y - uy * backPx + uy * offset * widthRel * unit
        val rawW = widthRel * unit * widthFs
        val rawH = p.nailLenRel * unit * maxOf(p.minLenForeshorten, proj) * length * size

        val v1 = px[pip] - px[mcp]
        val v2 = px[tip] - px[dip]
        val den = v1.norm3D() * v2.norm3D()
        val flex = if (den > 1e-9f) {
            val dot = (v1.x * v2.x + v1.y * v2.y + v1.z * v2.z) / den
            Math.toDegrees(acos(dot.coerceIn(-1.0f, 1.0f).toDouble())).toFloat()
        } else 0.0f

        val visFlex = 1.0f - smoothstep(p.flexHideStartDeg, p.flexHideEndDeg, flex)
        val vb = if (name == "thumb") smoothstep(-0.30f, 0.0f, backConf) else visBack

        out[name] = RawFingerPose(
            rawX = baseX,
            rawY = baseY,
            rawAng = thetaAdj,
            rawW = rawW,
            rawH = rawH,
            flexDeg = flex,
            proj = proj,
            targetAlpha = visFlex * vb,
            widthRel = widthRel
        )
    }

    return out
}
