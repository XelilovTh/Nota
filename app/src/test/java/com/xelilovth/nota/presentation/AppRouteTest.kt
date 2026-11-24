package com.xelilovth.nota.presentation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
class AppRouteTest {
    @Test
    fun bottomTabRoutesAreStableAndUnique() {
        assertEquals(listOf("home", "library", "search", "settings"), AppRoute.bottomTabs)
        assertEquals(AppRoute.bottomTabs.size, AppRoute.bottomTabs.toSet().size)
        assertTrue(AppRoute.bottomTabs.contains(AppRoute.HOME))
    }
}
