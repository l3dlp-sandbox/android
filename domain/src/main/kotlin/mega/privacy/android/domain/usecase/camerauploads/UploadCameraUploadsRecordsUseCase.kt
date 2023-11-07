package mega.privacy.android.domain.usecase.camerauploads

import kotlinx.coroutines.channels.Channel.Factory.UNLIMITED
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.cancellable
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.retry
import kotlinx.coroutines.flow.singleOrNull
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import mega.privacy.android.domain.entity.SyncRecordType
import mega.privacy.android.domain.entity.VideoCompressionState
import mega.privacy.android.domain.entity.VideoQuality
import mega.privacy.android.domain.entity.camerauploads.CameraUploadFolderType
import mega.privacy.android.domain.entity.camerauploads.CameraUploadsRecord
import mega.privacy.android.domain.entity.camerauploads.CameraUploadsRecordUploadStatus
import mega.privacy.android.domain.entity.camerauploads.CameraUploadsTransferProgress
import mega.privacy.android.domain.entity.node.NodeId
import mega.privacy.android.domain.entity.transfer.TransferEvent
import mega.privacy.android.domain.entity.transfer.TransferType
import mega.privacy.android.domain.exception.NotEnoughStorageException
import mega.privacy.android.domain.repository.FileSystemRepository
import mega.privacy.android.domain.usecase.CreateTempFileAndRemoveCoordinatesUseCase
import mega.privacy.android.domain.usecase.file.GetFingerprintUseCase
import mega.privacy.android.domain.usecase.file.GetGPSCoordinatesUseCase
import mega.privacy.android.domain.usecase.node.CopyNodeUseCase
import mega.privacy.android.domain.usecase.thumbnailpreview.CreateImageOrVideoPreviewUseCase
import mega.privacy.android.domain.usecase.thumbnailpreview.CreateImageOrVideoThumbnailUseCase
import mega.privacy.android.domain.usecase.thumbnailpreview.DeletePreviewUseCase
import mega.privacy.android.domain.usecase.thumbnailpreview.DeleteThumbnailUseCase
import mega.privacy.android.domain.usecase.transfers.completed.AddCompletedTransferUseCase
import mega.privacy.android.domain.usecase.transfers.uploads.StartUploadUseCase
import mega.privacy.android.domain.usecase.video.CompressVideoUseCase
import java.io.File
import java.io.FileNotFoundException
import java.util.concurrent.TimeUnit
import javax.inject.Inject

/**
 * Camera Uploads upload process
 *
 * This use case is responsible to upload the list of [CameraUploadsRecord].
 * For each record, it will check if:
 * - the node does not exists in the cloud : upload
 * - the node exists in another folder except rubbish bin : copy
 * - the node exists in the upload folder : do nothing
 *
 * It will also generate the temporary file to upload in case:
 * - the user set the option to remove gps coordinates
 * - the user set the option to compress the video
 *
 * The use case is also responsible of setting the upload status in the database.
 * It will return a flow of events representing the status progress for an individual record.
 * The caller is responsible to aggregate the information.
 */
