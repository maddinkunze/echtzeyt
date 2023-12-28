package com.maddin.echtzeyt.randomcode

import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import java.lang.IndexOutOfBoundsException

/*class Matrix4x4(private val values: FloatArray) {
    companion object {
        val identity = Matrix4x4(floatArrayOf(
            1f, 0f, 0f, 0f,
            0f, 1f, 0f, 0f,
            0f, 0f, 1f, 0f,
            0f, 0f, 0f, 1f))
    }
    fun clone() : Matrix4x4 {
        return Matrix4x4(values.clone())
    }
    fun inverse() : Matrix4x4 {
        // Gauß-Jordan Algorithm, may be inefficient but meh
        val input = this.clone()
        val result = identity.clone()

        for (i in 0..3) {
            eliminationStep(input, result, i)
        }

        return result
    }

    operator fun times(vector: FloatArray) : FloatArray {
        return FloatArray(4) { row -> vector.mapIndexed { col, value -> this[row, col] * value }.sum() }
    }

    private fun subtractRow(indexMinuend: Int, indexSubtrahend: Int, factor: Float) {
        for (i in 0..3) {
            this[indexMinuend, i] -= factor * this[indexSubtrahend, i]
        }
    }

    private fun multiplyRow(index: Int, factor: Float) {
        for (i in 0..3) {
            this[index, i] *= factor
        }
    }

    private fun eliminationStep(input: Matrix4x4, result: Matrix4x4, index: Int) {
        var factor = 1 / input[index, index]
        input.multiplyRow(index, factor)
        result.multiplyRow(index, factor)

        for (i in 0..3) {
            if (i == index) { continue }
            factor = input[i, index]
            input.subtractRow(i, index, factor)
            result.subtractRow(i, index, factor)
        }
    }

    operator fun get(row: Int, column: Int) : Float {
        return this.values[coordsToIndex(row, column)]
    }

    operator fun set(row: Int, column: Int, value: Float) {
        this.values[coordsToIndex(row, column)] = value
    }

    private fun coordsToIndex(row: Int, column: Int) : Int {
        return 4 * row + column
    }

    override fun toString() : String {
        return "[ ${this[0, 0]}, ${this[0, 1]}, ${this[0, 2]} ${this[0, 3]},\n" +
                "  ${this[1, 0]}, ${this[1, 1]}, ${this[1, 2]}, ${this[1, 3]},\n" +
                "  ${this[2, 0]}, ${this[2, 1]}, ${this[2, 2]}, ${this[2, 3]},\n" +
                "  ${this[3, 0]}, ${this[3, 1]}, ${this[3, 2]}, ${this[3, 3]} ]"
    }
}*/

data class ColorRGB(val r: Float, val g: Float, val b: Float) {
    constructor(r: Int, g: Int, b: Int) : this(r.toFloat(), g.toFloat(), b.toFloat())

    operator fun get(index: Int) : Float {
        return when (index) {
            0 -> r
            1 -> g
            2 -> b
            else -> throw IndexOutOfBoundsException()
        }
    }
}

/*fun getColorMappingComponent(inputs: Array<ColorRGB>, output: FloatArray) : FloatArray {
    val matrix = Array(4) { i -> floatArrayOf(inputs[i].r, inputs[i].b, inputs[i].b, 1f).toTypedArray() }.flatten()
    val inverse = Matrix4x4(matrix.toFloatArray()).inverse()
    return inverse * output
}*/

class MatrixRowMapper(rows: Int) {
    private val map = mutableMapOf<Int, Int>()

    init {
        for (i in 0 until rows) {
            map[i] = i
        }
    }

    fun swap(old: Int, new: Int) {
        val tmp = map[old]!!
        map[old] = map[new]!!
        map[new] = tmp
    }

    fun get(row: Int) : Int {
        return map[row]!!
    }
}

fun solveLinearSystem(equations: Array<FloatArray>, output: FloatArray) : FloatArray {
    val inputs = Array(equations.size) { i -> equations[i].clone() }
    val result = output.clone()
    val rows = result.size
    val map = MatrixRowMapper(rows)

    for (row in 0 until rows) {
        // find row
        var swapRow = row
        while (inputs[swapRow][swapRow] == 0f) {
            swapRow++
            if (swapRow >= rows) {
                throw IndexOutOfBoundsException("Your linear equation could not be solved, please try tweaking your colors slightly")
            }
        }

        if (swapRow != row) {
            map.swap(row, swapRow)
        }

        val realRow = map.get(row)
        val cols = inputs[realRow].size

        var factor = 1f / inputs[realRow][realRow]
        for (col in 0 until cols) {
            inputs[realRow][col] *= factor
        }
        result[realRow] *= factor

        for (row2 in 0 until rows) {
            if (row == row2) { continue }
            val realRow2 = map.get(row2)
            factor = inputs[realRow2][realRow]
            for (col in 0 until cols) {
                inputs[realRow2][col] -= factor * inputs[realRow][col]
            }
            result[realRow2] -= factor * result[realRow]
        }
    }

    return result
}

