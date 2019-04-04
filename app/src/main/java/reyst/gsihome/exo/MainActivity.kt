package reyst.gsihome.exo

import android.net.Uri
import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.view.ViewGroup
import android.widget.FrameLayout
import com.daasuu.epf.EFramebufferObject
import com.daasuu.epf.EPlayerView
import com.daasuu.epf.filter.GlFilter
import com.google.android.exoplayer2.ExoPlayerFactory
import com.google.android.exoplayer2.Player
import com.google.android.exoplayer2.SimpleExoPlayer
import com.google.android.exoplayer2.source.hls.HlsMediaSource
import com.google.android.exoplayer2.trackselection.AdaptiveTrackSelection
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector
import com.google.android.exoplayer2.upstream.DefaultBandwidthMeter
import com.google.android.exoplayer2.upstream.DefaultDataSourceFactory
import com.google.android.exoplayer2.upstream.DefaultHttpDataSource
import com.google.android.exoplayer2.upstream.DefaultHttpDataSourceFactory
import com.google.android.exoplayer2.util.Util


class MainActivity : AppCompatActivity() {

    private lateinit var player: SimpleExoPlayer
    private lateinit var ePlayerView: EPlayerView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val bandwidthMeter = DefaultBandwidthMeter()
        val videoTrackSelectionFactory = AdaptiveTrackSelection.Factory(bandwidthMeter)
        val trackSelector = DefaultTrackSelector(videoTrackSelectionFactory)

        // 3. Create the player
        player = ExoPlayerFactory.newSimpleInstance(this, trackSelector)

        val url = "https://player.vimeo.com/external/276426755.m3u8?s\u003d9e53dc04f76f25c0ffd3e9fc3e6e762ed38c6c05\u0026oauth2_token_id\u003d1167604692"

        val mp4VideoUri = Uri.parse(url)
        val userAgent = Util.getUserAgent(this, applicationInfo.name)

        // Default parameters, except allowCrossProtocolRedirects is true
        val httpDataSourceFactory = DefaultHttpDataSourceFactory(
            userAgent,
            null /* listener */,
            DefaultHttpDataSource.DEFAULT_CONNECT_TIMEOUT_MILLIS,
            DefaultHttpDataSource.DEFAULT_READ_TIMEOUT_MILLIS,
            true /* allowCrossProtocolRedirects */
        )

        val dataSourceFactory = DefaultDataSourceFactory(
            this, null,
            httpDataSourceFactory
        )/* listener */

        val source = HlsMediaSource.Factory(dataSourceFactory).createMediaSource(mp4VideoUri)

        // Prepare the player with the source.
        player.repeatMode = Player.REPEAT_MODE_ALL
        player.prepare(source)

        player.playWhenReady = true

        ePlayerView = EPlayerView(this)
        // set SimpleExoPlayer
        ePlayerView.setSimpleExoPlayer(player)
        ePlayerView.layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        // add ePlayerView to WrapperView
        findViewById<FrameLayout>(R.id.content).addView(ePlayerView)

        ePlayerView.setGlFilter(VimeoMaskFilter())
    }

    override fun onResume() {
        super.onResume()
        ePlayerView.onResume()
    }
}

class VimeoMaskFilter : GlFilter(VERTEX_SHADER, MASK_SHADER) {
    companion object {

        private const val VERTEX_SHADER =
                "uniform mat4 uMVPMatrix;\n" +
                "uniform mat4 uSTMatrix;\n" +
                "attribute vec4 aPosition;\n" +
                "attribute vec4 aTextureCoord;\n" +
                "varying vec2 vTextureCoord;\n" +
                "void main() {\n" +
                "  gl_Position = uMVPMatrix * aPosition;\n" +
                "  vTextureCoord = (uSTMatrix * aTextureCoord).xy;\n" +
                "}\n"

        private const val MASK_SHADER =
            "#extension GL_OES_EGL_image_external : require\n" +
                "        precision mediump float;\n" +
                "        varying vec2 vTextureCoord;\n" +
                "        vec2 maskTextureCoord;\n" +
                "        uniform samplerExternalOES sTexture;\n" +
                "        void main() {\n" +
                "            vec4 color = texture2D(sTexture, vTextureCoord);\n" +
                "            maskTextureCoord = vec2(vTextureCoord.x,vTextureCoord.y+0.5);\n" +
                "            vec4 maskColor = texture2D(sTexture, maskTextureCoord);\n" +
                "\n" +
                "            float a = 1.0;\n" +
                "            if((maskColor.r + maskColor.g + maskColor.b) / 3.0 < 0.5){\n" +
                "               a = 0.0;\n" +
                "            };\n" +
                "            gl_FragColor = vec4(color.r, color.g, color.b, a);\n" +
                "        }"

        private const val FLOAT_SIZE_BYTES = 4
        private const val TRIANGLE_VERTICES_DATA_STRIDE_BYTES = 5 * FLOAT_SIZE_BYTES
        private const val TRIANGLE_VERTICES_DATA_POS_OFFSET = 0
        private const val TRIANGLE_VERTICES_DATA_UV_OFFSET = 3

        private const val GL_TEXTURE_EXTERNAL_OES = 0x8D65

    }

