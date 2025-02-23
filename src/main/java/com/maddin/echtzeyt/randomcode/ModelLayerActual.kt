package com.maddin.echtzeyt.randomcode

import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.opengl.GLUtils
import androidx.core.graphics.drawable.toBitmap
import com.maddin.echtzeyt.randomcode.Base3DModel.RenderData
import org.oscim.backend.GL
import org.oscim.backend.GLAdapter.gl
import org.oscim.core.GeoPoint
import org.oscim.core.MercatorProjection
import org.oscim.core.Tile
import org.oscim.layers.Layer
import org.oscim.renderer.GLMatrix
import org.oscim.renderer.GLState
import org.oscim.renderer.GLViewport
import org.oscim.renderer.LayerRenderer
import java.io.InputStream
import java.nio.FloatBuffer
import java.nio.IntBuffer
import kotlin.collections.Map
import kotlin.collections.component1
import kotlin.collections.component2
import kotlin.collections.set
import kotlin.math.absoluteValue
import kotlin.math.roundToInt
import org.oscim.map.Map as VMap

class ModelSetupLayer(map: VMap) : Layer(map) {
    init {
        mRenderer = ModelSetupRenderer()
    }
}

class ModelSetupRenderer : LayerRenderer() {
    init {
        isReady = true
    }
    override fun update(viewport: GLViewport?) {}

    override fun render(viewport: GLViewport?) {
        //GLState.test(true, false)
        gl.depthMask(true)
        gl.clear(GL.DEPTH_BUFFER_BIT)
        gl.depthMask(false)
    }
}


class ModelLayer(map: VMap, val layersBefore: MutableList<Layer> = mutableListOf()) : Layer(map) {
    private val modelRenderer = ModelRenderer(this)
    init {
        mRenderer = modelRenderer
    }

    fun createModelSubLayer(): ModelSubLayer {
        val subLayer = ModelSubLayer()
        modelRenderer.layers.add(subLayer)
        return subLayer
    }
    fun createInstanceSubLayer(model: Model): InstanceSubLayer {
        val subLayer = InstanceSubLayer(model)
        modelRenderer.layers.add(subLayer)
        return subLayer
    }
}

interface Model {
    fun setup()
    fun prepare(viewport: GLViewport)
    fun update(viewport: GLViewport, instance: InstanceData)
    fun begin(viewport: GLViewport)
    fun render(viewport: GLViewport, instance: InstanceData)
    fun finish(viewport: GLViewport)
}

class InstanceData (var uid: Any?, position: GeoPoint, scale: Double=SCALE_DEFAULT, alpha: Double= ALPHA_DEFAULT) {
    constructor(position: GeoPoint, scale: Double=SCALE_DEFAULT, alpha: Double=ALPHA_DEFAULT) : this(null, position, scale, alpha)
    internal var renderData: Any? = null
    internal var renderVisible = false
    internal var renderDepth = 0.0
    var changed = true; private set get() {
        if (field) {
            field = false
            return true
        }
        return false
    }
    var position = position; set(value) {
        field = value
        changed = true
        recalculateMercator()
    }
    var alpha = alpha; set(value) {
        field = value
        changed = true
    }
    var scale = scale; set(value) {
        field = value
        changed = true
    }
    var mercatorX: Double = 0.0; private set
    var mercatorY: Double = 0.0; private set
    init {
        recalculateMercator()
    }
    private fun recalculateMercator() {
        mercatorX = MercatorProjection.longitudeToX(position.longitude)
        mercatorY = MercatorProjection.latitudeToY(position.latitude)
    }
    companion object {
        private const val ALPHA_DEFAULT = 1.0
        private const val SCALE_DEFAULT = 1.0
    }
}

class ModelRenderer(val layer: ModelLayer) : LayerRenderer() {
    internal val layers = mutableListOf<SubLayer>()
    init {
        isReady = true
    }
    override fun update(viewport: GLViewport?) {
        layer.layersBefore.forEach {
            it.renderer.setup()
            it.renderer.update(viewport)
            if (it.renderer.isReady) {
                it.renderer.render(viewport)
            }
        }
    }
    override fun render(viewport: GLViewport?) {
        viewport ?: return
        layers.forEach { it.render(viewport) }
    }
}

