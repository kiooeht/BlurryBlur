package blurryblur

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.GL30
import com.badlogic.gdx.graphics.Pixmap
import com.badlogic.gdx.graphics.g2d.Batch
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.badlogic.gdx.graphics.g2d.TextureRegion
import com.badlogic.gdx.graphics.glutils.FrameBuffer
import com.badlogic.gdx.graphics.glutils.ShaderProgram
import com.badlogic.gdx.utils.BufferUtils
import com.evacipated.cardcrawl.modthespire.lib.*
import com.megacrit.cardcrawl.cards.AbstractCard
import com.megacrit.cardcrawl.cards.green.Blur
import com.megacrit.cardcrawl.core.Settings
import com.megacrit.cardcrawl.screens.SingleCardViewPopup
import javassist.CtBehavior
import javassist.expr.ExprEditor
import javassist.expr.MethodCall
import kotlin.system.exitProcess

object CardPatches {
    private val shader by lazy {
        ShaderProgram(
            Gdx.files.internal("blurryblurResources/vert.vs"),
            Gdx.files.internal("blurryblurResources/frag.fs"),
        ).apply {
            if (!isCompiled) {
                System.err.println(log)
                exitProcess(0);
            }
            if (log.isNotEmpty()) {
                println(log)
            }
            begin()
            setUniformf("dir", 0f, 0f)
            setUniformf("resolution", Gdx.graphics.width.toFloat())
            setUniformf("radius", blurRadius)
            end()
        }
    }
    private var saveShader: ShaderProgram? = null
    private val saveEquations = arrayOf(0, 0)
    private val fbo1 by lazy { FrameBuffer(Pixmap.Format.RGBA8888, Gdx.graphics.width, Gdx.graphics.height, false) }
    private val fbo2 by lazy { FrameBuffer(Pixmap.Format.RGBA8888, Gdx.graphics.width, Gdx.graphics.height, false) }
    private val fboRegion by lazy { TextureRegion(fbo1.colorBufferTexture).also { it.flip(false, true) } }

    private var blurRadius = 2f

    fun begin(b: Batch, blurRadius: Float) {
        this.blurRadius = blurRadius

        saveShader = b.shader
        b.end()

        fbo1.begin()
        Gdx.gl.glClearColor(0f, 0f, 0f, 0f)
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT)

        val buf_rgb = BufferUtils.newIntBuffer(16)
        val buf_a = BufferUtils.newIntBuffer(16)
        Gdx.gl.glGetIntegerv(GL20.GL_BLEND_EQUATION_RGB, buf_rgb)
        Gdx.gl.glGetIntegerv(GL20.GL_BLEND_EQUATION_ALPHA, buf_a)
        saveEquations[0] = buf_rgb[0]
        saveEquations[1] = buf_a[0]
        Gdx.gl.glBlendEquationSeparate(saveEquations[0], GL30.GL_MAX)
        b.begin()
    }

    fun end(b: Batch) {
        b.flush()
        fbo1.end()
        Gdx.gl.glBlendEquationSeparate(saveEquations[0], saveEquations[1])

        b.shader = shader
        shader.setUniformf("dir", 1f, 0f)
        shader.setUniformf("resolution", Gdx.graphics.width.toFloat())
        shader.setUniformf("radius", blurRadius)

        fbo2.begin()
        Gdx.gl.glClearColor(0f, 0f, 0f, 0f)
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT)

        b.setBlendFunction(GL20.GL_ONE, GL20.GL_ONE_MINUS_SRC_ALPHA)
        fboRegion.texture = fbo1.colorBufferTexture
        b.draw(fboRegion, -Settings.VERT_LETTERBOX_AMT.toFloat(), -Settings.HORIZ_LETTERBOX_AMT.toFloat())

        b.flush()
        fbo2.end()

        shader.setUniformf("dir", 0f, 1f)
        shader.setUniformf("resolution", Gdx.graphics.height.toFloat())

        b.setBlendFunction(GL20.GL_ONE, GL20.GL_ONE_MINUS_SRC_ALPHA)

        fboRegion.texture = fbo2.colorBufferTexture
        b.draw(fboRegion, -Settings.VERT_LETTERBOX_AMT.toFloat(), -Settings.HORIZ_LETTERBOX_AMT.toFloat())
        b.flush()

        b.setBlendFunction(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA)

        b.shader = saveShader
        saveShader = null
    }

    @SpirePatches2(
        SpirePatch2(
            clz = AbstractCard::class,
            method = "renderCard"
        ),
        SpirePatch2(
            clz = AbstractCard::class,
            method = "renderInLibrary"
        ),
    )
    object NormalRender {
        @JvmStatic
        @SpireInsertPatch(
            locator = LocatorBefore::class
        )
        fun InsertBefore(__instance: AbstractCard, sb: SpriteBatch) {
            if (__instance is Blur) {
                begin(sb, 2f)
            }
        }

        @JvmStatic
        fun InsertAfter(__instance: AbstractCard, sb: SpriteBatch) {
            if (__instance is Blur) {
                end(sb)
            }
        }

        @JvmStatic
        fun Instrument(): ExprEditor =
            object : ExprEditor() {
                override fun edit(m: MethodCall) {
                    if (m.className == AbstractCard::class.qualifiedName && m.methodName == "renderEnergy") {
                        m.replace(
                            "\$_ = \$proceed(\$\$);" +
                                    "${NormalRender::class.qualifiedName}.InsertAfter(this, sb);"
                        )
                    }
                }
            }

        private class LocatorBefore : SpireInsertLocator() {
            override fun Locate(ctBehavior: CtBehavior?): IntArray {
                val finalMatcher = Matcher.MethodCallMatcher(AbstractCard::class.java, "renderGlow")
                return LineFinder.findInOrder(ctBehavior, finalMatcher)
            }
        }
    }

    @SpirePatch2(
        clz = SingleCardViewPopup::class,
        method = "render"
    )
    object SCVRender {
        @JvmStatic
        @SpireInsertPatch(
            locator = LocatorBefore::class
        )
        fun InsertBefore(sb: SpriteBatch, ___card: AbstractCard) {
            if (___card is Blur) {
                begin(sb, 4f)
            }
        }

        @JvmStatic
        @SpireInsertPatch(
            locator = LocatorAfter::class
        )
        fun InsertAfter(sb: SpriteBatch, ___card: AbstractCard) {
            if (___card is Blur) {
                end(sb)
            }
        }

        private class LocatorBefore : SpireInsertLocator() {
            override fun Locate(ctBehavior: CtBehavior?): IntArray {
                val finalMatcher = Matcher.MethodCallMatcher(SingleCardViewPopup::class.java, "renderCardBack")
                return LineFinder.findInOrder(ctBehavior, finalMatcher)
            }
        }

        private class LocatorAfter : SpireInsertLocator() {
            override fun Locate(ctBehavior: CtBehavior?): IntArray {
                val finalMatcher = Matcher.MethodCallMatcher(SingleCardViewPopup::class.java, "renderArrows")
                return LineFinder.findInOrder(ctBehavior, finalMatcher)
            }
        }
    }
}
