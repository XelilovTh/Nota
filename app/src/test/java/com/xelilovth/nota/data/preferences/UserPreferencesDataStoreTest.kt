package com.xelilovth.nota.data.preferences

import com.xelilovth.nota.domain.model.RepeatMode
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class UserPreferencesDataStoreTest {
    private lateinit var dataStore: UserPreferencesDataStore

    @Before
    fun setUp() = runTest {
        val context = RuntimeEnvironment.getApplication()
        dataStore = UserPreferencesDataStore(context)
        dataStore.clear()
    }

    @Test
    fun defaultPreferencesAreReturnedWhenEmpty() = runTest {
        val preferences = dataStore.userPreferencesFlow.first()
        assertNull(preferences.lastTrackId)
        assertEquals(0L, preferences.positionMs)
        assertFalse(preferences.shuffle)
        assertEquals(RepeatMode.OFF, preferences.repeatMode)
    }

    @Test
    fun updateIndividualPreferences() = runTest {
        dataStore.setLastTrackId(123L)
        assertEquals(123L, dataStore.lastTrackIdFlow.first())

        dataStore.setPositionMs(45000L)
        assertEquals(45000L, dataStore.positionMsFlow.first())

        dataStore.setShuffle(true)
        assertTrue(dataStore.shuffleFlow.first())

        dataStore.setRepeatMode(RepeatMode.ALL)
        assertEquals(RepeatMode.ALL, dataStore.repeatModeFlow.first())
    }

    @Test
    fun savePlaybackStateSavesAllFieldsAtomically() = runTest {
        dataStore.savePlaybackState(
            trackId = 999L,
            positionMs = 12000L,
            shuffle = true,
            repeatMode = RepeatMode.ONE,
        )

        val preferences = dataStore.userPreferencesFlow.first()
        assertEquals(999L, preferences.lastTrackId)
        assertEquals(12000L, preferences.positionMs)
        assertTrue(preferences.shuffle)
        assertEquals(RepeatMode.ONE, preferences.repeatMode)

        // Clear trackId
        dataStore.setLastTrackId(null)
        assertNull(dataStore.lastTrackIdFlow.first())
    }
}