interface SubLayer {
    fun render(viewport: GLViewport)
}

class ModelInstance(var model: Model, val data: InstanceData)

// Sub-Layer to render multiple models with very few (1-5) instances each, e.g. landmarks, selected POIs
class ModelSubLayer : SubLayer {
    private val mModels = mutableListOf<ModelInstance>()
    val models: Collection<ModelInstance>; get() = mModels
    override fun render(viewport: GLViewport) {
        mModels.forEach {
            it.model.prepare(viewport)
            it.model.update(viewport, it.data)
            if (!it.data.renderVisible) { return@forEach }

            it.model.setup()

            it.model.begin(viewport)
            it.model.render(viewport, it.data)
            it.model.finish(viewport)
        }
    }

    fun createModelInstance(model: Model, data: InstanceData): ModelInstance {
        val instance = ModelInstance(model, data)
        mModels.add(instance)
        return instance
    }
}

class LayerInstance(layer: InstanceSubLayer, val data: InstanceData) {
    var layer = layer; internal set
    fun update(viewport: GLViewport) = layer.model.update(viewport, data)
    internal val isVisible; get() = data.renderVisible
    internal val renderDepth; get() = data.renderDepth
    fun moveToLayer(newLayer: InstanceSubLayer) = newLayer.moveInstanceHere(this)
}

// Sub Layer to render multiple instances of the same model, e.g. markers
class InstanceSubLayer(val model: Model) : SubLayer {
    private val mInstances = mutableListOf<LayerInstance>()
    private var mVisibleInstances: List<LayerInstance>? = null
    val instances: Collection<LayerInstance>; get() = mInstances
    override fun render(viewport: GLViewport) {
        model.prepare(viewport)
        mVisibleInstances = mInstances.filter { it.update(viewport); it.isVisible }.sortedByDescending { it.renderDepth }
        if (mVisibleInstances.isNullOrEmpty()) { return }
        model.setup()

        model.begin(viewport)
        mVisibleInstances?.forEach { model.render(viewport, it.data) }
        model.finish(viewport)
    }
    fun createInstance(data: InstanceData): LayerInstance {
        val instance = LayerInstance(this, data)
        mInstances.add(instance)
        return instance
    }
    fun moveInstanceHere(instance: LayerInstance) {
        instance.layer.mInstances.remove(instance)
        instance.layer = this
        instance.layer.mInstances.add(instance)
    }
}


internal object ModelUtils {
    internal val BYTES_PER_FLOAT = 4 // float = 32 bit = 4 bytes

    internal fun makeShader(type: Int, source: String): Int {
        val shaderId = gl.createShader(type)
        gl.shaderSource(shaderId, source)
        gl.compileShader(shaderId)

        return shaderId
    }

    internal fun makeProgram(vertexShaderSource: String, fragmentShaderSource: String): Int {
        val vertexShader = makeShader(GL.VERTEX_SHADER, vertexShaderSource)
        val fragmentShader = makeShader(GL.FRAGMENT_SHADER, fragmentShaderSource)

        val programId = gl.createProgram()
        gl.attachShader(programId, vertexShader)
        gl.attachShader(programId, fragmentShader)
        gl.linkProgram(programId)

        gl.deleteShader(vertexShader)
        gl.deleteShader(fragmentShader)

        return programId
    }

    internal fun calcScreenPosition(mvp: FloatArray, x: Double, y: Double, z: Double, clipSpace: FloatArray) {
        val ws = 1 / (mvp[3] * x + mvp[7] * y + mvp[11] * z + mvp[15])
        clipSpace[0] = (ws * (mvp[0] * x + mvp[4] * y + mvp[8] * z + mvp[12])).toFloat()
        clipSpace[1] = (ws * (mvp[1] * x + mvp[5] * y + mvp[9] * z + mvp[13])).toFloat()
        clipSpace[2] = (ws * (mvp[2] * x + mvp[6] * y + mvp[10] * z + mvp[14])).toFloat()
    }

    internal class VisibilityState {
        var hasPartsOnTheLeft = false
        var hasPartsOnTheRight = false
        var hasPartsOnTheTop = false
        var hasPartsOnTheBottom = false
        fun reset() {
            hasPartsOnTheLeft = false
            hasPartsOnTheRight = false
            hasPartsOnTheTop = false
            hasPartsOnTheBottom = false
        }
        val spansHorizontally; get() = hasPartsOnTheLeft && hasPartsOnTheRight
        val spansVertically; get() = hasPartsOnTheTop && hasPartsOnTheBottom
    }
}

