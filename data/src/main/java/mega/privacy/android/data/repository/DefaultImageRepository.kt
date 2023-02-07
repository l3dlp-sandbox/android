package mega.privacy.android.data.repository

import android.content.Context
import android.graphics.Bitmap
import android.media.ThumbnailUtils
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.core.net.toFile
import androidx.core.net.toUri
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import mega.privacy.android.data.constant.CacheFolderConstant
import mega.privacy.android.data.constant.FileConstant
import mega.privacy.android.data.extensions.failWithError
import mega.privacy.android.data.extensions.failWithException
import mega.privacy.android.data.extensions.getPreviewFileName
import mega.privacy.android.data.extensions.getScreenSize
import mega.privacy.android.data.extensions.getThumbnailFileName
import mega.privacy.android.data.extensions.isVideo
import mega.privacy.android.data.extensions.toException
import mega.privacy.android.data.gateway.CacheGateway
import mega.privacy.android.data.gateway.FileGateway
import mega.privacy.android.data.gateway.api.MegaApiGateway
import mega.privacy.android.data.gateway.preferences.FileManagementPreferencesGateway
import mega.privacy.android.data.listener.OptionalMegaRequestListenerInterface
import mega.privacy.android.data.listener.OptionalMegaTransferListenerInterface
import mega.privacy.android.data.model.FullImageDownloadResult
import mega.privacy.android.data.model.MimeTypeList
import mega.privacy.android.domain.entity.imageviewer.ImageResult
import mega.privacy.android.domain.exception.MegaException
import mega.privacy.android.domain.exception.QuotaExceededMegaException
import mega.privacy.android.domain.exception.ResourceAlreadyExistsMegaException
import mega.privacy.android.domain.qualifier.IoDispatcher
import mega.privacy.android.domain.repository.ImageRepository
import nz.mega.sdk.MegaError
import nz.mega.sdk.MegaNode
import nz.mega.sdk.MegaTransfer
import timber.log.Timber
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import kotlin.coroutines.resumeWithException

/**
 * The repository implementation class regarding thumbnail feature.
 *
 * @param context Context
 * @param megaApiGateway MegaApiGateway
 * @param ioDispatcher CoroutineDispatcher
 * @param cacheGateway CacheGateway
 * @param fileManagementPreferencesGateway FileManagementPreferencesGateway
 * @param fileGateway FileGateway
 */
