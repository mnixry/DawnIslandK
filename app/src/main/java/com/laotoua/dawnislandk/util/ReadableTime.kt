/*
 * Copyright 2015 Hippo Seven
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.laotoua.dawnislandk.util

import android.content.Context
import android.content.res.Resources
import com.laotoua.dawnislandk.DawnApp
import com.laotoua.dawnislandk.R
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.*

object ReadableTime {
    private var sResources: Resources? = null
    private var timeFormat: String? = null
    const val SECOND_MILLIS: Long = 1000
    const val MINUTE_MILLIS = 60 * SECOND_MILLIS
    const val HOUR_MILLIS = 60 * MINUTE_MILLIS
    const val DAY_MILLIS = 24 * HOUR_MILLIS
    const val WEEK_MILLIS = 7 * DAY_MILLIS
    const val YEAR_MILLIS = 365 * DAY_MILLIS
    const val SIZE = 5
    val MULTIPLES = longArrayOf(
        YEAR_MILLIS,
        DAY_MILLIS,
        HOUR_MILLIS,
        MINUTE_MILLIS,
        SECOND_MILLIS
    )
    val UNITS = intArrayOf(
        R.plurals.year,
        R.plurals.day,
        R.plurals.hour,
        R.plurals.minute,
        R.plurals.second
    )
    private val sCalendar =
        Calendar.getInstance(TimeZone.getTimeZone("GMT+08:00"))
    private val sCalendarLock = Any()
    private val DATE_FORMAT_WITHOUT_YEAR =
        SimpleDateFormat("MM/dd", Locale.getDefault())
    val DATE_FORMAT_WITH_YEAR =
        SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    private val DATE_FORMAT =
        SimpleDateFormat("yy/MM/dd HH:mm", Locale.getDefault())
    private val BASIC_DATE_FORMAT =
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())

    private val sDateFormatLock1 = Any()
    private val FILENAMABLE_DATE_FORMAT =
        SimpleDateFormat("yyyy-MM-dd-HH-mm-ss-SSS", Locale.getDefault())
    private val sDateFormatLock2 = Any()

    fun initialize(context: Context) {
        sResources = context.applicationContext.resources
        timeFormat = DawnApp.applicationDataStore.mmkv.getString(
            Constants.TIME_FORMAT,
            Constants.DEFAULT_TIME_FORMAT
        )
    }

    fun getTodayDateLong(): Long {
        return string2Time(getDateString(Date(), DATE_FORMAT_WITH_YEAR), DATE_FORMAT_WITH_YEAR)
    }

    fun getDateString(time: Long, format: SimpleDateFormat? = null): String {
        return format?.format(Date(time)) ?: DATE_FORMAT_WITH_YEAR.format(Date(time))
    }

    fun getDateString(date: Date, format: SimpleDateFormat? = null): String {
        return format?.format(date) ?: DATE_FORMAT_WITH_YEAR.format(date)
    }

    fun string2Time(str: String, dateFormat: SimpleDateFormat = BASIC_DATE_FORMAT): Long {
        var s = str
        if (s.contains("(")) {
            s = s.substring(0, 10) + " " + s.substring(13)
        }
        var date: Date? = null
        try {
            date = dateFormat.parse(s)
        } catch (e: ParseException) {
            e.printStackTrace()
        }
        return date!!.time
    }

    fun getDisplayTime(time: String): String {
        return when (timeFormat) {
            "simplified" -> getTimeAgo(
                string2Time(time)
            )
            "original" -> getPlainTime(
                string2Time(time)
            )
            else -> throw Exception("Unhandled time format")
        }
    }

    fun getPlainTime(time: Long): String {
        synchronized(
            sDateFormatLock1
        ) { return DATE_FORMAT.format(Date(time)) }
    }

    fun getTimeAgo(time: Long): String {
        val resources = sResources
        var now = System.currentTimeMillis()
        val timeZoneShift = (TimeZone.getTimeZone("GMT+08:00").getOffset(now)
                - TimeZone.getDefault().getOffset(now)).toLong()
        now = System.currentTimeMillis() + timeZoneShift
        if (time > now + 2 * MINUTE_MILLIS || time <= 0) {
            return resources!!.getString(R.string.from_the_future)
        }
        val diff = now - time
        return if (diff < MINUTE_MILLIS) {
            resources!!.getString(R.string.just_now)
        } else if (diff < 2 * MINUTE_MILLIS) {
            resources!!.getQuantityString(R.plurals.some_minutes_ago, 1, 1)
        } else if (diff < 50 * MINUTE_MILLIS) {
            val minutes = (diff / MINUTE_MILLIS).toInt()
            resources!!.getQuantityString(R.plurals.some_minutes_ago, minutes, minutes)
        } else if (diff < 90 * MINUTE_MILLIS) {
            resources!!.getQuantityString(R.plurals.some_hours_ago, 1, 1)
        } else if (diff < 24 * HOUR_MILLIS) {
            val hours = (diff / HOUR_MILLIS).toInt()
            resources!!.getQuantityString(R.plurals.some_hours_ago, hours, hours)
        } else if (diff < 48 * HOUR_MILLIS) {
            resources!!.getString(R.string.yesterday)
        } else if (diff < WEEK_MILLIS) {
            val days = (diff / DAY_MILLIS).toInt()
            resources!!.getString(R.string.some_days_ago, days)
        } else {
            synchronized(sCalendarLock) {
                val nowDate = Date(now)
                val timeDate = Date(time)
                sCalendar.time = nowDate
                val nowYear = sCalendar[Calendar.YEAR]
                sCalendar.time = timeDate
                val timeYear = sCalendar[Calendar.YEAR]
                return if (nowYear == timeYear) {
                    DATE_FORMAT_WITHOUT_YEAR.format(timeDate)
                } else {
                    DATE_FORMAT_WITH_YEAR.format(timeDate)
                }
            }
        }
    }

    fun getTimeInterval(time: Long): String {
        val sb = StringBuilder()
        val resources = sResources
        var leftover = time
        var start = false
        for (i in 0 until SIZE) {
            val multiple = MULTIPLES[i]
            val quotient = leftover / multiple
            val remainder = leftover % multiple
            if (start || quotient != 0L || i == SIZE - 1) {
                if (start) {
                    sb.append(" ")
                }
                sb.append(quotient)
                    .append(" ")
                    .append(resources!!.getQuantityString(UNITS[i], quotient.toInt()))
                start = true
            }
            leftover = remainder
        }
        return sb.toString()
    }

    fun getFilenamableTime(time: Long): String {
        synchronized(
            sDateFormatLock2
        ) { return FILENAMABLE_DATE_FORMAT.format(Date(time)) }
    }

    init {
        // The website use GMT+08:00, so tell user the same
        DATE_FORMAT.timeZone = TimeZone.getTimeZone("GMT+08:00")
        DATE_FORMAT_WITHOUT_YEAR.timeZone = TimeZone.getTimeZone("GMT+08:00")
    }
}