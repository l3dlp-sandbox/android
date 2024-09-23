package mega.privacy.android.data.facade

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import mega.privacy.android.data.cryptography.DecryptData
import mega.privacy.android.data.cryptography.EncryptData
import mega.privacy.android.data.database.dao.ActiveTransferDao
import mega.privacy.android.data.database.dao.BackupDao
import mega.privacy.android.data.database.dao.CameraUploadsRecordDao
import mega.privacy.android.data.database.dao.ChatPendingChangesDao
import mega.privacy.android.data.database.dao.CompletedTransferDao
import mega.privacy.android.data.database.dao.ContactDao
import mega.privacy.android.data.database.dao.OfflineDao
import mega.privacy.android.data.database.dao.SdTransferDao
import mega.privacy.android.data.database.dao.VideoRecentlyWatchedDao
import mega.privacy.android.data.gateway.MegaLocalRoomGateway
import mega.privacy.android.data.mapper.backup.BackupEntityMapper
import mega.privacy.android.data.mapper.backup.BackupInfoTypeIntMapper
import mega.privacy.android.data.mapper.backup.BackupModelMapper
import mega.privacy.android.data.mapper.camerauploads.CameraUploadsRecordEntityMapper
import mega.privacy.android.data.mapper.camerauploads.CameraUploadsRecordModelMapper
import mega.privacy.android.data.mapper.chat.ChatRoomPendingChangesEntityMapper
import mega.privacy.android.data.mapper.chat.ChatRoomPendingChangesModelMapper
import mega.privacy.android.data.mapper.contact.ContactEntityMapper
import mega.privacy.android.data.mapper.contact.ContactModelMapper
import mega.privacy.android.data.mapper.offline.OfflineEntityMapper
import mega.privacy.android.data.mapper.offline.OfflineModelMapper
import mega.privacy.android.data.mapper.transfer.active.ActiveTransferEntityMapper
import mega.privacy.android.data.mapper.transfer.completed.CompletedTransferEntityMapper
import mega.privacy.android.data.mapper.transfer.completed.CompletedTransferLegacyModelMapper
import mega.privacy.android.data.mapper.transfer.completed.CompletedTransferModelMapper
import mega.privacy.android.data.mapper.transfer.sd.SdTransferEntityMapper
import mega.privacy.android.data.mapper.transfer.sd.SdTransferModelMapper
import mega.privacy.android.data.mapper.videosection.VideoRecentlyWatchedEntityMapper
import mega.privacy.android.data.mapper.videosection.VideoRecentlyWatchedItemMapper
import mega.privacy.android.data.model.VideoRecentlyWatchedItem
import mega.privacy.android.domain.entity.CameraUploadsRecordType
import mega.privacy.android.domain.entity.Contact
import mega.privacy.android.domain.entity.Offline
import mega.privacy.android.domain.entity.SdTransfer
import mega.privacy.android.domain.entity.backup.Backup
import mega.privacy.android.domain.entity.backup.BackupInfoType
import mega.privacy.android.domain.entity.camerauploads.CameraUploadFolderType
import mega.privacy.android.domain.entity.camerauploads.CameraUploadsRecord
import mega.privacy.android.domain.entity.camerauploads.CameraUploadsRecordUploadStatus
import mega.privacy.android.domain.entity.chat.ChatPendingChanges
import mega.privacy.android.domain.entity.transfer.ActiveTransfer
import mega.privacy.android.domain.entity.transfer.CompletedTransfer
import mega.privacy.android.domain.entity.transfer.TransferType
import javax.inject.Inject