/*fun getColorMappingMatrixFilter(mappedColors: Array<Pair<ColorRGB, ColorRGB>>): ColorMatrixColorFilter {
    val inputs = Array(4) { mappedColors[it].first }
    val mappingMatrix = Array(4) { component ->
        val componentArray = FloatArray(4) {
            val color = mappedColors[it].second
            when (component) {
                0 -> color.r
                1 -> color.b
                2 -> color.g
                else -> 1f
            }
        }
        getColorMappingComponent(inputs, componentArray)
    }

    val colorMatrix = Array(4) { component ->
        if (component in 0..2) {
            val mapping = mappingMatrix[component]
            arrayOf(mapping[0], mapping[1], mapping[2], 0f, mapping[3] * 255)
        } else {
            arrayOf(0f, 0f, 0f, 255f, 0f)
        }

    }

    println(colorMatrix.map { it.map { "$it," }.reduce { a, b -> "$a $b" } }.reduce { a, b -> "$a\n$b" })

    return ColorMatrixColorFilter(colorMatrix.flatten().toFloatArray())
}*/

fun getColorMappingMatrixFilter(mappings: Array<Pair<ColorRGB, ColorRGB>>) : ColorMatrixColorFilter {
    val equations = Array(4) { i ->
        val color = mappings[i].first
        floatArrayOf(color.r, color.g, color.b, 1f)
    }

    val map = Array(3) { component ->
        val output = FloatArray(4) { colorIndex -> mappings[colorIndex].second[component] }
        solveLinearSystem(equations, output)
    }

    val matrix = floatArrayOf(
        map[0][0], map[0][1], map[0][2], 0f, map[0][3],
        map[1][0], map[1][1], map[1][2], 0f, map[1][3],
        map[2][0], map[2][1], map[2][2], 0f, map[2][3],
        0f, 0f, 0f, 255f, 0f
    )

    return ColorMatrixColorFilter(matrix)
}

@Suppress("PrivatePropertyName")
private lateinit var FILTER_OSM_DARK: ColorMatrixColorFilter
@Suppress("PrivatePropertyName")
private lateinit var FILTER_OSM_LIGHT: ColorMatrixColorFilter

fun getDarkColorMatrixFilter() : ColorMatrixColorFilter {
    if (::FILTER_OSM_DARK.isInitialized) {
        return FILTER_OSM_DARK
    }

    val white = ColorRGB(255, 255, 254)
    val gray = ColorRGB(-10, -10, -10)

    val yellow = ColorRGB(255, 255, 0)
    val darkyellow = ColorRGB(150, 150, 0)

    val green = ColorRGB(0, 255, 0)
    val darkgreen = ColorRGB(50, 120, 20)

    val darkgray = ColorRGB(12, 12, 12)
    val lightgray = ColorRGB(200, 200, 200)


    FILTER_OSM_DARK = getColorMappingMatrixFilter(arrayOf(
        Pair(white, gray),
        Pair(yellow, darkyellow),
        Pair(green, darkgreen),
        Pair(darkgray, lightgray)
    ))

    return FILTER_OSM_DARK
}

fun getLightColorMatrixFilter() : ColorMatrixColorFilter {
    if (::FILTER_OSM_LIGHT.isInitialized) {
        return FILTER_OSM_LIGHT
    }

    val gray = ColorRGB(210, 210, 211)
    val white = ColorRGB(240, 240, 240)

    val yellow = ColorRGB(255, 255, 0)
    val lightyellow = ColorRGB(220, 220, 20)

    val green = ColorRGB(0, 255, 0)
    val lightgreen = ColorRGB(50, 200, 20)

    val darkgray = ColorRGB(12, 12, 12)
    val lightgray = ColorRGB(40, 40, 40)


    FILTER_OSM_LIGHT = getColorMappingMatrixFilter(arrayOf(
        Pair(gray, white),
        Pair(yellow, lightyellow),
        Pair(green, lightgreen),
        Pair(darkgray, lightgray)
    ))

    return FILTER_OSM_LIGHT
}
