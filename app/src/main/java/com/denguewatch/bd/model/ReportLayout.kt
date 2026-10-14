package com.denguewatch.bd.model

/**
 * Rebuilds the rows of a lab report from where its text sits on the page.
 *
 * This exists because of a difference between the platforms. Apple's Vision
 * returns a report's text roughly in reading order, so the iOS build can join
 * its observations and get "Platelet Count  96,000 /cumm" on one line. ML Kit
 * groups text into blocks instead, and on a two-column report that means every
 * label first and every value afterwards:
 *
 *     Haemoglobin / Haematocrit / Total WBC Count / Platelet Count
 *     14.1 g/dL / 47.5 % / 3,600 /cumm / 96,000 /cumm
 *
 * [LabReportParser] insists a value shares a line with its label — which is
 * what stops it pairing a platelet count with somebody's age — so that input
 * yields nothing at all. Grouping by vertical position puts the rows back
 * before the parser ever sees them.
 *
 * Pure, and deliberately free of any ML Kit or Android type, so the grouping
 * can be tested without a camera.
 */
object ReportLayout {

    /** One recognised line and the box it occupied, in image pixels. */
    data class Fragment(
        val text: String,
        val top: Int,
        val bottom: Int,
        val left: Int,
    ) {
        val centreY: Int get() = (top + bottom) / 2
        val height: Int get() = (bottom - top).coerceAtLeast(1)
    }

    /**
     * Fragments joined back into visual rows, top to bottom.
     *
     * Two fragments belong to the same row when their vertical centres are
     * closer together than half a line's height. That tolerance is relative to
     * the text itself rather than a fixed number of pixels, so it holds for a
     * photograph taken close up and one taken across a desk.
     */
    fun rows(fragments: List<Fragment>): String {
        if (fragments.isEmpty()) return ""

        val typicalHeight = fragments.map { it.height }.sorted()[fragments.size / 2]
        val tolerance = (typicalHeight / 2).coerceAtLeast(1)

        val rows = mutableListOf<MutableList<Fragment>>()
        fragments.sortedBy { it.centreY }.forEach { fragment ->
            val row = rows.lastOrNull()
            if (row != null && kotlin.math.abs(fragment.centreY - row.first().centreY) <= tolerance) {
                row.add(fragment)
            } else {
                rows.add(mutableListOf(fragment))
            }
        }

        return rows.joinToString("\n") { row ->
            row.sortedBy { it.left }.joinToString("  ") { it.text.trim() }
        }
    }
}
