package de.salomax.currencies.util

import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import de.salomax.currencies.model.Currency

/**
 * A small colored badge with a short ticker in the middle. Used as icon for assets that have
 * neither a flag nor a dedicated vector drawable (most crypto currencies and commodities).
 *
 * The drawable has no intrinsic size, so it simply fills whatever bounds its ImageView gives it -
 * the text is scaled to fit and therefore never gets stretched.
 */
class AssetBadgeDrawable(
    private val label: String,
    private val backgroundColor: Int,
    private val textColor: Int
) : Drawable() {

    private val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = backgroundColor
        style = Paint.Style.FILL
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = textColor
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        textAlign = Paint.Align.CENTER
    }
    private val textBounds = Rect()
    private val rect = RectF()

    override fun draw(canvas: Canvas) {
        val b = bounds
        if (b.isEmpty) return
        rect.set(b)
        // the ImageView clips the corners to its own shape appearance, a plain fill is enough
        canvas.drawRect(rect, backgroundPaint)

        // largest text size that still fits: at most half the height, at most 80% of the width
        textPaint.textSize = b.height() * 0.5f
        textPaint.getTextBounds(label, 0, label.length, textBounds)
        val maxWidth = b.width() * 0.8f
        if (textBounds.width() > maxWidth)
            textPaint.textSize *= maxWidth / textBounds.width()
        textPaint.getTextBounds(label, 0, label.length, textBounds)

        canvas.drawText(
            label,
            rect.centerX(),
            rect.centerY() - textBounds.exactCenterY(),
            textPaint
        )
    }

    override fun setAlpha(alpha: Int) {
        backgroundPaint.alpha = alpha
        textPaint.alpha = alpha
        invalidateSelf()
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        backgroundPaint.colorFilter = colorFilter
        textPaint.colorFilter = colorFilter
        invalidateSelf()
    }

    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT

    companion object {
        private const val WHITE = 0xFFFFFFFF.toInt()
        private const val DARK = 0xFF212121.toInt()

        /**
         * the badge for the given asset, or null if none is defined (-> caller falls back to a flag)
         */
        fun forCurrency(currency: Currency): Drawable? {
            val (label, background, text) = when (currency) {
                // crypto
                Currency.ETH -> Triple("ETH", 0xFF627EEA.toInt(), WHITE)
                Currency.XRP -> Triple("XRP", 0xFF23292F.toInt(), WHITE)
                Currency.BNB -> Triple("BNB", 0xFFF3BA2F.toInt(), DARK)
                Currency.SOL -> Triple("SOL", 0xFF9945FF.toInt(), WHITE)
                Currency.ADA -> Triple("ADA", 0xFF0033AD.toInt(), WHITE)
                Currency.DOGE -> Triple("DOGE", 0xFFC2A633.toInt(), DARK)
                Currency.DOT -> Triple("DOT", 0xFFE6007A.toInt(), WHITE)
                Currency.LTC -> Triple("LTC", 0xFF345D9D.toInt(), WHITE)
                Currency.BCH -> Triple("BCH", 0xFF8DC351.toInt(), DARK)
                Currency.LINK -> Triple("LINK", 0xFF2A5ADA.toInt(), WHITE)
                Currency.AVAX -> Triple("AVAX", 0xFFE84142.toInt(), WHITE)
                Currency.XLM -> Triple("XLM", 0xFF14B6E7.toInt(), DARK)
                // commodities
                Currency.XNG -> Triple("NG", 0xFF1E88E5.toInt(), WHITE)
                Currency.XCU -> Triple("Cu", 0xFFB87333.toInt(), WHITE)
                Currency.XWH -> Triple("WHT", 0xFFD9B44A.toInt(), DARK)
                Currency.XCN -> Triple("CORN", 0xFFF2C230.toInt(), DARK)
                Currency.XSY -> Triple("SOY", 0xFF6D8B3A.toInt(), WHITE)
                Currency.XCF -> Triple("COF", 0xFF6F4E37.toInt(), WHITE)
                Currency.XSG -> Triple("SUG", 0xFFEC407A.toInt(), WHITE)
                Currency.XCC -> Triple("COC", 0xFF5D4037.toInt(), WHITE)
                Currency.XCT -> Triple("COT", 0xFF90A4AE.toInt(), DARK)
                else -> return null
            }
            return AssetBadgeDrawable(label, background, text)
        }
    }

}
