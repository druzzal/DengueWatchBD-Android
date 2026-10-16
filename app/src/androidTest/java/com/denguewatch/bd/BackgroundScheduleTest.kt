package com.denguewatch.bd

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.Configuration
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import com.denguewatch.bd.data.BackgroundRefresh
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The periodic work, against the real WorkManager.
 *
 * Both schedules are re-applied on every launch and every return to the app,
 * so what matters is that doing so changes nothing. The digest used to be
 * re-enqueued with UPDATE each time, which kept its original start while
 * taking a shorter delay — and so it went out on whatever day the reader last
 * opened the app instead of on Sunday.
 */
@RunWith(AndroidJUnit4::class)
class BackgroundScheduleTest {

    private lateinit var context: Context
    private lateinit var manager: WorkManager

    @Before
    fun useTestWorkManager() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        WorkManagerTestInitHelper.initializeTestWorkManager(
            context,
            Configuration.Builder().setExecutor(SynchronousExecutor()).build(),
        )
        manager = WorkManager.getInstance(context)
    }

    private fun only(name: String): WorkInfo =
        manager.getWorkInfosForUniqueWork(name).get().single()

    @Test
    fun reapplyingTheDigestLeavesItsScheduleAlone() {
        BackgroundRefresh.scheduleWeeklyDigest(context, enabled = true)
        val first = only(BackgroundRefresh.DIGEST_WORK)

        Thread.sleep(50)
        BackgroundRefresh.scheduleWeeklyDigest(context, enabled = true)
        BackgroundRefresh.scheduleWeeklyDigest(context, enabled = true)
        val again = only(BackgroundRefresh.DIGEST_WORK)

        assertEquals(first.id, again.id)
        assertEquals("the work was not replaced", first.generation, again.generation)
        assertEquals("the first digest did not move", first.nextScheduleTimeMillis, again.nextScheduleTimeMillis)
    }

    @Test
    fun switchingTheDigestOffAndOnSchedulesItAfresh() {
        BackgroundRefresh.scheduleWeeklyDigest(context, enabled = true)
        val before = only(BackgroundRefresh.DIGEST_WORK)

        BackgroundRefresh.scheduleWeeklyDigest(context, enabled = false)
        assertEquals(WorkInfo.State.CANCELLED, only(BackgroundRefresh.DIGEST_WORK).state)

        BackgroundRefresh.scheduleWeeklyDigest(context, enabled = true)
        val after = manager.getWorkInfosForUniqueWork(BackgroundRefresh.DIGEST_WORK).get()
            .single { it.state == WorkInfo.State.ENQUEUED }
        assertNotEquals(before.id, after.id)
    }

    @Test
    fun reapplyingTheRefreshLeavesItsScheduleAlone() {
        BackgroundRefresh.schedule(context)
        val first = only(BackgroundRefresh.REFRESH_WORK)
        BackgroundRefresh.schedule(context)
        val again = only(BackgroundRefresh.REFRESH_WORK)
        assertEquals(first.id, again.id)
        assertEquals(first.nextScheduleTimeMillis, again.nextScheduleTimeMillis)
    }
}
