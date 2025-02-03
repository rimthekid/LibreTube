package com.github.libretube.util

import android.content.Context
import android.icu.text.RelativeDateTimeFormatter
import android.net.Uri
import android.text.format.DateUtils
import androidx.core.text.isDigitsOnly
import com.github.libretube.BuildConfig
import com.github.libretube.R
import com.github.libretube.extensions.formatShort
import com.github.libretube.extensions.toLocalDate
import com.google.common.math.IntMath.pow
import kotlinx.datetime.toJavaLocalDate
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.temporal.ChronoUnit
import kotlin.time.Duration
import kotlinx.datetime.LocalDate as KotlinLocalDate

object TextUtils {
    /**
     * Separator used for descriptions
     */
    const val SEPARATOR = " • "

    /**
     * Reserved characters by unix which can not be used for file name.
     */
    const val RESERVED_CHARS = "?:\"*|/\\<>\u0000"

    /**
     * Date time formatter which uses the [FormatStyle.MEDIUM] format style.
     */
    private val MEDIUM_DATE_FORMATTER = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)

    /**
     * Date time formatter which doesn't use any forbidden characters for file names like ':'
     */
    private val SAFE_FILENAME_DATETIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd-HH_mm_ss")

    /**
     * Localize the date from a date string, using the medium format.
     * @param date The date to parse
     * @return localized date string
     */
    fun localizeDate(date: KotlinLocalDate): String {
        return date.toJavaLocalDate().format(MEDIUM_DATE_FORMATTER)
    }

    fun localizeInstant(instant: kotlinx.datetime.Instant): String {
        return localizeDate(instant.toLocalDate())
    }

    /**
     * Get time in seconds from a YouTube video link.
     * @return Time in seconds
     */
    fun String.toTimeInSeconds(): Long? = parseTimeString(this)?.toLong()

    fun String.parseDurationString(): Float? = parseTimeString(this)

    private fun parseTimeString(timeString: String): Float? {
        if (timeString.isDigitsOnly()) return timeString.toLongOrNull()?.toFloat()

        if (timeString.all { it.isDigit() || ",.:".contains(it) }) {
            var secondsTotal = 0
            var secondsScoped = 0

            var milliseconds = 0
            var inMillis = false
            var millisLength = 0

            for (char in timeString) {
                if (inMillis) {
                    if (!char.isDigit()) break

                    milliseconds *= 10
                    milliseconds += char.digitToInt()
                    millisLength++
                } else if (char.isDigit()) {
                    secondsScoped *= 10
                    secondsScoped += char.digitToInt()
                } else if (char == ':') {
                    secondsTotal += secondsScoped * 60
                    secondsScoped = 0
                } else if (",.".contains(char)) {
                    secondsTotal += secondsScoped
                    secondsScoped = 0
                    inMillis = true
                }
            }
            secondsTotal += secondsScoped

            val millisDecimal = milliseconds.toFloat() / pow(10, millisLength)
            return secondsTotal.toFloat() + millisDecimal
        }

        return Duration.parseOrNull(timeString)?.inWholeMilliseconds?.toFloat()?.div(1000)
    }

    /**
     * Get video id if the link is a valid youtube video link
     */
    fun getVideoIdFromUri(uri: Uri) = when (uri.host) {
        "www.youtube.com", "m.youtube.com", "piped.video" -> uri.getQueryParameter("v")
        "youtu.be" -> uri.lastPathSegment
        else -> null
    }

    fun formatRelativeDate(unixTime: Long): CharSequence {
        val date = LocalDateTime.ofInstant(Instant.ofEpochMilli(unixTime), ZoneId.systemDefault())
        val now = LocalDateTime.now()
        val months = date.until(now, ChronoUnit.MONTHS)

        return if (months > 0) {
            val years = months / 12

            val (timeFormat, time) = if (years > 0) {
                RelativeDateTimeFormatter.RelativeUnit.YEARS to years
            } else {
                RelativeDateTimeFormatter.RelativeUnit.MONTHS to months
            }
            RelativeDateTimeFormatter.getInstance()
                .format(time.toDouble(), RelativeDateTimeFormatter.Direction.LAST, timeFormat)
        } else {
            val weeks = date.until(now, ChronoUnit.WEEKS)
            val minResolution = if (weeks > 0) DateUtils.WEEK_IN_MILLIS else 0L
            DateUtils.getRelativeTimeSpanString(unixTime, System.currentTimeMillis(), minResolution)
        }
    }

    fun formatBitrate(bitrate: Int?): String {
        bitrate ?: return ""
        return "${bitrate / 1024}kbps"
    }

    fun limitTextToLength(text: String, maxLength: Int): String {
        if (text.length <= maxLength) return text
        return text.take(maxLength) + "…"
    }

    fun getUserAgent(context: Context): String {
        return "${context.packageName}/${BuildConfig.VERSION_NAME}"
    }

    fun formatViewsString(context: Context, views: Long, uploaded: Long, uploader: String? = null): String {
        val viewsString = views.takeIf { it != -1L }?.formatShort()?.let {
            context.getString(R.string.view_count, it)
        }
        val uploadDate = uploaded.takeIf { it > 0 }?.let {
            formatRelativeDate(it)
        }
        return listOfNotNull(uploader, viewsString, uploadDate).joinToString(SEPARATOR)
    }

    /**
     * Timestamp of the current time which doesn't use any forbidden characters for file names like ':'
     */
    fun getFileSafeTimeStampNow(): String {
        return SAFE_FILENAME_DATETIME_FORMATTER.format(LocalDateTime.now())
    }
}
