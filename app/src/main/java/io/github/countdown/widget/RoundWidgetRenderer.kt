package io.github.countdown.widget

import android.content.Context
import android.graphics.*
import android.text.TextUtils
import android.text.TextPaint
import io.github.countdown.R
import io.github.countdown.domain.*
import java.text.NumberFormat
import kotlin.math.abs
import kotlin.math.min

/** Small, bounded raster for RemoteViews: transparent outside a true circle on every Android API. */
object RoundWidgetRenderer {
    fun render(context: Context, event: CountdownEvent?, snapshot: CountdownSnapshot?, progress: Boolean, widthDp: Float = 72f, heightDp: Float = 96f): Bitmap {
        val width = widthDp.coerceIn(32f, 256f)
        val height = heightDp.coerceIn(32f, 256f)
        val scale = 2f
        val bitmap = Bitmap.createBitmap((width * scale).toInt(), (height * scale).toInt(), Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap).apply { scale(scale, scale) }
        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG)
        val caption = height >= 60f
        val diameter = min(72f, min(width - 4f, height - if (caption) 22f else 4f)).coerceAtLeast(24f)
        val cx = width / 2f
        val top = (height - diameter - if (caption) 20f else 0f) / 2f
        val cy = top + diameter / 2f
        val radius = diameter / 2f - 2f
        paint.color = Color.WHITE
        canvas.drawCircle(cx, cy, radius, paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 0.8f
        paint.color = Color.LTGRAY
        canvas.drawCircle(cx, cy, radius, paint)
        val fraction = snapshot?.progress
        if (progress && fraction != null && snapshot.status != EventStatus.PAST) {
            paint.color = event?.color?.toInt() ?: Color.RED
            paint.strokeWidth = diameter * 0.065f
            val r = radius - paint.strokeWidth / 2f
            canvas.drawArc(RectF(cx - r, cy - r, cx + r, cy + r), -90f, 360f * (1f - fraction.coerceIn(0f, 1f)), false, paint)
        }
        paint.style = Paint.Style.FILL
        paint.color = Color.BLACK
        paint.typeface = Typeface.create("sans-serif", Typeface.BOLD)
        paint.textAlign = Paint.Align.CENTER
        val terminal = snapshot?.status in setOf(EventStatus.ARCHIVED, EventStatus.COMPLETED, EventStatus.EXHAUSTED)
        val weeks = event?.format == CountdownFormat.WEEKS
        val number = when {
            snapshot == null -> "+"
            terminal -> "—"
            else -> {
                val format = NumberFormat.getNumberInstance(context.resources.configuration.locales[0]).apply { maximumFractionDigits = if (weeks) 1 else 0; isGroupingUsed = false }
                (if (snapshot.status == EventStatus.PAST) "+" else "") + format.format(if (weeks) abs(snapshot.calendarDays) / 7.0 else abs(snapshot.calendarDays).toDouble())
            }
        }
        val unit = when (snapshot?.status) {
            null -> context.getString(R.string.choose_event)
            EventStatus.ARCHIVED -> context.getString(R.string.archived)
            EventStatus.COMPLETED -> context.getString(R.string.completed)
            EventStatus.EXHAUSTED -> context.getString(R.string.exhausted)
            else -> context.getString(if (weeks) R.string.circle_weeks else R.string.circle_days)
        }
        fun fit(text: String, desired: Float, available: Float) { paint.textSize = desired; val measured = paint.measureText(text); if (measured > available) paint.textSize = desired * available / measured }
        fit(number, diameter * 0.38f, diameter * 0.78f)
        canvas.drawText(number, cx, cy + diameter * 0.045f, paint)
        fit(unit, diameter * 0.12f, diameter * 0.72f)
        canvas.drawText(unit, cx, cy + diameter * 0.24f, paint)
        if (caption) {
            paint.typeface = Typeface.create("sans-serif", Typeface.NORMAL)
            paint.textSize = 12f
            paint.color = Color.WHITE
            paint.setShadowLayer(2f, 0f, 1f, Color.BLACK)
            val title = TextUtils.ellipsize(event?.title ?: context.getString(R.string.choose_event), paint, width - 4f, TextUtils.TruncateAt.END).toString()
            canvas.drawText(title, cx, top + diameter + 14f, paint)
        }
        return bitmap
    }
}
