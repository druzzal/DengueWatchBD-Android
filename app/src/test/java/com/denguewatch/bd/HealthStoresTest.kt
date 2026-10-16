package com.denguewatch.bd

import com.denguewatch.bd.data.CaseLogEntry
import com.denguewatch.bd.data.CaseLogStore
import com.denguewatch.bd.data.FluidStore
import com.denguewatch.bd.data.LabStore
import com.denguewatch.bd.data.VitalsStore
import com.denguewatch.bd.model.DengueTest
import com.denguewatch.bd.model.FluidEntry
import com.denguewatch.bd.model.LabMeasure
import com.denguewatch.bd.model.LabReport
import com.denguewatch.bd.model.TestResult
import com.denguewatch.bd.model.VitalsEntry
import java.io.File
import java.nio.file.Files
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * The four stores that hold the reader's own record.
 *
 * Each is one JSON file, and everything else in My health — the latest check,
 * the care plan, the tiles, the log — reads from them. These pin what a store
 * promises: newest first whatever order things arrive in, nothing empty kept,
 * a deletion that sticks, and a record that survives being read back.
 */
class HealthStoresTest {

    private lateinit var directory: File
    private val hour = 3_600_000L
    private val now = System.currentTimeMillis()

    @Before
    fun makeDirectory() {
        directory = Files.createTempDirectory("stores").toFile()
    }

    @After
    fun removeDirectory() {
        directory.deleteRecursively()
    }

    private fun file(name: String) = File(directory, name)

    // MARK: - Vitals

    @Test
    fun `readings are kept newest first whatever order they are entered in`() {
        val store = VitalsStore(file("vitals.json"))
        // A fever taken at three in the morning, written up at nine after a
        // reading taken at eight.
        store.add(VitalsEntry(dateMillis = now - 1 * hour, pulse = 90.0))
        store.add(VitalsEntry(dateMillis = now - 6 * hour, temperature = 39.4))
        assertEquals("the latest reading is the newest one", 90.0, store.latest?.pulse)
        assertEquals(listOf(now - 1 * hour, now - 6 * hour), store.entries.map { it.dateMillis })
    }

    @Test
    fun `an entry with no readings is not saved`() {
        val store = VitalsStore(file("vitals.json"))
        store.add(VitalsEntry(note = "felt fine"))
        assertTrue(store.entries.isEmpty())
    }

    @Test
    fun `readings survive being read back`() {
        val path = file("vitals.json")
        VitalsStore(path).add(
            VitalsEntry(dateMillis = now, temperature = 38.6, systolic = 100.0, diastolic = 70.0, note = "after lunch"),
        )
        val reloaded = VitalsStore(path).entries.single()
        assertEquals(38.6, reloaded.temperature!!, 0.0)
        assertEquals(100.0, reloaded.systolic!!, 0.0)
        assertEquals(70.0, reloaded.diastolic!!, 0.0)
        assertEquals("after lunch", reloaded.note)
    }

    @Test
    fun `a deletion is kept`() {
        val path = file("vitals.json")
        val store = VitalsStore(path)
        val keep = VitalsEntry(dateMillis = now - hour, pulse = 80.0)
        val drop = VitalsEntry(dateMillis = now, pulse = 120.0)
        store.add(keep)
        store.add(drop)
        store.delete(drop.id)
        assertEquals(listOf(keep.id), VitalsStore(path).entries.map { it.id })
    }

    @Test
    fun `an unreadable file loads as empty rather than crashing`() {
        val path = file("vitals.json")
        path.writeText("[{\"id\":")
        assertTrue(VitalsStore(path).entries.isEmpty())
    }

    // MARK: - Labs

    @Test
    fun `a report with only a test result still keeps the counts before it on show`() {
        val store = LabStore(file("labs.json"))
        store.add(LabReport(dateMillis = now - 24 * hour, platelets = 96.0, haematocrit = 44.0))
        store.add(LabReport(dateMillis = now, ns1 = TestResult.POSITIVE))

        assertNull("the newest report has no count", store.latest?.platelets)
        assertEquals(96.0, store.mostRecent(LabMeasure.PLATELETS)!!.second, 0.0)
        assertEquals(TestResult.POSITIVE, store.mostRecentResult(DengueTest.NS1)!!.second)
        assertNull(store.mostRecentResult(DengueTest.IGM))
    }

    @Test
    fun `an empty report is not saved and results survive a reload`() {
        val path = file("labs.json")
        val store = LabStore(path)
        store.add(LabReport())
        assertTrue(store.reports.isEmpty())

        store.add(LabReport(dateMillis = now, igm = TestResult.NEGATIVE, whiteCells = 3.2))
        val reloaded = LabStore(path).reports.single()
        assertEquals(TestResult.NEGATIVE, reloaded.igm)
        assertEquals(TestResult.NOT_DONE, reloaded.ns1)
        assertEquals(3.2, reloaded.whiteCells!!, 0.0)
    }

    // MARK: - Fluids

    @Test
    fun `a drink needs a plausible amount and urine needs none`() {
        val store = FluidStore(file("fluids.json"))
        store.add(FluidEntry(dateMillis = now, kind = FluidEntry.Kind.DRINK))
        store.add(FluidEntry(dateMillis = now, kind = FluidEntry.Kind.DRINK, millilitres = 20_000))
        store.add(FluidEntry(dateMillis = now, kind = FluidEntry.Kind.DRINK, millilitres = 250))
        store.add(FluidEntry(dateMillis = now, kind = FluidEntry.Kind.URINE))
        assertEquals(2, store.entries.size)
        assertEquals(250, store.day(now).drunkMillilitres)
        assertEquals(1, store.day(now).urinations)
    }

    // MARK: - Symptom checks

    @Test
    fun `the latest check is the newest, and what it was given survives a reload`() {
        val path = file("case-log.json")
        val store = CaseLogStore(path)
        store.add(CaseLogEntry(dateMillis = now, symptomIds = listOf("fever", "abdominal"), feverDaysAgo = 4, isPregnant = true))
        store.add(CaseLogEntry(dateMillis = now - 48 * hour, symptomIds = listOf("headache")))

        val latest = CaseLogStore(path).entries.first()
        assertEquals(listOf("fever", "abdominal"), latest.symptomIds)
        assertEquals(4, latest.feverDaysAgo)
        assertTrue(latest.isPregnant)
    }

    @Test
    fun `a check written before the WHO fields existed still loads`() {
        val path = file("case-log.json")
        path.writeText("""[{"id":"a","date":$now,"symptomIds":["fever"],"outcome":1,"note":""}]""")
        val entry = CaseLogStore(path).entries.single()
        assertNull("not asked is not zero days", entry.feverDaysAgo)
        assertEquals(false, entry.isPregnant)
    }
}