internal class MegaLocalRoomFacade @Inject constructor(
    private val contactDao: ContactDao,
    private val contactEntityMapper: ContactEntityMapper,
    private val contactModelMapper: ContactModelMapper,
    private val completedTransferDao: CompletedTransferDao,
    private val activeTransferDao: ActiveTransferDao,
    private val completedTransferModelMapper: CompletedTransferModelMapper,
    private val completedTransferEntityMapper: CompletedTransferEntityMapper,
    private val completedTransferLegacyModelMapper: CompletedTransferLegacyModelMapper,
    private val activeTransferEntityMapper: ActiveTransferEntityMapper,
    private val sdTransferDao: SdTransferDao,
    private val sdTransferModelMapper: SdTransferModelMapper,
    private val sdTransferEntityMapper: SdTransferEntityMapper,
    private val backupDao: BackupDao,
    private val backupEntityMapper: BackupEntityMapper,
    private val backupModelMapper: BackupModelMapper,
    private val backupInfoTypeIntMapper: BackupInfoTypeIntMapper,
    private val cameraUploadsRecordDao: CameraUploadsRecordDao,
    private val cameraUploadsRecordEntityMapper: CameraUploadsRecordEntityMapper,
    private val cameraUploadsRecordModelMapper: CameraUploadsRecordModelMapper,
    private val encryptData: EncryptData,
    private val decryptData: DecryptData,
    private val offlineDao: OfflineDao,
    private val offlineModelMapper: OfflineModelMapper,
    private val offlineEntityMapper: OfflineEntityMapper,
    private val chatPendingChangesDao: ChatPendingChangesDao,
    private val chatRoomPendingChangesEntityMapper: ChatRoomPendingChangesEntityMapper,
    private val chatRoomPendingChangesModelMapper: ChatRoomPendingChangesModelMapper,
    private val videoRecentlyWatchedDao: VideoRecentlyWatchedDao,
    private val videoRecentlyWatchedEntityMapper: VideoRecentlyWatchedEntityMapper,
    private val videoRecentlyWatchedItemMapper: VideoRecentlyWatchedItemMapper,
) : MegaLocalRoomGateway {
    override suspend fun insertContact(contact: Contact) {
        contactDao.insertOrUpdateContact(contactEntityMapper(contact))
    }

    override suspend fun updateContactNameByEmail(firstName: String?, email: String?) {
        if (email.isNullOrBlank()) return
        contactDao.getContactByEmail(encryptData(email))?.let { entity ->
            contactDao.insertOrUpdateContact(entity.copy(firstName = encryptData(firstName)))
        }
    }

    override suspend fun updateContactLastNameByEmail(lastName: String?, email: String?) {
        if (email.isNullOrBlank()) return
        contactDao.getContactByEmail(encryptData(email))?.let { entity ->
            contactDao.insertOrUpdateContact(entity.copy(lastName = encryptData(lastName)))
        }
    }

    override suspend fun updateContactMailByHandle(handle: Long, email: String?) {
        contactDao.getContactByHandle(encryptData(handle.toString()))?.let { entity ->
            contactDao.insertOrUpdateContact(entity.copy(mail = encryptData(email)))
        }
    }

    override suspend fun updateContactFistNameByHandle(handle: Long, firstName: String?) {
        contactDao.getContactByHandle(encryptData(handle.toString()))?.let { entity ->
            contactDao.insertOrUpdateContact(entity.copy(firstName = encryptData(firstName)))
        }
    }

    override suspend fun updateContactLastNameByHandle(handle: Long, lastName: String?) {
        contactDao.getContactByHandle(encryptData(handle.toString()))?.let { entity ->
            contactDao.insertOrUpdateContact(entity.copy(lastName = encryptData(lastName)))
        }
    }

    override suspend fun updateContactNicknameByHandle(handle: Long, nickname: String?) {
        contactDao.getContactByHandle(encryptData(handle.toString()))?.let { entity ->
            contactDao.insertOrUpdateContact(entity.copy(nickName = encryptData(nickname)))
        }
    }

    override suspend fun getContactByHandle(handle: Long): Contact? =
        contactDao.getContactByHandle(encryptData(handle.toString()))
            ?.let { contactModelMapper(it) }

    override suspend fun getContactByEmail(email: String?): Contact? =
        contactDao.getContactByEmail(encryptData(email))?.let { contactModelMapper(it) }

    override suspend fun deleteAllContacts() = contactDao.deleteAllContact()

    override suspend fun getContactCount() = contactDao.getContactCount()

    override suspend fun getAllContacts(): List<Contact> {
        val entities = contactDao.getAllContact().first()
        return entities.map { contactModelMapper(it) }
    }

    override fun getCompletedTransfers(size: Int?) =
        completedTransferDao.getAllCompletedTransfers()
            .map { list ->
                list.map { completedTransferModelMapper(it) }
                    .toMutableList()
                    .apply { sortWith(compareByDescending { it.timestamp }) }
                    .let { if (size != null) it.take(size) else it }
            }

    override suspend fun addCompletedTransfer(transfer: CompletedTransfer) {
        completedTransferDao.insertOrUpdateCompletedTransfer(completedTransferEntityMapper(transfer))
    }

    override suspend fun addCompletedTransfers(transfers: List<CompletedTransfer>) {
        transfers.map { completedTransferEntityMapper(it) }.let { mappedTransfers ->
            completedTransferDao.insertOrUpdateCompletedTransfers(
                mappedTransfers,
                MAX_INSERT_LIST_SIZE
            )
        }
    }

    override suspend fun getCompletedTransfersCount() =
        completedTransferDao.getCompletedTransfersCount()

    override suspend fun deleteAllCompletedTransfers() =
        completedTransferDao.deleteAllCompletedTransfers()

    override suspend fun getCompletedTransfersByState(states: List<Int>): List<CompletedTransfer> {
        val encryptedStates = states.mapNotNull { encryptData(it.toString()) }
        return completedTransferDao.getCompletedTransfersByState(encryptedStates)
            .map { entity -> completedTransferModelMapper(entity) }
    }

    override suspend fun deleteCompletedTransfersByState(states: List<Int>): List<CompletedTransfer> {
        val encryptedStates = states.mapNotNull { encryptData(it.toString()) }
        val entities = completedTransferDao.getCompletedTransfersByState(encryptedStates)
        deleteCompletedTransferBatch(entities.mapNotNull { it.id })
        return entities.map { entity -> completedTransferModelMapper(entity) }
    }

    override suspend fun deleteCompletedTransfer(completedTransfer: CompletedTransfer) {
        completedTransferDao.deleteCompletedTransferByIds(
            listOf(completedTransfer.id ?: return)
        )
    }

    override suspend fun deleteOldestCompletedTransfers() {
        val count = completedTransferDao.getCompletedTransfersCount()
        if (count > MAX_COMPLETED_TRANSFER_ROWS) {
            val transfers = completedTransferDao.getAllCompletedTransfers().first()
                .map { completedTransferModelMapper(it) }
            val deletedTransfers =
                transfers.sortedWith(compareByDescending { it.timestamp })
                    .drop(MAX_COMPLETED_TRANSFER_ROWS)
                    .mapNotNull { it.id }

            if (deletedTransfers.isNotEmpty()) {
                deleteCompletedTransferBatch(deletedTransfers)
            }
        }
    }

    override suspend fun migrateLegacyCompletedTransfers() {
        completedTransferDao.getAllLegacyCompletedTransfers()
            .takeIf { it.isNotEmpty() }
            ?.let { legacyEntities ->
                val firstHundred = legacyEntities
                    .sortedWith(compareByDescending { it.timestamp })
                    .take(100)
                addCompletedTransfers(firstHundred.map { completedTransferLegacyModelMapper(it) })
                completedTransferDao.deleteAllLegacyCompletedTransfers()
            }
    }

    override suspend fun getActiveTransferByTag(tag: Int) =
        activeTransferDao.getActiveTransferByTag(tag)

    override fun getActiveTransfersByType(transferType: TransferType) =
        activeTransferDao.getActiveTransfersByType(transferType).map { activeTransferEntities ->
            activeTransferEntities.map { it }
        }

    override suspend fun getCurrentActiveTransfersByType(transferType: TransferType) =
        activeTransferDao.getCurrentActiveTransfersByType(transferType).map { it }

    override suspend fun getCurrentActiveTransfers(): List<ActiveTransfer> =
        activeTransferDao.getCurrentActiveTransfers()

    override suspend fun insertOrUpdateActiveTransfer(activeTransfer: ActiveTransfer) =
        activeTransferDao.insertOrUpdateActiveTransfer(activeTransferEntityMapper(activeTransfer))

    override suspend fun insertOrUpdateActiveTransfers(activeTransfers: List<ActiveTransfer>) =
        activeTransfers.map { activeTransferEntityMapper(it) }.let { mappedActiveTransfers ->
            activeTransferDao.insertOrUpdateActiveTransfers(
                mappedActiveTransfers,
                MAX_INSERT_LIST_SIZE
            )
        }

    override suspend fun deleteAllActiveTransfersByType(transferType: TransferType) =
        activeTransferDao.deleteAllActiveTransfersByType(transferType)

    override suspend fun deleteAllActiveTransfers() = activeTransferDao.deleteAllActiveTransfers()

    override suspend fun setActiveTransferAsFinishedByTag(tags: List<Int>) =
        activeTransferDao.setActiveTransferAsFinishedByTag(tags)

    override suspend fun getAllSdTransfers(): List<SdTransfer> {
        val entities = sdTransferDao.getAllSdTransfers().first()
        return entities.map { sdTransferModelMapper(it) }
    }

    override suspend fun getSdTransferByTag(tag: Int): SdTransfer? =
        sdTransferDao.getSdTransferByTag(tag)?.let {
            sdTransferModelMapper(it)
        }

    override suspend fun insertSdTransfer(transfer: SdTransfer) =
        sdTransferDao.insertSdTransfer(sdTransferEntityMapper(transfer))

    override suspend fun deleteSdTransferByTag(tag: Int) {
        sdTransferDao.deleteSdTransferByTag(tag)
    }

    override suspend fun getCompletedTransferById(id: Int) = completedTransferDao
        .getCompletedTransferById(id)?.let { completedTransferModelMapper(it) }

    override suspend fun insertOrUpdateCameraUploadsRecords(records: List<CameraUploadsRecord>) =
        cameraUploadsRecordDao.insertOrUpdateCameraUploadsRecords(
            records.map { cameraUploadsRecordEntityMapper(it) }
        )

    override suspend fun getAllCameraUploadsRecords(): List<CameraUploadsRecord> =
        cameraUploadsRecordDao.getAllCameraUploadsRecords().map {
            cameraUploadsRecordModelMapper(it)
        }

    override suspend fun getCameraUploadsRecordsBy(
        uploadStatus: List<CameraUploadsRecordUploadStatus>,
        types: List<CameraUploadsRecordType>,
        folderTypes: List<CameraUploadFolderType>,
    ): List<CameraUploadsRecord> =
        cameraUploadsRecordDao.getCameraUploadsRecordsBy(
            uploadStatus,
            types,
            folderTypes,
        ).map {
            cameraUploadsRecordModelMapper(it)
        }

    override suspend fun updateCameraUploadsRecordUploadStatus(
        mediaId: Long,
        timestamp: Long,
        folderType: CameraUploadFolderType,
        uploadStatus: CameraUploadsRecordUploadStatus,
    ) {
        cameraUploadsRecordDao.updateCameraUploadsRecordUploadStatus(
            mediaId,
            timestamp,
            folderType,
            uploadStatus
        )
    }

    override suspend fun setCameraUploadsRecordGeneratedFingerprint(
        mediaId: Long,
        timestamp: Long,
        folderType: CameraUploadFolderType,
        generatedFingerprint: String,
    ) {
        cameraUploadsRecordDao.updateCameraUploadsRecordGeneratedFingerprint(
            mediaId,
            timestamp,
            folderType,
            generatedFingerprint
        )
    }

    override suspend fun deleteCameraUploadsRecords(folderTypes: List<CameraUploadFolderType>) =
        cameraUploadsRecordDao.deleteCameraUploadsRecordsByFolderType(folderTypes)

    override suspend fun deleteBackupById(backupId: Long) {
        encryptData(backupId.toString())?.let {
            backupDao.deleteBackupByBackupId(it)
        }
    }

    override suspend fun setBackupAsOutdated(backupId: Long) {
        encryptData(backupId.toString())?.let { encryptedBackupId ->
            encryptData("true")?.let { encryptedTrue ->
                backupDao.updateBackupAsOutdated(
                    encryptedBackupId = encryptedBackupId,
                    encryptedIsOutdated = encryptedTrue
                )
            }
        }
    }

    override suspend fun saveBackup(backup: Backup) {
        backupEntityMapper(backup)?.let {
            backupDao.insertOrUpdateBackup(it)
        }
    }

    override suspend fun getCuBackUp(): Backup? {
        return encryptData("false")?.let { encryptedFalse ->
            backupDao.getBackupByType(
                backupType = backupInfoTypeIntMapper(BackupInfoType.CAMERA_UPLOADS),
                encryptedIsOutdated = encryptedFalse
            ).lastOrNull()
        }?.let { backupModelMapper(it) }
    }

    override suspend fun getMuBackUp(): Backup? {
        return encryptData("false")?.let { encryptedFalse ->
            backupDao.getBackupByType(
                backupInfoTypeIntMapper(BackupInfoType.MEDIA_UPLOADS),
                encryptedFalse
            ).lastOrNull()
        }?.let { backupModelMapper(it) }
    }

    override suspend fun getCuBackUpId(): Long? {
        return encryptData("false")?.let { encryptedFalse ->
            backupDao.getBackupIdByType(
                backupInfoTypeIntMapper(BackupInfoType.CAMERA_UPLOADS),
                encryptedFalse
            ).lastOrNull()
        }?.let { decryptData(it) }?.toLong()
    }

    override suspend fun getMuBackUpId(): Long? {
        return encryptData("false")?.let { encryptedFalse ->
            backupDao.getBackupIdByType(
                backupInfoTypeIntMapper(BackupInfoType.MEDIA_UPLOADS),
                encryptedFalse
            ).lastOrNull()
        }?.let { decryptData(it) }?.toLong()
    }

    override suspend fun getBackupById(id: Long): Backup? {
        return encryptData(id.toString())?.let { encryptedBackupId ->
            backupDao.getBackupById(encryptedBackupId)
        }?.let { backupModelMapper(it) }
    }

    override suspend fun updateBackup(backup: Backup) {
        backupEntityMapper(backup)?.let {
            backupDao.insertOrUpdateBackup(it)
        }
    }

    override suspend fun deleteAllBackups() {
        backupDao.deleteAllBackups()
    }

    override suspend fun isOfflineInformationAvailable(nodeHandle: Long) =
        offlineDao.getOfflineByHandle("${encryptData("$nodeHandle")}") != null

    override suspend fun getOfflineInformation(nodeHandle: Long) =
        offlineDao.getOfflineByHandle("${encryptData("$nodeHandle")}")?.let {
            offlineModelMapper(it)
        }

    override suspend fun saveOfflineInformation(offline: Offline) =
        offlineEntityMapper(offline).let {
            offlineDao.insertOrUpdateOffline(it)
        }

    override suspend fun clearOffline() = offlineDao.deleteAllOffline()

    override fun monitorOfflineUpdates() = offlineDao.monitorOffline()
        .map { it.map { offlineEntity -> offlineModelMapper(offlineEntity) } }


    override suspend fun getAllOfflineInfo() =
        offlineDao.getOfflineFiles()?.map { offlineModelMapper(it) } ?: emptyList()

    override suspend fun removeOfflineInformation(nodeId: String) {
        encryptData(nodeId)?.let {
            offlineDao.deleteOfflineByHandle(it)
        }
    }

    override suspend fun getOfflineInfoByParentId(parentId: Int): List<Offline> =
        offlineDao.getOfflineByParentId(parentId)?.map {
            offlineModelMapper(it)
        } ?: emptyList()

    override suspend fun getOfflineLineById(id: Int): Offline? =
        offlineDao.getOfflineById(id)?.let {
            offlineModelMapper(it)
        }

    override suspend fun removeOfflineInformationById(id: Int) {
        offlineDao.deleteOfflineById(id)
    }

    override suspend fun removeOfflineInformationByIds(ids: List<Int>) {
        offlineDao.deleteOfflineByIds(ids)
    }

    private suspend fun deleteCompletedTransferBatch(ids: List<Int>) {
        completedTransferDao.deleteCompletedTransferByIds(
            ids,
            MAX_INSERT_LIST_SIZE
        )
    }

    override suspend fun setChatPendingChanges(chatPendingChanges: ChatPendingChanges) {
        chatPendingChangesDao.upsertChatPendingChanges(
            chatRoomPendingChangesEntityMapper(chatPendingChanges)
        )
    }

    override suspend fun getAllRecentlyWatchedVideos() =
        videoRecentlyWatchedDao.getAllRecentlyWatchedVideos().map { entities ->
            entities.map { entity ->
                videoRecentlyWatchedItemMapper(entity.videoHandle, entity.watchedTimestamp)
            }
        }

    override suspend fun removeRecentlyWatchedVideo(handle: Long) =
        videoRecentlyWatchedDao.removeRecentlyWatchedVideo(handle)

    override suspend fun clearRecentlyWatchedVideos() =
        videoRecentlyWatchedDao.clearRecentlyWatchedVideos()

    override suspend fun saveRecentlyWatchedVideo(item: VideoRecentlyWatchedItem) {
        val entity = videoRecentlyWatchedEntityMapper(item)
        videoRecentlyWatchedDao.insertOrUpdateRecentlyWatchedVideo(entity)
    }

    override suspend fun saveRecentlyWatchedVideos(items: List<VideoRecentlyWatchedItem>) {
        val entities = items.map { videoRecentlyWatchedEntityMapper(it) }
        videoRecentlyWatchedDao.insertOrUpdateRecentlyWatchedVideos(entities)
    }

    override fun monitorChatPendingChanges(chatId: Long): Flow<ChatPendingChanges?> =
        chatPendingChangesDao.getChatPendingChanges(chatId)
            .map { entity -> entity?.let { chatRoomPendingChangesModelMapper(it) } }

    companion object {
        private const val MAX_COMPLETED_TRANSFER_ROWS = 100
        internal const val MAX_INSERT_LIST_SIZE = 200
    }
}
