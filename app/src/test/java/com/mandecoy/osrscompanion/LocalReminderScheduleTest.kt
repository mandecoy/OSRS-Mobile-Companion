package com.mandecoy.osrscompanion

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class LocalReminderScheduleTest {
    private val zone = ZoneId.of("America/New_York")

    private fun at(y: Int, m: Int, d: Int, h: Int, minute: Int): Long =
        ZonedDateTime.of(y, m, d, h, minute, 0, 0, zone).toInstant().toEpochMilli()

    @Test
    fun dailyReminderUsesChosenTimeOnNextEligibleDate() {
        assertEquals(
            at(2026, 11, 3, 19, 30),
            LocalReminderSchedule.nextTriggerMillis(
                at(2026, 11, 2, 15, 45), 1,
                java.time.LocalTime.of(19, 30),
                at(2026, 11, 3, 17, 0), zone
            )
        )
    }

    @Test
    fun changingToEarlierTimeSkipsPassedTimeToday() {
        assertEquals(
            at(2026, 11, 4, 8, 0),
            LocalReminderSchedule.nextTriggerMillis(
                at(2026, 11, 2, 15, 45), 1,
                java.time.LocalTime.of(8, 0),
                at(2026, 11, 3, 17, 0), zone
            )
        )
    }

    @Test
    fun weeklyCycleStaysAnchoredToActivationDate() {
        assertEquals(
            at(2026, 11, 8, 18, 0),
            LocalReminderSchedule.nextTriggerMillis(
                at(2026, 11, 1, 9, 0), 7,
                java.time.LocalTime.of(18, 0),
                at(2026, 11, 3, 10, 0), zone
            )
        )
    }

    @Test
    fun selectedWallClockTimeSurvivesDaylightSavingTransition() {
        assertEquals(
            at(2026, 3, 13, 9, 0),
            LocalReminderSchedule.nextTriggerMillis(
                at(2026, 3, 6, 12, 0), 7,
                java.time.LocalTime.of(9, 0),
                at(2026, 3, 7, 8, 0), zone
            )
        )
    }

    @Test
    fun sevenDayIntervalKeepsRepeatingAfterManyCycles() {
        assertEquals(
            at(2026, 11, 10, 9, 0),
            LocalReminderSchedule.nextTriggerMillis(
                at(2026, 10, 6, 12, 0), 7,
                java.time.LocalTime.of(9, 0),
                at(2026, 11, 3, 10, 0), zone
            )
        )
    }
    @Test
    fun lateWeeklyAlarmReschedulesToFollowingWeekWithoutResettingStart() {
        val started = at(2026, 10, 27, 18, 30)
        val now = at(2026, 11, 4, 11, 0)
        assertEquals(
            at(2026, 11, 10, 18, 30),
            LocalReminderSchedule.nextTriggerMillis(
                started, 7, java.time.LocalTime.of(18, 30), now, zone
            )
        )
    }

    @Test
    fun miscThreeDayReminderKeepsOriginalActivationAcrossAppUpdates() {
        val activation = at(2026, 10, 25, 9, 0)
        assertEquals(
            at(2026, 11, 6, 17, 0),
            LocalReminderSchedule.nextTriggerMillis(
                activation, 3, java.time.LocalTime.of(17, 0),
                at(2026, 11, 5, 17, 2), zone
            )
        )
    }

    @Test
    fun miscSevenDayReminderIsUnaffectedByDaylightSavingTimeChange() {
        val activation = at(2026, 10, 30, 8, 0)
        assertEquals(
            at(2026, 11, 6, 9, 0),
            LocalReminderSchedule.nextTriggerMillis(
                activation, 7, java.time.LocalTime.of(9, 0),
                at(2026, 11, 5, 16, 0), zone
            )
        )
    }

}
