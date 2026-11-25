package com.xelilovth.nota.data.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import com.xelilovth.nota.domain.model.PlaylistCollectionType
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

        val album = reloaded.createCollection("Acoustic", PlaylistCollectionType.ALBUM, listOf(10L, 20L))
        reloaded.hideAutoGroup("album:legacy")
        val afterRestart = CustomPlaylistRepositoryImpl(context)
        assertEquals(PlaylistCollectionType.ALBUM, afterRestart.playlists.value.first { it.id == album.id }.type)
        assertEquals(listOf(10L, 20L), afterRestart.playlists.value.first { it.id == album.id }.trackIds)
        assertTrue("album:legacy" in afterRestart.hiddenAutoGroupIds.value)
    }
}
