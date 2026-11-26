package com.xelilovth.nota.data.online

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class OnlineMusicRepositoryImplTest {

    @Test
    fun extractVideoIdFromUrlSupportsStandardShortAndEmbedUrls() {
        assertEquals("dQw4w9WgXcQ", OnlineMusicRepositoryImpl.extractVideoIdFromUrl("https://www.youtube.com/watch?v=dQw4w9WgXcQ"))
        assertEquals("dQw4w9WgXcQ", OnlineMusicRepositoryImpl.extractVideoIdFromUrl("https://youtu.be/dQw4w9WgXcQ"))
        assertEquals("dQw4w9WgXcQ", OnlineMusicRepositoryImpl.extractVideoIdFromUrl("https://www.youtube.com/shorts/dQw4w9WgXcQ"))
        assertNull(OnlineMusicRepositoryImpl.extractVideoIdFromUrl("https://example.com/other"))
    }

    @Test
    fun extractOnlineIdParsesNotaOnlinePrefix() {
        assertEquals("abc123", OnlineMusicRepositoryImpl.extractOnlineId("${OnlineMusicRepositoryImpl.ONLINE_URI_PREFIX}abc123"))
        assertNull(OnlineMusicRepositoryImpl.extractOnlineId("content://media/external/audio/media/42"))
    }

    @Test
    fun stableOnlineTrackIdIsAlwaysNegativeAndDeterministic() {
        val id1 = OnlineMusicRepositoryImpl.stableOnlineTrackId("dQw4w9WgXcQ")
        val id2 = OnlineMusicRepositoryImpl.stableOnlineTrackId("dQw4w9WgXcQ")
        val id3 = OnlineMusicRepositoryImpl.stableOnlineTrackId("otherVideo1")
        assertEquals(id1, id2)
        assertNotEquals(id1, id3)
        assertTrue(id1 < 0L)
        assertTrue(id3 < 0L)
    }
}
