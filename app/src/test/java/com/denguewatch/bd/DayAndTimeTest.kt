package com.denguewatch.bd

import com.denguewatch.bd.i18n.AppLanguage
import com.denguewatch.bd.i18n.NumberStyle
import java.util.Calendar
import java.util.TimeZone
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/**
 * When a reading was taken, as the recording screens and the health card say
 * it. The expected strings are what the iOS build prints for the same moments
 * (Foundation, en_BD and bn_BD), so the two apps describe a reading alike.
 */
class DayAndTimeTest {

    private val original: TimeZone = TimeZone.getDefault()

    @Before fun useDhaka() = TimeZone.setDefault(TimeZone.getTimeZone("Asia/Dhaka"))

    @After fun restore() = TimeZone.setDefault(original)

    private val english = NumberStyle(AppLanguage.ENGLISH)
    private val bangla = NumberStyle(AppLanguage.BANGLA)

    private fun local(month: Int, day: Int, hour: Int, minute: Int): Long =
        Calendar.getInstance().apply {
            clear()
            set(2026, month, day, hour, minute)
        }.timeInMillis

    private val now = local(Calendar.OCTOBER, 14, 17, 30)

    @Test
    fun `clock time is twelve-hour in both languages`() {
        assertEquals("5:02 PM", english.timeOfDay(local(Calendar.OCTOBER, 14, 17, 2)))
        assertEquals("৫:০২ PM", bangla.timeOfDay(local(Calendar.OCTOBER, 14, 17, 2)))
        assertEquals("12:05 AM", english.timeOfDay(local(Calendar.OCTOBER, 14, 0, 5)))
    }

    @Test
    fun `the picker date carries the year`() {
        assertEquals("14 Oct, 2026", english.pickerDate(local(Calendar.OCTOBER, 14, 5, 23)))
        assertEquals("১৪ অক্টো, ২০২৬", bangla.pickerDate(local(Calendar.OCTOBER, 14, 5, 23)))
    }

    @Test
    fun `today and yesterday are named`() {
        assertEquals(
            "Today, 9:07 AM",
            english.dayAndTime(local(Calendar.OCTOBER, 14, 9, 7), "Today", "Yesterday", now),
        )
        assertEquals(
            "Yesterday, 11:40 PM",
            english.dayAndTime(local(Calendar.OCTOBER, 13, 23, 40), "Today", "Yesterday", now),
        )
        assertEquals(
            "আজ, ৯:০৭ AM",
            bangla.dayAndTime(local(Calendar.OCTOBER, 14, 9, 7), "আজ", "গতকাল", now),
        )
    }

    @Test
    fun `the past week is a weekday, anything older a date`() {
        assertEquals(
            "Monday, 9:07 AM",
            english.dayAndTime(local(Calendar.OCTOBER, 12, 9, 7), "Today", "Yesterday", now),
        )
        assertEquals(
            "Mon, 5 Oct, 9:07 PM",
            english.dayAndTime(local(Calendar.OCTOBER, 5, 21, 7), "Today", "Yesterday", now),
        )
        assertEquals(
            "সোমবার, ৯:০৭ AM",
            bangla.dayAndTime(local(Calendar.OCTOBER, 12, 9, 7), "আজ", "গতকাল", now),
        )
        assertEquals(
            "সোম, ৫ অক্টো, ৯:০৭ PM",
            bangla.dayAndTime(local(Calendar.OCTOBER, 5, 21, 7), "আজ", "গতকাল", now),
        )
    }

    @Test
    fun `the report date sets Bangla month first`() {
        assertEquals("13 Oct", english.reportedDate(local(Calendar.OCTOBER, 13, 6, 0)))
        assertEquals("অক্টোবর ১৩", bangla.reportedDate(local(Calendar.OCTOBER, 13, 6, 0)))
    }
}
