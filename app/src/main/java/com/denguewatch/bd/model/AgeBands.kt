package com.denguewatch.bd.model

/**
 * Making DGHS's age table safe to draw.
 *
 * The published table is typed by hand and shows it. A single season's table
 * carried, alongside the seventeen real bands:
 *
 *   * `6-10` with one case, beside `06-10` with 4,556 — the same band written
 *     two ways, so the reader saw it twice;
 *   * `0-10` and `21-30`, one case each — coarser bands laid over `0-5`/`06-10`
 *     and `21-25`/`26-30`, counting the same ages again;
 *   * `42309` and `46301`, dates that Excel turned into serial numbers;
 *   * a blank label, and a `Grand Total` row.
 *
 * Drawn as published they became three extra bars in a pyramid that is supposed
 * to show one bar per age group, with `6-10` sorted between `56-60` and `61-65`
 * because the labels were being compared as text rather than as numbers. A
 * reader counting groups off the chart would have got the wrong answer.
 *
 * Nothing here invents or redistributes cases. Rows that name the same band are
 * added together; a row that overlaps a finer band is dropped, because the finer
 * grid is the one the rest of the table is on. Pure, so the real table can be
 * tested against it.
 */
object AgeBands {

    /**
     * The scale both halves of the age pyramid share: the longest single side
     * any band draws. The right side carries female cases and, beyond them,
     * the cases with no sex recorded, so that is its length.
     *
     * One scale for both halves is what makes the chart symmetrical — the
     * longest bar on either side reaches its edge and a bar of the same count
     * is the same length left or right. Scaled to the band totals instead, as
     * it was, no side ever came near its edge and the whole pyramid huddled in
     * the middle third of the card.
     */
    fun sidePeak(bands: List<AgeBand>): Int =
        maxOf(bands.maxOfOrNull { maxOf(it.male, it.female + it.unrecordedSex) } ?: 1, 1)

    /**
     * The years a label covers, or null when it is not an age band at all.
     *
     * The open-ended top band is closed at 120 so it can be compared with the
     * others; no one in the table is older than that.
     */
    fun span(label: String): IntRange? {
        val trimmed = label.trim()
        if (trimmed == "80+") return 80..120

        val parts = trimmed.split("-")
        if (parts.size != 2) return null
        // "06" parses to 6, which is how the zero-padded labels line up with
        // their unpadded twins.
        val low = parts[0].trim().toIntOrNull() ?: return null
        val high = parts[1].trim().toIntOrNull() ?: return null
        if (low > high || high > 120 || low < 0) return null
        return low..high
    }

    /**
     * The band written out in the reader's own digits.
     *
     * The feed publishes Western numerals and nothing else, so a Bengali reader
     * saw `11-15` and `80+` sitting beside `৬.১ হাজার` — the only Latin figures
     * on the screen. Built from the parsed bounds rather than the published
     * string, which also drops DGHS's stray zero padding (`06-10`) and uses a
     * proper en dash, matching what iOS already draws.
     *
     * A label that is not an age band is handed back untouched: this formats
     * what the feed said, and never invents a range it could not read.
     */
    fun displayLabel(label: String, num: (Int) -> String): String {
        val trimmed = label.trim()
        if (trimmed.endsWith("+")) {
            val low = trimmed.dropLast(1).trim().toIntOrNull() ?: return label
            return "${num(low)}+"
        }
        val parts = trimmed.split("-")
        if (parts.size != 2) return label
        val low = parts[0].trim().toIntOrNull() ?: return label
        val high = parts[1].trim().toIntOrNull() ?: return label
        if (low > high || high > 120 || low < 0) return label
        return "${num(low)}\u2013${num(high)}"
    }

    /**
     * Whether two bands cover the same ages rather than merely touching.
     *
     * DGHS's own grid has `76-80` and `80+` sharing the year 80, so a single
     * shared year is how the table is meant to read. More than that means one
     * band is counting ages another has already counted.
     */
    private fun overlaps(a: IntRange, b: IntRange): Boolean =
        minOf(a.last, b.last) - maxOf(a.first, b.first) >= 1

    /**
     * The bands worth drawing: duplicates merged, overlaps removed, in age
     * order.
     */
    fun normalise(rows: List<AgeBand>): List<AgeBand> {
        val parsed = rows.mapNotNull { band -> span(band.label)?.let { it to band } }

        // Rows naming the same span are the same band written twice, so their
        // counts are added rather than one being thrown away. The label kept is
        // the one from the larger row — the spelling the table mostly uses.
        val merged = parsed.groupBy({ it.first }, { it.second }).map { (span, group) ->
            span to AgeBand(
                label = group.maxBy { it.total }.label,
                male = group.sumOf { it.male },
                female = group.sumOf { it.female },
                // Published totals add the same way the sexes do, so a band
                // written twice keeps every case either row counted.
                total = group.sumOf { it.total },
            )
        }

        // Narrowest first, so the five-year grid is laid down before anything
        // coarser can claim the same years. Ties break on the lower bound to
        // keep the result the same whatever order the feed arrived in.
        val kept = mutableListOf<Pair<IntRange, AgeBand>>()
        merged.sortedWith(
            compareBy({ it.first.last - it.first.first }, { it.first.first }),
        ).forEach { candidate ->
            if (kept.none { overlaps(it.first, candidate.first) }) kept += candidate
        }

        return kept.sortedBy { it.first.first }.map { it.second }
    }
}
