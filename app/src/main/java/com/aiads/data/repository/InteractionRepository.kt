package com.aiads.data.repository

import com.aiads.data.local.DatabaseHelper
import com.aiads.data.local.DatabaseHelper.InteractionState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class InteractionRepository(private val database: DatabaseHelper) {

    suspend fun getInteraction(adId: String): InteractionState? =
        withContext(Dispatchers.IO) { database.getInteraction(adId) }

    suspend fun getInteractionMap(adIds: Set<String>): Map<String, InteractionState> =
        withContext(Dispatchers.IO) {
            adIds.mapNotNull { id ->
                database.getInteraction(id)?.let { id to it }
            }.toMap()
        }

    suspend fun toggleLike(adId: String): InteractionState =
        withContext(Dispatchers.IO) {
            val current = database.getInteraction(adId)
            val newLiked = !(current?.isLiked ?: false)
            database.upsertInteraction(
                adId = adId,
                isLiked = newLiked,
                isBookmarked = current?.isBookmarked ?: false,
                isShared = current?.isShared ?: false
            )
            InteractionState(
                isLiked = newLiked,
                isBookmarked = current?.isBookmarked ?: false,
                isShared = current?.isShared ?: false
            )
        }

    suspend fun toggleBookmark(adId: String): InteractionState =
        withContext(Dispatchers.IO) {
            val current = database.getInteraction(adId)
            val newBookmarked = !(current?.isBookmarked ?: false)
            database.upsertInteraction(
                adId = adId,
                isLiked = current?.isLiked ?: false,
                isBookmarked = newBookmarked,
                isShared = current?.isShared ?: false
            )
            InteractionState(
                isLiked = current?.isLiked ?: false,
                isBookmarked = newBookmarked,
                isShared = current?.isShared ?: false
            )
        }

    suspend fun toggleShare(adId: String): InteractionState =
        withContext(Dispatchers.IO) {
            val current = database.getInteraction(adId)
            val newShared = !(current?.isShared ?: false)
            database.upsertInteraction(
                adId = adId,
                isLiked = current?.isLiked ?: false,
                isBookmarked = current?.isBookmarked ?: false,
                isShared = newShared
            )
            InteractionState(
                isLiked = current?.isLiked ?: false,
                isBookmarked = current?.isBookmarked ?: false,
                isShared = newShared
            )
        }
}