open class Base3DModel(faces: Iterable<Face>) : Model {
    override fun setup() {
        if (isReady) { return }

        GLState.bindBuffer(GL.ARRAY_BUFFER, vertexBuffer)
        gl.bufferData(GL.ARRAY_BUFFER, numCoords * ModelUtils.BYTES_PER_FLOAT, vertexTrisBuffer, GL.STATIC_DRAW)

        GLState.bindBuffer(GL.ARRAY_BUFFER, normalBuffer)
        gl.bufferData(GL.ARRAY_BUFFER, numCoords * ModelUtils.BYTES_PER_FLOAT, normalTrisBuffer, GL.STATIC_DRAW)

        GLState.bindBuffer(GL.ARRAY_BUFFER, colorBuffer)
        gl.bufferData(GL.ARRAY_BUFFER, numCoords * ModelUtils.BYTES_PER_FLOAT, colorTrisBuffer, GL.STATIC_DRAW)

        isReady = true
    }

    private val tempBuffer = IntBuffer.allocate(1)
    private val tempArray = FloatArray(16)
    private var sWasDepthMaskEnabled = false
    private var sPixelPerMeter = 0.0
    private var sTileScale = 0.0
    override fun prepare(viewport: GLViewport) {
        sPixelPerMeter = 1 / MercatorProjection.groundResolution(viewport.pos)
        sTileScale = Tile.SIZE * viewport.pos.scale
    }
    override fun begin(viewport: GLViewport) {
        GLState.useProgram(program)
        GLState.blend(true)

        GLState.test(true, false)
        sWasDepthMaskEnabled = tempBuffer.also { gl.getIntegerv(GL.DEPTH_WRITEMASK, it) }[0] != 0
        gl.depthMask(true)

        GLState.bindBuffer(GL.ARRAY_BUFFER, vertexBuffer)
        gl.vertexAttribPointer(locAttrPosition, COORDS_PER_VERTEX, GL.FLOAT, false, 0, 0)

        GLState.bindBuffer(GL.ARRAY_BUFFER, normalBuffer)
        gl.vertexAttribPointer(locAttrNormal, COORDS_PER_VERTEX, GL.FLOAT, false, 0, 0)

        GLState.bindBuffer(GL.ARRAY_BUFFER, colorBuffer)
        gl.vertexAttribPointer(locAttrColor, COORDS_PER_VERTEX, GL.FLOAT, false, 0, 0)
    }
    override fun render(viewport: GLViewport, instance: InstanceData) {
        val renderData = instance.renderData as? RenderData ?: return

        gl.uniform1f(locUnifScale, (instance.scale * sPixelPerMeter).toFloat()) // pixels per meter -> 1 unit in 3d = 1m

        renderData.mvp.setAsUniform(locUnifMvp)

        gl.drawArrays(GL.TRIANGLES, 0, numVertices)
    }

    override fun finish(viewport: GLViewport) {
        gl.depthMask(sWasDepthMaskEnabled) // Reset depth mask value
    }

    private val visibilityState = ModelUtils.VisibilityState()

    private class RenderData(val mvp: GLMatrix=GLMatrix())

    override fun update(viewport: GLViewport, instance: InstanceData) {
        if (instance.alpha < 0.005) {
            instance.renderVisible = false
            return
        }

        var renderData = instance.renderData as? RenderData
        if (!viewport.changed() && !instance.changed && renderData != null) {
            return
        }

        if (renderData == null) {
            renderData = RenderData()
            instance.renderData = renderData
        }

        val ix = (instance.mercatorX - viewport.pos.x) * sTileScale
        val iy = (instance.mercatorY - viewport.pos.y) * sTileScale

        viewport.mvp.setTransScale(ix.toFloat(), iy.toFloat(), 1f)
        viewport.mvp.multiplyMM(viewport.viewproj, viewport.mvp)
        viewport.mvp.get(tempArray)

        visibilityState.reset()
        instance.renderDepth = Double.POSITIVE_INFINITY

        var visible = false
        for (x in listOf(minX*instance.scale, maxX*instance.scale)) {
            for (y in listOf(minY*instance.scale, maxY*instance.scale)) {
                for (z in listOf(minZ*instance.scale, maxZ*instance.scale)) {
                    visible = couldBeOnScreen(tempArray, x, y, z, instance)
                    if (visible) { break }
                }
                if (visible) { break }
            }
            if (visible) { break }
        }

        if (visible) {
            renderData.mvp.copy(viewport.mvp)
        }
        instance.renderVisible = visible
    }