class UploadCameraUploadsRecordsUseCase @Inject constructor(
    private val findNodeWithFingerprintInParentNodeUseCase: FindNodeWithFingerprintInParentNodeUseCase,
    private val copyNodeUseCase: CopyNodeUseCase,
    private val setCoordinatesUseCase: SetCoordinatesUseCase,
    private val getNodeGPSCoordinatesUseCase: GetNodeGPSCoordinatesUseCase,
    private val getFingerprintUseCase: GetFingerprintUseCase,
    private val startUploadUseCase: StartUploadUseCase,
    private val getGPSCoordinatesUseCase: GetGPSCoordinatesUseCase,
    private val setOriginalFingerprintUseCase: SetOriginalFingerprintUseCase,
    private val areLocationTagsEnabledUseCase: AreLocationTagsEnabledUseCase,
    private val createTempFileAndRemoveCoordinatesUseCase: CreateTempFileAndRemoveCoordinatesUseCase,
    private val setCameraUploadsRecordUploadStatusUseCase: SetCameraUploadsRecordUploadStatusUseCase,
    private val setCameraUploadsRecordGeneratedFingerprintUseCase: SetCameraUploadsRecordGeneratedFingerprintUseCase,
    private val createImageOrVideoThumbnailUseCase: CreateImageOrVideoThumbnailUseCase,
    private val createImageOrVideoPreviewUseCase: CreateImageOrVideoPreviewUseCase,
    private val deleteThumbnailUseCase: DeleteThumbnailUseCase,
    private val deletePreviewUseCase: DeletePreviewUseCase,
    private val compressVideoUseCase: CompressVideoUseCase,
    private val getUploadVideoQualityUseCase: GetUploadVideoQualityUseCase,
    private val addCompletedTransferUseCase: AddCompletedTransferUseCase,
    private val fileSystemRepository: FileSystemRepository,
) {

    companion object {
        private const val CONCURRENT_UPLOADS_LIMIT = 16
        private const val CONCURRENT_VIDEO_COMPRESSION_LIMIT = 1
    }

    /**
     * Limit the number of concurrent uploads to [CONCURRENT_UPLOADS_LIMIT]
     * to not overload the memory of the app,
     */
    private val semaphore = Semaphore(CONCURRENT_UPLOADS_LIMIT)

    /**
     * Limit the number of concurrent video compression to [CONCURRENT_VIDEO_COMPRESSION_LIMIT]
     * to not overload the memory and cache size of the app
     */
    private val videoCompressionSemaphore = Semaphore(CONCURRENT_VIDEO_COMPRESSION_LIMIT)

    /**
     * Camera Uploads upload process
     *
     * @param cameraUploadsRecords The list of records to process
     * @param primaryUploadNodeId The primary upload node id
     * @param secondaryUploadNodeId The secondary upload node id
     * @param tempRoot The file path to the temporary folder to generate temp files
     */
    suspend operator fun invoke(
        cameraUploadsRecords: List<CameraUploadsRecord>,
        primaryUploadNodeId: NodeId,
        secondaryUploadNodeId: NodeId,
        tempRoot: String,
    ): Flow<CameraUploadsTransferProgress> = channelFlow {
        val videoQuality = getUploadVideoQualityUseCase()
        val locationTagsDisabled = !areLocationTagsEnabledUseCase()

        cameraUploadsRecords.mapNotNull { record ->
            runCatching {
                retrieveNode(
                    record = record,
                    parentNodeId =
                    getParentNodeId(record, primaryUploadNodeId, secondaryUploadNodeId)
                ).let { (existsInParentFolder, existingNodeId) ->
                    Triple(record, existsInParentFolder, existingNodeId)
                }
            }.getOrElse {
                trySend(CameraUploadsTransferProgress.Error(record, it))
                null
            }
        }.map { (record, existsInParentFolder, existingNodeId) ->
            launch {
                semaphore.acquire()

                val parentNodeId =
                    getParentNodeId(record, primaryUploadNodeId, secondaryUploadNodeId)

                when {
                    // node does not exist => upload
                    existingNodeId == null -> {

                        val shouldRemoveLocationTags =
                            record.type == SyncRecordType.TYPE_PHOTO && locationTagsDisabled
                        val shouldCompressVideo =
                            record.type == SyncRecordType.TYPE_VIDEO && videoQuality != VideoQuality.ORIGINAL

                        // create temporary file
                        if (shouldRemoveLocationTags) {
                            createTempFileAndRemoveCoordinates(record, tempRoot)
                                .catch {
                                    trySend(CameraUploadsTransferProgress.Error(record, it))
                                    setCameraUploadsRecordUploadStatus(
                                        record = record,
                                        status = if (it is FileNotFoundException)
                                            CameraUploadsRecordUploadStatus.LOCAL_FILE_NOT_EXIST
                                        else CameraUploadsRecordUploadStatus.FAILED
                                    ).onFailure { error ->
                                        trySend(CameraUploadsTransferProgress.Error(record, error))
                                    }
                                }
                                .singleOrNull()
                                ?: run {
                                    semaphore.release()
                                    return@launch
                                }
                        }

                        // Compress Video
                        if (shouldCompressVideo) {
                            videoCompressionSemaphore.acquire()
                            compressVideo(record, tempRoot, videoQuality)
                                .catch {
                                    emit(VideoCompressionState.Finished)
                                    trySend(CameraUploadsTransferProgress.Error(record, it))
                                }
                                .onCompletion { videoCompressionSemaphore.release() }
                                .collect {
                                    when (it) {
                                        is VideoCompressionState.Progress -> {
                                            trySend(
                                                CameraUploadsTransferProgress.Compressing.Progress(
                                                    record = record,
                                                    progress = it.progress,
                                                )
                                            )
                                        }

                                        is VideoCompressionState.Successful -> {
                                            trySend(

                                                CameraUploadsTransferProgress.Compressing.Successful(
                                                    record = record,
                                                )
                                            )
                                        }

                                        is VideoCompressionState.InsufficientStorage -> {
                                            trySend(
                                                CameraUploadsTransferProgress.Compressing.InsufficientStorage(
                                                    record = record,
                                                )
                                            )
                                        }

                                        else -> Unit
                                    }
                                }
                        }

                        // generate fingerprint and save it
                        // This step is important to check if a file exist in the cloud drive,
                        // in case the original fingerprint cannot be assigned to the Node after the transfer finishes
                        val setGeneratedFingerprintJob = launch {
                            setGeneratedFingerprint(record)
                                .onFailure {
                                    trySend(CameraUploadsTransferProgress.Error(record, it))
                                }
                        }

                        // retrieve path of file to upload
                        val path = getPath(record, shouldRemoveLocationTags, shouldCompressVideo)

                        // upload
                        startUploadUseCase(
                            localPath = path,
                            parentNodeId = parentNodeId,
                            fileName = record.fileName,
                            modificationTime = record.timestamp / 1000,
                            appData = TransferType.CU_UPLOAD.name,
                            isSourceTemporary = false,
                            shouldStartFirst = false,
                        ).collect { transferEvent ->
                            when (transferEvent) {
                                is TransferEvent.TransferStartEvent -> {
                                    // set status to STARTED
                                    setCameraUploadsRecordUploadStatus(
                                        record = record,
                                        status = CameraUploadsRecordUploadStatus.STARTED,
                                    ).onFailure {
                                        trySend(CameraUploadsTransferProgress.Error(record, it))
                                    }

                                    trySend(
                                        CameraUploadsTransferProgress.ToUpload(
                                            record = record,
                                            transferEvent = transferEvent,
                                        )
                                    )
                                }

                                is TransferEvent.TransferFinishEvent -> {
                                    processTransferFinishEvent(record, transferEvent, path)
                                        .collect {
                                            trySend(CameraUploadsTransferProgress.Error(record, it))
                                        }

                                    // Make sure that the generated fingerprint has complete
                                    setGeneratedFingerprintJob.join()

                                    // delete temp file
                                    deleteTempFile(record)
                                        .onFailure {
                                            trySend(CameraUploadsTransferProgress.Error(record, it))
                                        }


                                    trySend(
                                        CameraUploadsTransferProgress.Uploaded(
                                            record = record,
                                            transferEvent = transferEvent,
                                            nodeId = NodeId(transferEvent.transfer.nodeHandle),
                                        )
                                    )

                                    semaphore.release()
                                }

                                is TransferEvent.TransferUpdateEvent -> {
                                    trySend(
                                        CameraUploadsTransferProgress.UploadInProgress.TransferUpdate(
                                            record = record,
                                            transferEvent = transferEvent,
                                        )
                                    )
                                }

                                is TransferEvent.TransferTemporaryErrorEvent ->
                                    trySend(
                                        CameraUploadsTransferProgress.UploadInProgress.TransferTemporaryError(
                                            record = record,
                                            transferEvent = transferEvent,
                                        )
                                    )

                                else -> Unit
                            }
                        }
                    }

                    // node exists but not in target folder => copy
                    existsInParentFolder == false -> {
                        trySend(
                            CameraUploadsTransferProgress.ToCopy(
                                record = record,
                                nodeId = existingNodeId,
                            )
                        )

                        copyNode(
                            record = record,
                            existingNodeId = existingNodeId,
                            parentNodeId = parentNodeId,
                        ).onFailure {
                            trySend(CameraUploadsTransferProgress.Error(record, it))
                        }

                        trySend(
                            CameraUploadsTransferProgress.Copied(
                                record = record,
                                nodeId = existingNodeId,
                            )
                        )

                        setCameraUploadsRecordUploadStatus(
                            record = record,
                            status = CameraUploadsRecordUploadStatus.COPIED
                        ).onFailure {
                            trySend(CameraUploadsTransferProgress.Error(record, it))
                        }

                        semaphore.release()
                        return@launch
                    }

                    // node exists in target folder or is in rubbish bin => do nothing
                    else -> {
                        setCameraUploadsRecordUploadStatus(
                            record = record,
                            status = CameraUploadsRecordUploadStatus.ALREADY_EXISTS,
                        ).onFailure {
                            trySend(CameraUploadsTransferProgress.Error(record, it))
                        }

                        semaphore.release()
                        return@launch
                    }
                }
            }
        }.joinAll()
        channel.close()
    }
        .buffer(UNLIMITED)
        .cancellable()

    /**
     * Compress a video
     * Will emit a [VideoCompressionState.Finished] if an error is thrown
     *
     * @param record
     * @param tempRoot
     * @param videoQuality
     * @return a [Flow] of [VideoCompressionState]
     */
    private fun compressVideo(
        record: CameraUploadsRecord,
        tempRoot: String,
        videoQuality: VideoQuality,
    ): Flow<VideoCompressionState> =
        compressVideoUseCase(
            tempRoot,
            record.filePath,
            record.tempFilePath,
            videoQuality,
        ).cancellable()


    /**
     * Create a temporary file with the coordinates removed
     *
     * @param record
     * @param tempRoot
     * @return a [Flow] of [String]
     *
     */
    private suspend fun createTempFileAndRemoveCoordinates(
        record: CameraUploadsRecord,
        tempRoot: String
    ): Flow<String> = flow {
        emit(
            createTempFileAndRemoveCoordinatesUseCase(
                tempRoot,
                record.filePath,
                record.tempFilePath,
                record.timestamp,
            )
        )
    }.retry(60) { cause ->
        return@retry if (cause is NotEnoughStorageException) {
            // total delay (1 second times 60 attempts) = 60 seconds
            delay(TimeUnit.SECONDS.toMillis(1))
            true
        } else {
            // not storage exception, no need to retry
            false
        }
    }.cancellable()

    /**
     * Find a node corresponding to the [CameraUploadsRecord] in the cloud
     *
     * @param record
     * @param parentNodeId
     *
     * @return a [Pair] of <Boolean?, Boolean?>.
     *         The first element will return true if it exists in the parent folder given in parameter,
     *         false otherwise, null if in rubbish bin
     *         The second element will return the node retrieved, null if cannot be retrieved
     */
    private suspend fun retrieveNode(
        record: CameraUploadsRecord,
        parentNodeId: NodeId
    ): Pair<Boolean?, NodeId?> {
        val (existsInParentFolder, existingNodeId) =
            findNodeWithFingerprintInParentNodeUseCase(
                record.originalFingerprint,
                record.generatedFingerprint,
                parentNodeId,
            )
        return Pair(existsInParentFolder, existingNodeId)
    }

    /**
     * Get the path of the file to upload
     *
     * @param record
     * @param shouldRemoveLocationTags
     * @param shouldCompressVideo
     *
     * @return the path of file to upload
     */
    private suspend fun getPath(
        record: CameraUploadsRecord,
        shouldRemoveLocationTags: Boolean,
        shouldCompressVideo: Boolean,
    ): String = when {
        shouldRemoveLocationTags -> {
            record.tempFilePath
        }

        shouldCompressVideo -> {
            // Fallback to the original file if for some reason the compression failed
            record.tempFilePath.takeIf { fileSystemRepository.doesFileExist(it) }
                ?: record.filePath
        }

        else -> record.filePath
    }

    /**
     * Delete the temporary file created
     *
     * @param record
     */
    private suspend fun deleteTempFile(record: CameraUploadsRecord) = runCatching {
        fileSystemRepository.deleteFile(File(record.tempFilePath))
    }

    /**
     * Set the generated fingerprint to the database
     *
     * This value will be used to check the existence of the file in the cloud
     *
     * @param record
     */
    private suspend fun setGeneratedFingerprint(
        record: CameraUploadsRecord
    ) = runCatching {
        getFingerprintUseCase(record.tempFilePath)?.let { generatedFingerprint ->
            setCameraUploadsRecordGeneratedFingerprintUseCase(
                mediaId = record.mediaId,
                timestamp = record.timestamp,
                folderType = record.folderType,
                generatedFingerprint = generatedFingerprint,
            )
        }
    }

    /**
     * Run some operations after a transfer completes
     *
     * @param record
     * @param transferEvent
     * @param path
     *
     * @return a [Flow] of [Throwable] to inform about the failure of one of the operations
     */
    private suspend fun processTransferFinishEvent(
        record: CameraUploadsRecord,
        transferEvent: TransferEvent.TransferFinishEvent,
        path: String,
    ) = channelFlow {
        val nodeId = NodeId(transferEvent.transfer.nodeHandle)
        listOf(
            launch {
                setOriginalFingerprintUseCase(
                    record = record,
                    nodeId = nodeId,
                ).onFailure { trySend(it) }
            },
            launch {
                runCatching {
                    setGpsCoordinatesFromRecord(
                        record = record,
                        nodeId = nodeId,
                    )
                }.onFailure { trySend(it) }
            },
            launch {
                setCameraUploadsRecordUploadStatus(
                    record = record,
                    status = CameraUploadsRecordUploadStatus.UPLOADED,
                ).onFailure { trySend(it) }
            },
            launch {
                createThumbnailAndPreview(
                    path = path,
                    nodeId = nodeId,
                ).onFailure { trySend(it) }
            },
            launch {
                addCompletedTransfer(transferEvent)
                    .onFailure { trySend(it) }
            }
        ).joinAll()
        close()
    }

    /**
     * Add the transfer to the completed transfer list
     *
     * @param transferEvent
     */
    private suspend fun addCompletedTransfer(
        transferEvent: TransferEvent.TransferFinishEvent,
    ) = runCatching {
        with(transferEvent) {
            addCompletedTransferUseCase(transfer, error)
        }
    }

    /**
     * Copy a node and set the gps coordinates to the node
     *
     * @param record
     * @param existingNodeId
     * @param parentNodeId
     */
    private suspend fun copyNode(
        record: CameraUploadsRecord,
        existingNodeId: NodeId,
        parentNodeId: NodeId,
    ) = runCatching {
        copyNodeUseCase(
            nodeToCopy = existingNodeId,
            newNodeParent = parentNodeId,
            newNodeName = record.fileName,
        ).let { newNodeId ->
            setGpsCoordinatesFromNode(
                existingNodeId = existingNodeId,
                newNodeId = newNodeId,
            )
        }
    }

    /**
     * Set the original fingerprint to the node
     *
     * @param record
     * @param nodeId
     */
    private suspend fun setOriginalFingerprintUseCase(
        record: CameraUploadsRecord,
        nodeId: NodeId,
    ) = runCatching {
        setOriginalFingerprintUseCase(
            nodeId = nodeId,
            originalFingerprint = record.originalFingerprint,
        )
    }

    /**
     * Set the gps coordinates to a node from a record
     *
     * @param record
     * @param nodeId
     */
    private suspend fun setGpsCoordinatesFromRecord(
        record: CameraUploadsRecord,
        nodeId: NodeId,
    ) = runCatching {
        getGpsCoordinatesFromRecord(
            record = record,
        ).let { (latitude, longitude) ->
            setGpsCoordinates(
                nodeId = nodeId,
                latitude = latitude,
                longitude = longitude,
            )
        }
    }

    /**
     * Set the gps coordinates to a node from an existing node
     *
     * @param existingNodeId
     * @param newNodeId
     */
    private suspend fun setGpsCoordinatesFromNode(
        existingNodeId: NodeId,
        newNodeId: NodeId
    ) = runCatching {
        getGpsCoordinatesFromNode(
            existingNodeId = existingNodeId,
        ).let { (latitude, longitude) ->
            setGpsCoordinates(
                nodeId = newNodeId,
                latitude = latitude,
                longitude = longitude,
            )
        }
    }

    /**
     * Get the gps coordinates from a record
     *
     * @param record
     * @return a [Pair] of <[Double], [Double]> corresponding to latitude and longitude
     */
    private suspend fun getGpsCoordinatesFromRecord(
        record: CameraUploadsRecord,
    ): Pair<Double, Double> = getGPSCoordinatesUseCase(
        filePath = record.filePath,
        isVideo = record.type == SyncRecordType.TYPE_VIDEO,
    ).let { (latitude, longitude) -> Pair(latitude.toDouble(), longitude.toDouble()) }

    /**
     * Get the gps coordinates from node
     *
     * @param existingNodeId
     * @return a [Pair] of <[Double], [Double]> corresponding to latitude and longitude
     */
    private suspend fun getGpsCoordinatesFromNode(existingNodeId: NodeId) =
        getNodeGPSCoordinatesUseCase(existingNodeId)

    /**
     * Set gps coordinates to a Node
     *
     * @param nodeId
     * @param latitude
     * @param longitude
     */
    private suspend fun setGpsCoordinates(
        nodeId: NodeId,
        latitude: Double,
        longitude: Double,
    ) = setCoordinatesUseCase(
        nodeId = nodeId,
        latitude = latitude,
        longitude = longitude,
    )

    /**
     * Create thumbnail and preview
     *
     * @param path
     * @param nodeId
     */
    private suspend fun createThumbnailAndPreview(
        path: String,
        nodeId: NodeId,
    ) = runCatching {
        File(path).let {
            val nodeHandle = nodeId.longValue
            if (deleteThumbnailUseCase(nodeId.longValue)) {
                createImageOrVideoThumbnailUseCase(nodeHandle, it)
            }
            if (deletePreviewUseCase(nodeHandle)) {
                createImageOrVideoPreviewUseCase(nodeHandle, it)
            }
        }
    }

    /**
     * Set the camera uploads status
     *
     * @param record
     * @param status
     */
    private suspend fun setCameraUploadsRecordUploadStatus(
        record: CameraUploadsRecord,
        status: CameraUploadsRecordUploadStatus,
    ) = runCatching {
        setCameraUploadsRecordUploadStatusUseCase(
            mediaId = record.mediaId,
            timestamp = record.timestamp,
            folderType = record.folderType,
            uploadStatus = status,
        )
    }

    /**
     * Get the target node id based on the record folder type
     *
     * @param record
     * @param primaryUploadNodeId
     * @param secondaryUploadNodeId
     */
    private fun getParentNodeId(
        record: CameraUploadsRecord,
        primaryUploadNodeId: NodeId,
        secondaryUploadNodeId: NodeId
    ): NodeId = when (record.folderType) {
        CameraUploadFolderType.Primary -> primaryUploadNodeId
        CameraUploadFolderType.Secondary -> secondaryUploadNodeId
    }
}
