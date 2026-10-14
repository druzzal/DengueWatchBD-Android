package com.denguewatch.bd

import com.denguewatch.bd.ui.theme.TypeScale
import org.junit.Assert.assertEquals
import org.junit.Test

/** Text sized to the screen: iOS sizes at an iPhone's width, capped both ways. */
class TypeScaleTest {

    @Test
    fun `an iPhone-width screen gets the iOS sizes exactly`() {
        assertEquals(1f, TypeScale.forSmallestWidth(390), 0.0001f)
    }

    @Test
    fun `a narrower phone scales down in proportion, but not below the floor`() {
        assertEquals(384f / 390f, TypeScale.forSmallestWidth(384), 0.0001f)
        assertEquals(360f / 390f, TypeScale.forSmallestWidth(360), 0.0001f)
        assertEquals(TypeScale.MIN, TypeScale.forSmallestWidth(280), 0.0001f)
    }

    @Test
    fun `a tablet scales up gently, to a cap`() {
        val tablet = TypeScale.forSmallestWidth(600)
        assert(tablet > 1f && tablet < TypeScale.MAX)
        assertEquals(TypeScale.MAX, TypeScale.forSmallestWidth(840), 0.0001f)
        assertEquals(TypeScale.MAX, TypeScale.forSmallestWidth(1200), 0.0001f)
    }

    @Test
    fun `an unknown width leaves the sizes alone`() {
        assertEquals(1f, TypeScale.forSmallestWidth(0), 0.0001f)
    }
}