    private val clipSpacePos = FloatArray(3)
    private fun couldBeOnScreen(array: FloatArray, x: Double, y: Double, z: Double, instance: InstanceData): Boolean {
        ModelUtils.calcScreenPosition(array, x, y, z, clipSpacePos)

        if (clipSpacePos[2] < instance.renderDepth) { instance.renderDepth = clipSpacePos[2].toDouble() }

        if (clipSpacePos[0] < 1) {
            visibilityState.hasPartsOnTheLeft = true
        }
        if (clipSpacePos[0] > -1) {
            visibilityState.hasPartsOnTheRight = true
        }
        if (clipSpacePos[1] < 1) {
            visibilityState.hasPartsOnTheBottom = true
        }
        if (clipSpacePos[1] > -1) {
            visibilityState.hasPartsOnTheTop = true
        }

        return visibilityState.spansHorizontally && visibilityState.spansVertically
    }

    private var isReady = false
    private val vertexBuffer by lazy { tempBuffer.also { gl.genBuffers(1, it) }[0] }
    private val normalBuffer by lazy { tempBuffer.also { gl.genBuffers(1, it) }[0] }
    private val colorBuffer by lazy { tempBuffer.also { gl.genBuffers(1, it) }[0] }

    private val numVertices by lazy { numCoords / COORDS_PER_VERTEX }
    private val numCoords by lazy { vertexTris.size }
    private val vertexTris by lazy { faces.flatMap { it.toVertexBuffer() }.toFloatArray() }
    private val vertexTrisBuffer by lazy { FloatBuffer.wrap(vertexTris) }

    private val normalTris by lazy { faces.flatMap { it.toNormalBuffer() }.toFloatArray() }
    private val normalTrisBuffer by lazy { FloatBuffer.wrap(normalTris) }

    private val colorTris by lazy { faces.flatMap { it.toColorBuffer() }.toFloatArray() }
    private val colorTrisBuffer by lazy { FloatBuffer.wrap(colorTris) }

    private val program by lazy { ModelUtils.makeProgram(SHADER_VERTEX, SHADER_FRAGMENT) }
    private val locAttrPosition by lazy { gl.getAttribLocation(program, "a_position") }
    private val locAttrNormal by lazy { gl.getAttribLocation(program, "a_normal") }
    private val locAttrColor by lazy { gl.getAttribLocation(program, "a_color") }
    private val locUnifMvp by lazy { gl.getUniformLocation(program, "u_mvp") }
    private val locUnifScale by lazy { gl.getUniformLocation(program, "u_scale") }

    private val minX: Double
    private val maxX: Double
    private val minY: Double
    private val maxY: Double
    private val minZ: Double
    private val maxZ: Double

    init {
        var minX = Double.POSITIVE_INFINITY
        var maxX = Double.NEGATIVE_INFINITY
        var minY = Double.POSITIVE_INFINITY
        var maxY = Double.NEGATIVE_INFINITY
        var minZ = Double.POSITIVE_INFINITY
        var maxZ = Double.NEGATIVE_INFINITY
        for (face in faces) {
            for (vertex in face.vertices) {
                if (vertex.coords.x < minX) { minX = vertex.coords.x }
                if (vertex.coords.x > maxX) { maxX = vertex.coords.x }
                if (vertex.coords.y < minY) { minY = vertex.coords.y }
                if (vertex.coords.y > maxY) { maxY = vertex.coords.y }
                if (vertex.coords.z < minZ) { minZ = vertex.coords.z }
                if (vertex.coords.z > maxZ) { maxZ = vertex.coords.z }
            }
        }
        this.minX = minX
        this.maxX = maxX
        this.minY = minY
        this.maxY = maxY
        this.minZ = minZ
        this.maxZ = maxZ
    }