    private val triangleVerticesData = floatArrayOf(
        // X, Y, Z, U, V
        -1.0f, -1.0f, 0f, 0f, 0f, 1.0f, -1.0f, 0f, 1f, 0f, -1.0f, 1.0f, 0f, 0f, 1f, 1.0f, 1.0f, 0f, 1f, 1f
    )

    private val triangleVertices: FloatBuffer

    private val mVPMatrix = FloatArray(16)
    private val sTMatrix = FloatArray(16)

    private var textureID: Int = 0

    init {
        triangleVertices = ByteBuffer.allocateDirect(
            triangleVerticesData.size * FLOAT_SIZE_BYTES
        )
            .order(ByteOrder.nativeOrder()).asFloatBuffer()
        triangleVertices.put(triangleVerticesData).position(0)

        Matrix.setIdentityM(sTMatrix, 0)
    }

    override fun setup() {
        super.setup()

        val textures = IntArray(1)
        GLES20.glGenTextures(1, textures, 0)

        textureID = textures[0]
        GLES20.glBindTexture(GL_TEXTURE_EXTERNAL_OES, textureID)

        GLES20.glTexParameterf(
            GL_TEXTURE_EXTERNAL_OES,
            GLES20.GL_TEXTURE_MIN_FILTER,
            GLES20.GL_NEAREST.toFloat()
        )

        GLES20.glTexParameterf(
            GL_TEXTURE_EXTERNAL_OES,
            GLES20.GL_TEXTURE_MAG_FILTER,
            GLES20.GL_LINEAR.toFloat()
        )
    }

    override fun draw(texName: Int, fbo: EFramebufferObject?) {

//        GLES20.glClear(GLES20.GL_DEPTH_BUFFER_BIT or GLES20.GL_COLOR_BUFFER_BIT)

        GLES20.glEnable(GLES20.GL_BLEND)
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA)
        GLES20.glClearColor(0.0f, 0.0f, 0.0f, 0.0f)

        useProgram()

        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GL_TEXTURE_EXTERNAL_OES, textureID)

        triangleVertices.position(TRIANGLE_VERTICES_DATA_POS_OFFSET)
        GLES20.glVertexAttribPointer(
            getHandle("aPosition"), 3, GLES20.GL_FLOAT, false,
            TRIANGLE_VERTICES_DATA_STRIDE_BYTES, triangleVertices
        )
        GLES20.glEnableVertexAttribArray(getHandle("aPosition"))

        triangleVertices.position(TRIANGLE_VERTICES_DATA_UV_OFFSET)
        GLES20.glVertexAttribPointer(
            getHandle("aTextureCoord"), 3, GLES20.GL_FLOAT, false,
            TRIANGLE_VERTICES_DATA_STRIDE_BYTES, triangleVertices
        )
        GLES20.glEnableVertexAttribArray(getHandle("aTextureCoord"))

        Matrix.setIdentityM(mVPMatrix, 0)
        GLES20.glUniformMatrix4fv(getHandle("uMVPMatrix"), 1, false, mVPMatrix, 0)
        GLES20.glUniformMatrix4fv(getHandle("uSTMatrix"), 1, false, sTMatrix, 0)

        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)

//        GLES20.glFinish()

    }


}
