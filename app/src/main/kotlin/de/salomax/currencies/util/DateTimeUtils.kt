package de.salomax.currencies.util

import android.content.Context
import android.text.format.DateUtils
import de.salomax.currencies.R
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.concurrent.TimeUnit

/**
 * Converts a Unix timestamp to a LocalDate
 */
fun Long.toLocalDate(): LocalDate = Instant.ofEpochMilli(this)
    .atZone(ZoneOffset.UTC)
    .toLocalDate()

/**
 * Converts a LocalDate to a Unix timestamp
 */
fun LocalDate.toMillis() = this
    .atStartOfDay(ZoneOffset.UTC)
    .toInstant()
    .toEpochMilli()

/**
 * Formats a timestamp as human-readable relative time:
 * "just now" (less than a minute ago), "x min ago" (less than an hour ago),
 * "x h ago" (less than a day ago), else a localized date.
 */
fun Long.toRelativeTimeString(context: Context): String {
    val elapsedMillis = System.currentTimeMillis() - this
    return when {
        elapsedMillis < TimeUnit.MINUTES.toMillis(1) ->
            context.getString(R.string.updated_just_now)
        elapsedMillis < TimeUnit.HOURS.toMillis(1) ->
            context.getString(R.string.updated_x_minutes_ago, TimeUnit.MILLISECONDS.toMinutes(elapsedMillis).toInt())
        elapsedMillis < TimeUnit.DAYS.toMillis(1) ->
            context.getString(R.string.updated_x_hours_ago, TimeUnit.MILLISECONDS.toHours(elapsedMillis).toInt())
        else ->
            DateUtils.formatDateTime(
                context,
                this,
                DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_YEAR or DateUtils.FORMAT_ABBREV_MONTH
            )
    }
}