    companion object {
        internal val VERTICES_PER_FACE = 3 // we are only working with triangles
        internal val COORDS_PER_VERTEX = 3 // we are working in 3 dimensions
        private val SHADER_VERTEX = """
                precision mediump float;
                attribute vec3 a_position;
                attribute vec3 a_normal;
                attribute vec3 a_color;
                uniform mat4 u_mvp;
                uniform float u_scale;
                varying vec3 v_color;
                
                void main()  {
                    gl_Position = u_mvp * vec4(a_position * u_scale, 1.0);
                    //gl_Normal = vec4(a_normal, 1.0);
                    v_color = a_color;
                }
                """.trimIndent()
        val SHADER_FRAGMENT = """
                precision mediump float;
                varying vec3 v_color;
    
                void main() {
                    //gl_FragColor = vec4(1, 0, 0.5, 1);
                    gl_FragColor = vec4(v_color, 1);
                }
                """.trimIndent()
    }

    class Vertex(val x: Double, val y: Double, val z: Double)
    class VertexNormal(val x: Double, val y: Double, val z: Double)
    class VertexTexture()
    class VertexColor(val r: Double, val g: Double, val b: Double)
    class FVertex(val coords: Vertex, val texture: VertexTexture?=null, val normal: VertexNormal?=null, val color: VertexColor?=null)
    interface Face {
        val vertices: Collection<FVertex>
        fun toTris(): Iterable<TriFace>
        fun toVertexBuffer() = toTris().flatMap { it.toVertexBuffer() }
        fun toNormalBuffer() = toTris().flatMap { it.toNormalBuffer() }
        fun toColorBuffer() = toTris().flatMap { it.toColorBuffer() }
    }
    class TriFace(val v1: FVertex, val v2: FVertex, val v3: FVertex) : Face {
        override val vertices = listOf(v1, v2, v3)
        override fun toTris(): Iterable<TriFace> = listOf(this)
        override fun toVertexBuffer() = vertices.flatMap { listOf(it.coords.x.toFloat(), it.coords.y.toFloat(), it.coords.z.toFloat()) }
        override fun toNormalBuffer() = vertices.flatMap { listOf(it.normal?.x?.toFloat() ?: 0f, it.normal?.y?.toFloat() ?: 0f, it.normal?.z?.toFloat() ?: 0f) }
        override fun toColorBuffer() = vertices.flatMap { listOf(it.color?.r?.toFloat() ?: 1f, it.color?.g?.toFloat() ?: 0f, it.color?.b?.toFloat() ?: 0.5f) }
    }
    class TriStripFace(override val vertices: List<FVertex>) : Face {
        // Thanks to https://gamedev.stackexchange.com/a/207779 for this method
        override fun toTris(): Iterable<TriFace> {
            var start = 0
            var end = vertices.size - 1
            val tris = mutableListOf<TriFace>()
            while (true) {
                if (end - start <= 1) { break }
                tris.add(TriFace(vertices[start], vertices[end-1], vertices[end]))
                end--
                if (end - start <= 1) { break }
                tris.add(TriFace(vertices[start], vertices[start+1], vertices[end]))
                start++
            }
            return tris
        }
    }
}

class WavefrontModel : Base3DModel {
    //constructor(zip: InputStream) : super()
    constructor(obj: InputStream, mtl: InputStream?) : super(parse(obj) { mtl })
    constructor(obj: InputStream, resolver: (filename: String) -> InputStream?) : super(parse(obj, resolver))