internal class DefaultImageRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val megaApiGateway: MegaApiGateway,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    private val cacheGateway: CacheGateway,
    private val fileManagementPreferencesGateway: FileManagementPreferencesGateway,
    private val fileGateway: FileGateway,
) : ImageRepository {

    private var thumbnailFolderPath: String? = null

    private var previewFolderPath: String? = null

    init {
        runBlocking(ioDispatcher) {
            thumbnailFolderPath =
                cacheGateway.getOrCreateCacheFolder(CacheFolderConstant.THUMBNAIL_FOLDER)?.path
            previewFolderPath =
                cacheGateway.getOrCreateCacheFolder(CacheFolderConstant.PREVIEW_FOLDER)?.path
        }
    }

    override suspend fun getThumbnailFromLocal(handle: Long): File? =
        withContext(ioDispatcher) {
            megaApiGateway.getMegaNodeByHandle(handle)?.run {
                getThumbnailFile(this).takeIf {
                    it?.exists() ?: false
                }
            }
        }

    private suspend fun getThumbnailFile(node: MegaNode): File? =
        cacheGateway.getCacheFile(
            CacheFolderConstant.THUMBNAIL_FOLDER,
            "${node.base64Handle}${FileConstant.JPG_EXTENSION}"
        )

    override suspend fun getThumbnailFromServer(handle: Long): File? =
        withContext(ioDispatcher) {
            megaApiGateway.getMegaNodeByHandle(handle)?.let { node ->
                getThumbnailFile(node)?.let { thumbnail ->
                    suspendCancellableCoroutine { continuation ->
                        megaApiGateway.getThumbnail(node, thumbnail.absolutePath,
                            OptionalMegaRequestListenerInterface(
                                onRequestFinish = { _, error ->
                                    if (error.errorCode == MegaError.API_OK) {
                                        continuation.resumeWith(Result.success(thumbnail))
                                    } else {
                                        continuation.failWithError(error)
                                    }
                                }
                            )
                        )
                    }
                }
            }
        }

    private suspend fun getPreviewFile(node: MegaNode): File? =
        cacheGateway.getCacheFile(
            CacheFolderConstant.PREVIEW_FOLDER,
            "${node.base64Handle}${FileConstant.JPG_EXTENSION}"
        )

    private suspend fun getFullFile(node: MegaNode): File? =
        cacheGateway.getCacheFile(
            CacheFolderConstant.TEMPORARY_FOLDER,
            "${node.base64Handle}.${MimeTypeList.typeForName(node.name).extension}"
        )

    override suspend fun getPreviewFromLocal(handle: Long): File? =
        withContext(ioDispatcher) {
            megaApiGateway.getMegaNodeByHandle(handle)?.run {
                getPreviewFile(this).takeIf {
                    it?.exists() ?: false
                }
            }
        }

    override suspend fun getPreviewFromServer(handle: Long): File? =
        withContext(ioDispatcher) {
            megaApiGateway.getMegaNodeByHandle(handle)?.let { node ->
                getPreviewFile(node)?.let { preview ->
                    suspendCancellableCoroutine { continuation ->
                        megaApiGateway.getPreview(node, preview.absolutePath,
                            OptionalMegaRequestListenerInterface(
                                onRequestFinish = { _, error ->
                                    if (error.errorCode == MegaError.API_OK) {
                                        continuation.resumeWith(Result.success(preview))
                                    } else {
                                        continuation.failWithError(error)
                                    }
                                }
                            )
                        )
                    }
                }
            }
        }

    override suspend fun downloadThumbnail(
        handle: Long,
        callback: (success: Boolean) -> Unit,
    ) = withContext(ioDispatcher) {
        val node = megaApiGateway.getMegaNodeByHandle(handle)
        if (node == null || thumbnailFolderPath == null || !node.hasThumbnail()) {
            callback(false)
        } else {
            megaApiGateway.getThumbnail(
                node,
                getThumbnailPath(thumbnailFolderPath ?: return@withContext, node),
                OptionalMegaRequestListenerInterface(
                    onRequestFinish = { _, error ->
                        callback(error.errorCode == MegaError.API_OK)
                    }
                )
            )
        }
    }

    override suspend fun downloadPreview(
        handle: Long,
        callback: (success: Boolean) -> Unit,
    ) = withContext(ioDispatcher) {
        val node = megaApiGateway.getMegaNodeByHandle(handle)
        if (node == null || previewFolderPath == null || !node.hasPreview()) {
            callback(false)
        } else {
            megaApiGateway.getPreview(
                node,
                getPreviewPath(previewFolderPath ?: return@withContext, node),
                OptionalMegaRequestListenerInterface(
                    onRequestFinish = { _, error ->
                        callback(error.errorCode == MegaError.API_OK)
                    }
                )
            )
        }
    }

    private fun getPreviewPath(previewFolderPath: String, megaNode: MegaNode) =
        "$previewFolderPath${File.separator}${megaNode.getPreviewFileName()}"

    private fun getThumbnailPath(thumbnailFolderPath: String, megaNode: MegaNode) =
        "$thumbnailFolderPath${File.separator}${megaNode.getThumbnailFileName()}"


    override suspend fun getImageByNodeHandle(
        nodeHandle: Long,
        fullSize: Boolean,
        highPriority: Boolean,
        isMeteredConnection: Boolean,
    ): Flow<ImageResult> = withContext(ioDispatcher) {
        megaApiGateway.getMegaNodeByHandle(nodeHandle)?.let {
            if (!it.isFile) throw IllegalArgumentException("Node is not a file")
            return@let getImageByNode(it, fullSize, highPriority, isMeteredConnection)
        } ?: throw IllegalArgumentException("Node is null")
    }

    override suspend fun getImageByNodePublicLink(
        nodeFileLink: String,
        fullSize: Boolean,
        highPriority: Boolean,
        isMeteredConnection: Boolean,
    ): Flow<ImageResult> = withContext(ioDispatcher) {
        if (nodeFileLink.isBlank()) throw IllegalArgumentException("Invalid megaFileLink")
        return@withContext getImageByNode(
            getPublicNode(nodeFileLink),
            fullSize,
            highPriority,
            isMeteredConnection
        )
    }

    private suspend fun getPublicNode(nodeFileLink: String): MegaNode =
        suspendCancellableCoroutine { continuation ->
            val listener = OptionalMegaRequestListenerInterface(
                onRequestFinish = { request, error ->
                    if (error.errorCode == MegaError.API_OK) {
                        if (!request.flag) {
                            continuation.resumeWith(Result.success(request.publicNode))
                        } else {
                            continuation.resumeWithException(IllegalArgumentException("Invalid key for public node"))
                        }
                    } else {
                        continuation.failWithException(error.toException())
                    }
                }
            )
            megaApiGateway.getPublicNode(nodeFileLink, listener)
            continuation.invokeOnCancellation {
                megaApiGateway.removeRequestListener(listener)
            }
        }

    private suspend fun getFullImageFromServer(
        imageResult: ImageResult,
        node: MegaNode,
        fullFile: File,
        highPriority: Boolean,
        isValidNodeFile: Boolean,
    ): Flow<FullImageDownloadResult> = callbackFlow {
        val listener = OptionalMegaTransferListenerInterface(
            onTransferStart = { transfer ->
                imageResult.transferTag = transfer.tag
                imageResult.totalBytes = transfer.totalBytes
                trySend(FullImageDownloadResult(imageResult))
            },
            onTransferFinish = { _: MegaTransfer, error: MegaError ->
                imageResult.transferTag = null
                when (error.errorCode) {
                    MegaError.API_OK -> {
                        imageResult.fullSizeUri =
                            fullFile.toUri().toString()
                        imageResult.isFullyLoaded = true
                        trySend(FullImageDownloadResult(imageResult))
                    }
                    MegaError.API_EEXIST -> {
                        if (isValidNodeFile) {
                            imageResult.fullSizeUri =
                                fullFile.toUri().toString()
                            imageResult.isFullyLoaded = true
                            trySend(FullImageDownloadResult(imageResult))
                        } else {
                            trySend(
                                FullImageDownloadResult(
                                    deleteFile = fullFile,
                                    exception = ResourceAlreadyExistsMegaException(
                                        error.errorCode,
                                        error.errorString
                                    )
                                )
                            )
                        }
                    }
                    MegaError.API_ENOENT -> {
                        imageResult.isFullyLoaded = true
                        trySend(FullImageDownloadResult(imageResult = imageResult))
                    }
                    else -> {
                        trySend(
                            FullImageDownloadResult(
                                exception = MegaException(
                                    error.errorCode,
                                    error.errorString
                                )
                            )
                        )
                    }
                }
            },
            onTransferTemporaryError = { _, error ->
                if (error.errorCode == MegaError.API_EOVERQUOTA) {
                    imageResult.isFullyLoaded = true
                    trySend(
                        FullImageDownloadResult(
                            imageResult = imageResult,
                            exception = QuotaExceededMegaException(
                                error.errorCode,
                                error.errorString
                            )
                        )
                    )
                }
            },
            onTransferUpdate = {
                imageResult.transferredBytes = it.transferredBytes
                trySend(FullImageDownloadResult(imageResult = imageResult))
            }
        )

        megaApiGateway.getFullImage(
            node,
            fullFile,
            highPriority, listener
        )

        awaitClose {
            megaApiGateway.removeTransferListener(listener)
        }
    }

    private suspend fun getImageByNode(
        node: MegaNode,
        fullSize: Boolean,
        highPriority: Boolean,
        isMeteredConnection: Boolean,
    ): Flow<ImageResult> = flow {
        val fullSizeRequired =
            isFullSizeRequired(
                node,
                fullSize,
                fileManagementPreferencesGateway.isMobileDataAllowed(),
                isMeteredConnection
            )

        val thumbnailFile = if (node.hasThumbnail()) getThumbnailFile(node) else null

        val previewFile =
            if (node.hasPreview() || node.isVideo()) getPreviewFile(node) else null

        getFullFile(node)?.let { fullFile ->
            val isValidNodeFile = megaApiGateway.checkValidNodeFile(node, fullFile)
            if (!isValidNodeFile) {
                fileGateway.deleteFile(fullFile)
            }

            val imageResult = ImageResult(
                isVideo = node.isVideo(),
                thumbnailUri = thumbnailFile?.takeIf { it.exists() }?.toUri().toString(),
                previewUri = previewFile?.takeIf { it.exists() }?.toUri().toString(),
                fullSizeUri = fullFile.takeIf { it.exists() }?.toUri().toString(),
            )

            if (imageResult.isVideo && imageResult.fullSizeUri != null && previewFile == null) {
                imageResult.previewUri = getVideoThumbnail(
                    node.getThumbnailFileName(),
                    fullFile.toUri()
                )
            }

            if ((!fullSizeRequired && !imageResult.previewUri.isNullOrBlank()) || isValidNodeFile) {
                imageResult.isFullyLoaded = true
                emit(imageResult)
            } else {
                emit(imageResult)
                if (imageResult.thumbnailUri == null) {
                    runCatching {
                        getThumbnailFromServer(node.handle)
                    }.onSuccess {
                        imageResult.thumbnailUri = it?.toUri().toString()
                        emit(imageResult)
                    }.onFailure {
                        Timber.w(it)
                    }
                }

                if (imageResult.previewUri == null) {
                    runCatching {
                        getPreviewFromServer(node.handle)
                    }.onSuccess {
                        imageResult.previewUri = it?.toUri().toString()
                        if (!fullSizeRequired) {
                            imageResult.isFullyLoaded = true
                            emit(imageResult)
                        } else {
                            emit(imageResult)
                            if (imageResult.fullSizeUri == null) {
                                getFullImageFromServer(
                                    imageResult,
                                    node,
                                    fullFile,
                                    highPriority,
                                    isValidNodeFile
                                ).collect { result ->
                                    result.imageResult?.let { downloadImageResult ->
                                        emit(downloadImageResult)
                                    }
                                    result.deleteFile?.let { file ->
                                        fileGateway.deleteFile(file)
                                    }
                                    result.exception?.let { exception ->
                                        throw exception
                                    }
                                }
                            }
                        }
                    }.onFailure { exception ->
                        if (!fullSizeRequired) {
                            throw exception
                        } else {
                            Timber.w(exception)
                        }
                    }
                }
            }
        } ?: throw IllegalArgumentException("Full image file is null")
    }


    private fun isFullSizeRequired(
        node: MegaNode,
        fullSize: Boolean,
        isMobileDataAllowed: Boolean,
        isMeteredConnection: Boolean,
    ) = when {
        node.isTakenDown || node.isVideo() -> false
        node.size <= SIZE_1_MB -> true
        node.size in SIZE_1_MB..SIZE_50_MB -> fullSize || isMobileDataAllowed || !isMeteredConnection
        else -> false
    }

    private suspend fun getPreviewFile(fileName: String): File? =
        cacheGateway.getCacheFile(CacheFolderConstant.PREVIEW_FOLDER, fileName)

    @Suppress("deprecation")
    suspend fun getVideoThumbnail(fileName: String, videoUri: Uri): String? =
        withContext(ioDispatcher) {
            val videoFile = videoUri.toFile().takeIf { it.exists() }
            return@withContext videoFile?.let { getPreviewFile(fileName) }?.let { previewFile ->
                if (previewFile.exists()) previewFile.toUri().toString()

                val bitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    ThumbnailUtils.createVideoThumbnail(
                        videoFile,
                        context.getScreenSize(),
                        null
                    )
                } else {
                    ThumbnailUtils.createVideoThumbnail(
                        videoFile.path,
                        MediaStore.Images.Thumbnails.FULL_SCREEN_KIND
                    )
                }

                bitmap?.let {
                    BufferedOutputStream(FileOutputStream(previewFile)).apply {
                        this.use {
                            bitmap.compress(
                                Bitmap.CompressFormat.JPEG,
                                BITMAP_COMPRESS_QUALITY,
                                it,
                            )
                        }
                    }
                    bitmap.recycle()
                    previewFile.toUri().toString()
                }
            }
        }

    companion object {
        private const val SIZE_1_MB = 1024 * 1024 * 1L
        private const val SIZE_50_MB = SIZE_1_MB * 50L
        private const val BITMAP_COMPRESS_QUALITY = 75
    }

}