package blurryblur

import com.badlogic.gdx.graphics.g2d.PolygonSpriteBatch
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.esotericsoftware.spine.SkeletonMeshRenderer
import com.evacipated.cardcrawl.modthespire.lib.*
import com.megacrit.cardcrawl.cards.green.Blur
import com.megacrit.cardcrawl.characters.AbstractPlayer
import com.megacrit.cardcrawl.core.CardCrawlGame
import com.megacrit.cardcrawl.powers.AbstractPower
import javassist.CtBehavior

object PlayerPatches {
    @SpirePatch2(
        clz = AbstractPlayer::class,
        method = "renderPlayerImage"
    )
    object Model {
        @JvmStatic
        @SpireInsertPatch(
            locator = LocatorBefore::class
        )
        fun InsertBefore(__instance: AbstractPlayer) {
            val blur = __instance.getPower(Blur.ID)
            if (blur != null) {
                CardPatches.begin(CardCrawlGame.psb, blur.amount.toFloat())
            }
        }

        @JvmStatic
        @SpireInsertPatch(
            locator = LocatorAfter::class
        )
        fun InsertAfter(__instance: AbstractPlayer) {
            if (__instance.hasPower(Blur.ID)) {
                CardPatches.end(CardCrawlGame.psb)
            }
        }

        private class LocatorBefore : SpireInsertLocator() {
            override fun Locate(ctBehavior: CtBehavior?): IntArray {
                val finalMatcher = Matcher.MethodCallMatcher(SkeletonMeshRenderer::class.java, "draw")
                return LineFinder.findInOrder(ctBehavior, finalMatcher)
            }
        }

        private class LocatorAfter : SpireInsertLocator() {
            override fun Locate(ctBehavior: CtBehavior?): IntArray {
                val finalMatcher = Matcher.MethodCallMatcher(PolygonSpriteBatch::class.java, "end")
                return LineFinder.findInOrder(ctBehavior, finalMatcher)
            }
        }
    }

    @SpirePatch2(
        clz = AbstractPower::class,
        method = "renderIcons"
    )
    object BlurPowerIcon {
        @JvmStatic
        fun Prefix(__instance: AbstractPower, sb: SpriteBatch) {
            if (__instance.ID == Blur.ID) {
                CardPatches.begin(sb, __instance.amount.toFloat())
            }
        }

        @JvmStatic
        fun Postfix(__instance: AbstractPower, sb: SpriteBatch) {
            if (__instance.ID == Blur.ID) {
                CardPatches.end(sb)
            }
        }
    }
}