    companion object {
        private fun parse(obj: InputStream, resolver: (filename: String) -> InputStream?): Iterable<Face> {
            val vertices = mutableListOf<Vertex>()
            val vertexNormals = mutableListOf<VertexNormal>()
            val faces = mutableListOf<Face>()
            val colors = mutableMapOf<String, VertexColor>()
            var currentColor: VertexColor? = null
            obj.bufferedReader().lines().forEach { line ->
                val parts = line.trim().split(" ").map { it.trim() }
                when (parts[0]) {
                    "mtllib" -> resolver(parts[1])?.let { loadMtl(it, resolver).forEach { (s, vertexColor) -> colors[s] = vertexColor } }
                    "usemtl" -> currentColor = colors[parts[1]]
                    "v" -> vertices.add(Vertex(parts[1].toDouble(), parts[2].toDouble(), parts[3].toDouble()))
                    "vn" -> vertexNormals.add(VertexNormal(parts[1].toDouble(), parts[2].toDouble(), parts[3].toDouble()))
                    "f" -> faces.add(TriStripFace(parts.mapIndexedNotNull { index, s ->
                        if (index == 0) { return@mapIndexedNotNull null }
                        val indices = s.split("/")
                        val vertex = vertices[indices[0].toInt()-1]
                        val vertexTexture = null
                        val vertexNormal = indices[2].toIntOrNull()?.let { vertexNormals[it-1] }
                        return@mapIndexedNotNull FVertex(vertex, vertexTexture, vertexNormal, currentColor)
                    }))
                }
            }
            return faces
        }

        private fun loadMtl(mtl: InputStream, resolver: (filename: String) -> InputStream?): Map<String, VertexColor> {
            val colors = mutableMapOf<String, VertexColor>()
            var currentColorName = ""
            mtl.bufferedReader().lines().forEach { line ->
                val parts = line.trim().split(" ").map { it.trim() }
                when (parts[0]) {
                    "newmtl" -> currentColorName = parts[1]
                    "Kd" -> colors[currentColorName] = VertexColor(parts[1].toDouble(), parts[2].toDouble(), parts[3].toDouble())
                }
            }
            return colors
        }
    }
}

class MarkerModel(private val bitmap: Bitmap) : Model {
    constructor(drawable: Drawable) : this(drawable.toBitmap(config= Bitmap.Config.ARGB_8888))
    constructor(drawable: Drawable, maxWidth: Int, maxHeight: Int?=null) : this(drawable.toBitmap(maxWidth, maxHeight ?: ((drawable.intrinsicHeight.toDouble() / drawable.intrinsicWidth) * maxWidth).roundToInt(), Bitmap.Config.ARGB_8888))

    private val tempArray = FloatArray(16)
    private val tempBuffer = IntBuffer.allocate(1)
    private val clipSpacePos = FloatArray(3)

    private var isReady = false
    private val vertexBuffer by lazy { tempBuffer.also { gl.genBuffers(1, it) }[0] }
    private val textureBuffer by lazy { tempBuffer.also { gl.genTextures(1, it) }[0] }
    var scale = 1.0
    var alpha = 1.0

    private val program by lazy { ModelUtils.makeProgram(SHADER_VERTEX, SHADER_FRAGMENT) }
    private val locAttribCorner by lazy { gl.getAttribLocation(program, "a_corner") }
    private val locUnifScale by lazy { gl.getUniformLocation(program, "u_scale") }
    private val locUnifPos by lazy { gl.getUniformLocation(program, "u_pos") }
    private val locUnifAlpha by lazy { gl.getUniformLocation(program, "u_alpha") }
    private val locUnifTexture by lazy { gl.getUniformLocation(program, "u_texture") }

    private var sWasDepthMaskEnabled = false
    private var sTileScale = 0.0
    private var sWidthGlF = 0.0
    private var sHeightGlF = 0.0

    override fun setup() {
        if (isReady) { return }

        GLState.bindBuffer(GL.ARRAY_BUFFER, vertexBuffer)
        val buffer = FloatBuffer.wrap(floatArrayOf(-0.5f, 0f,  -0.5f, 1f,  0.5f, 0f,  0.5f, 1f))
        gl.bufferData(GL.ARRAY_BUFFER, 8 * ModelUtils.BYTES_PER_FLOAT, buffer, GL.STATIC_DRAW)

        GLState.bindTex2D(textureBuffer)
        gl.texParameteri(GL.TEXTURE_2D, GL.TEXTURE_MIN_FILTER, GL.LINEAR)
        gl.texParameteri(GL.TEXTURE_2D, GL.TEXTURE_MAG_FILTER, GL.LINEAR)
        gl.texParameteri(GL.TEXTURE_2D, GL.TEXTURE_WRAP_S, GL.CLAMP_TO_EDGE)
        gl.texParameteri(GL.TEXTURE_2D, GL.TEXTURE_WRAP_T, GL.CLAMP_TO_EDGE)
        GLUtils.texImage2D(GL.TEXTURE_2D, 0, bitmap, 0)

        isReady = true
    }

