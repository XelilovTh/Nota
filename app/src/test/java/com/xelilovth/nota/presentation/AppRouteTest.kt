package com.xelilovth.nota.presentation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AppRouteTest {
    @Test
    fun bottomTabsAreInRequestedOrderAndUnique() {
        assertEquals(listOf("home", "playlists", "search", "library"), AppRoute.bottomTabs)
        assertEquals(AppRoute.bottomTabs.size, AppRoute.bottomTabs.toSet().size)
    }

    @Test
    fun homeActionsResolveToSearchAndSettings() {
        assertEquals(AppRoute.SEARCH, AppRoute.homeActionRoute("search"))
        assertEquals(AppRoute.SETTINGS, AppRoute.homeActionRoute("settings"))
        assertNull(AppRoute.homeActionRoute("unknown"))
    }
}
