package com.xelilovth.nota.data.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class CustomPlaylistRepositoryImplTest {
    @Test
    fun createRenameAddRemoveAndDeletePersistAcrossInstances() {
        val context = RuntimeEnvironment.getApplication()
        val repo = CustomPlaylistRepositoryImpl(context)

        val created = repo.createPlaylist("Road Trip")
        assertEquals("Road Trip", created.name)
        assertTrue(created.trackIds.isEmpty())

        repo.addTrack(created.id, 10L)
        repo.addTrack(created.id, 20L)
        repo.addTrack(created.id, 10L)
        assertEquals(listOf(10L, 20L), repo.playlists.value.first { it.id == created.id }.trackIds)

        repo.renamePlaylist(created.id, "Night Drive")
        repo.removeTrack(created.id, 10L)

        val reloaded = CustomPlaylistRepositoryImpl(context)
        val persisted = reloaded.playlists.value.first { it.id == created.id }
        assertEquals("Night Drive", persisted.name)
        assertEquals(listOf(20L), persisted.trackIds)

        reloaded.deletePlaylist(created.id)
        assertTrue(reloaded.playlists.value.none { it.id == created.id })
    }
}