    override fun prepare(viewport: GLViewport) {
        sTileScale = Tile.SIZE * viewport.pos.scale
        sWidthGlF = 2.0 / viewport.width
        sHeightGlF = 2.0 / viewport.height
    }

    override fun begin(viewport: GLViewport) {
        GLState.useProgram(program)
        GLState.blend(true)
        GLState.test(true, false)
        sWasDepthMaskEnabled = tempBuffer.also { gl.getIntegerv(GL.DEPTH_WRITEMASK, it) }[0] != 0
        gl.depthMask(true)
        gl.depthFunc(GL.ALWAYS)

        gl.activeTexture(GL.TEXTURE0)
        GLState.bindTex2D(textureBuffer)
        gl.uniform1i(locUnifTexture, 0)
    }
    override fun render(viewport: GLViewport, instance: InstanceData) {
        val renderData = instance.renderData as? RenderData ?: return

        gl.uniform2f(locUnifScale, renderData.widthGl.toFloat(), renderData.heightGl.toFloat())

        gl.uniform3f(locUnifPos, renderData.clipSpacePos[0], renderData.clipSpacePos[1], renderData.clipSpacePos[2])

        gl.uniform1f(locUnifAlpha, renderData.alpha.toFloat())

        GLState.bindBuffer(GL.ARRAY_BUFFER, vertexBuffer)
        gl.vertexAttribPointer(locAttribCorner, 2, GL.FLOAT, false, 0, 0)

        gl.drawArrays(GL.TRIANGLE_STRIP, 0, 4)
    }

    override fun finish(viewport: GLViewport) {
        gl.depthMask(sWasDepthMaskEnabled) // Reset depth mask value
        gl.depthFunc(GL.LESS)
    }

    override fun update(viewport: GLViewport, instance: InstanceData) {
        val iAlpha = alpha * instance.alpha
        if (iAlpha < 0.005) {
            instance.renderVisible = false
            return
        }

        var renderData = instance.renderData as? RenderData
        if (!viewport.changed() && !instance.changed && renderData != null) {
            return
        }

        if (renderData == null) {
            renderData = RenderData()
            instance.renderData = renderData
        }

        val ix = (instance.mercatorX - viewport.pos.x) * sTileScale
        val iy = (instance.mercatorY - viewport.pos.y) * sTileScale

        renderData.widthGl = sWidthGlF * scale * instance.scale * bitmap.width
        renderData.heightGl = sHeightGlF * scale * instance.scale * bitmap.height

        viewport.mvp.setTransScale(ix.toFloat(), iy.toFloat(), 1f)
        viewport.mvp.multiplyMM(viewport.viewproj, viewport.mvp)
        viewport.mvp.get(tempArray)
        ModelUtils.calcScreenPosition(tempArray, 0.0, 0.0, 0.0, renderData.clipSpacePos)

        val visible =
            (renderData.clipSpacePos[0].absoluteValue - renderData.widthGl / 2) < 1 &&
            (renderData.clipSpacePos[1] in -1-renderData.heightGl..1.0)

        if (visible) {
            renderData.alpha = iAlpha
            instance.renderDepth = renderData.clipSpacePos[2].toDouble()
        }
        instance.renderVisible = visible
    }

    private class RenderData(val clipSpacePos: FloatArray=FloatArray(3), var alpha: Double=1.0, var widthGl: Double=0.0, var heightGl: Double=0.0)

    companion object {
        val SHADER_VERTEX = """
                attribute vec2 a_corner;
                uniform vec3 u_pos;
                uniform vec2 u_scale;
                varying vec2 v_texture;
                
                void main()  {
                    gl_Position = vec4(u_pos, 1) + vec4(u_scale * a_corner, 0, 0);
                    v_texture = a_corner;
                }
            """.trimIndent()
        val SHADER_FRAGMENT = """
                uniform sampler2D u_texture;
                uniform float u_alpha;
                varying vec2 v_texture;
                
                void main() {
                    vec4 color = texture2D(u_texture, vec2(1, -1) * v_texture + vec2(0.5, 1));
                    gl_FragColor = color * u_alpha;
                }
            """.trimIndent()
    }
}