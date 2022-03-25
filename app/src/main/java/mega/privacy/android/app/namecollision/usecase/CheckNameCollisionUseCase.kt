package mega.privacy.android.app.namecollision.usecase

import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.kotlin.blockingSubscribeBy
import mega.privacy.android.app.ShareInfo
import mega.privacy.android.app.di.MegaApi
import mega.privacy.android.app.namecollision.data.NameCollision
import mega.privacy.android.app.domain.exception.EmptyFolderException
import mega.privacy.android.app.namecollision.data.NameCollisionType
import mega.privacy.android.app.namecollision.exception.NoPendingCollisionsException
import mega.privacy.android.app.uploadFolder.list.data.FolderContent
import mega.privacy.android.app.usecase.GetNodeUseCase
import mega.privacy.android.app.usecase.exception.MegaNodeException
import mega.privacy.android.app.utils.LogUtil.logError
import mega.privacy.android.app.utils.RxUtil.blockingGetOrNull
import nz.mega.sdk.MegaApiAndroid
import nz.mega.sdk.MegaApiJava.INVALID_HANDLE
import nz.mega.sdk.MegaNode
import java.util.ArrayList
import javax.inject.Inject

/**
 * Use case for checking name collisions before uploading, copying or moving.
 *
 * @property megaApi        MegaApiAndroid instance to check collisions.
 * @property getNodeUseCase Required for getting nodes.
 */
class CheckNameCollisionUseCase @Inject constructor(
    @MegaApi private val megaApi: MegaApiAndroid,
    private val getNodeUseCase: GetNodeUseCase
) {

    /**
     * Checks if a node with the same name exists on the provided parent node.
     *
     * @param node        MegaNode to check its name.
     * @param parentNode  Parent MegaNode in which to look.
     * @param type          [NameCollisionType]
     * @return Single Long with the node handle with which there is a name collision.
     */
    fun check(
        node: MegaNode?,
        parentNode: MegaNode?,
        type: NameCollisionType
    ): Single<NameCollision> =
        check(node, parentNode, type)

    /**
     * Checks if a node with the same name exists on the provided parent node.
     *
     * @param handle        Handle of the node to check its name.
     * @param parentHandle  Handle of the parent node in which to look.
     * @param type          [NameCollisionType]
     * @return Single Long with the node handle with which there is a name collision.
     */
    fun check(handle: Long, parentHandle: Long, type: NameCollisionType): Single<NameCollision> =
        check(getNodeUseCase.get(handle).blockingGetOrNull(), parentHandle, type)

    /**
     * Checks if a node with the same name exists on the provided parent node.
     *
     * @param node          Node to check its name.
     * @param parentHandle  Handle of the parent node in which to look.
     * @param type          [NameCollisionType]
     * @return Single Long with the node handle with which there is a name collision.
     */
    fun check(node: MegaNode?, parentHandle: Long, type: NameCollisionType): Single<NameCollision> =
        Single.create { emitter ->
            if (node == null) {
                emitter.onError(MegaNodeException.NodeDoesNotExistsException())
                return@create
            }

            check(node.name, getNodeUseCase.get(parentHandle).blockingGetOrNull())
                .blockingSubscribeBy(
                    onError = { error -> emitter.onError(error) },
                    onSuccess = { handle ->
                        val collision: NameCollision? = when (type) {
                            NameCollisionType.COPY -> NameCollision.Copy.getCopyCollision(
                                handle,
                                node,
                                parentHandle
                            )
                            NameCollisionType.MOVEMENT -> NameCollision.Movement.getMovementCollision(
                                handle,
                                node,
                                parentHandle
                            )
                            else -> null
                        }

                        if (collision != null) {
                            emitter.onSuccess(collision)
                        }
                    }
                )
        }

    /**
     * Checks if a node with the given name exists on the provided parent node.
     *
     * @param name          Name of the node.
     * @param parentHandle  Handle of the parent node in which to look.
     * @return Single Long with the node handle with which there is a name collision.
     */
    fun check(name: String, parentHandle: Long): Single<Long> =
        Single.create { emitter ->
            val parentNode = if (parentHandle == INVALID_HANDLE) {
                megaApi.rootNode
            } else {
                getNodeUseCase.get(parentHandle).blockingGetOrNull()
            }

            check(name, parentNode).blockingSubscribeBy(
                onError = { error -> emitter.onError(error) },
                onSuccess = { handle -> emitter.onSuccess(handle) }
            )
        }

    /**
     * Checks if a node with the given name exists on the provided parent node.
     *
     * @param name          Name of the node.
     * @param parentNode    Parent node in which to look.
     * @return Single Long with the node handle with which there is a name collision.
     */
    fun check(name: String, parentNode: MegaNode?): Single<Long> =
        Single.create { emitter ->
            if (parentNode == null) {
                emitter.onError(MegaNodeException.ParentDoesNotExistException())
                return@create
            }

            val child = megaApi.getChildNode(parentNode, name)

            if (child != null) {
                emitter.onSuccess(child.handle)
            } else {
                emitter.onError(MegaNodeException.ChildDoesNotExistsException())
            }
        }

    /**
     * Checks a list of handles in order to know which names already exist on the parent nodes.
     *
     * @param nodes         List of node handles to check.
     * @param parentHandle  Parent handle node in which to look.
     * @return Single<Pair<List<NameCollision>, LongArray>> containing:
     *  - First:    List of NameCollision with name collisions.
     *  - Second:   Array of handles without name collision.
     */
    fun checkNodeList(
        nodes: List<MegaNode>,
        parentHandle: Long,
        type: NameCollisionType
    ): Single<Pair<ArrayList<NameCollision>, List<MegaNode>>> =
        Single.create { emitter ->
            if (nodes.isEmpty()) {
                emitter.onError(NoPendingCollisionsException())
                return@create
            }

            val collisions = ArrayList<NameCollision>()
            val results = mutableListOf<MegaNode>()

            nodes.forEach { node ->
                if (emitter.isDisposed) {
                    return@create
                }

                check(node, parentHandle, type).blockingSubscribeBy(
                    onError = { error ->
                        logError("No collision.", error)
                        results.add(node)
                    },
                    onSuccess = { collision -> collisions.add(collision) }
                )
            }

            when {
                emitter.isDisposed -> return@create
                else -> emitter.onSuccess(Pair(collisions, results))
            }
        }

    /**
     * Checks a list of handles in order to know which names already exist on the parent nodes.
     *
     * @param handles       List of node handles to check.
     * @param parentHandle  Parent handle node in which to look.
     * @return Single<Pair<List<NameCollision>, LongArray>> containing:
     *  - First:    List of NameCollision with name collisions.
     *  - Second:   Array of handles without name collision.
     */
    fun checkHandleList(
        handles: LongArray,
        parentHandle: Long,
        type: NameCollisionType
    ): Single<Pair<ArrayList<NameCollision>, LongArray>> =
        Single.create { emitter ->
            if (handles.isEmpty()) {
                emitter.onError(NoPendingCollisionsException())
                return@create
            }

            val collisions = ArrayList<NameCollision>()
            val results = mutableListOf<Long>()

            handles.forEach { handle ->
                if (emitter.isDisposed) {
                    return@create
                }

                check(handle, parentHandle, type).blockingSubscribeBy(
                    onError = { error ->
                        logError("No collision.", error)
                        results.add(handle)
                    },
                    onSuccess = { collision -> collisions.add(collision) }
                )
            }

            when {
                emitter.isDisposed -> return@create
                else -> emitter.onSuccess(Pair(collisions, results.toLongArray()))
            }
        }

    /**
     * Checks a list of MegaNode in order to know which names already exist on the parent nodes
     * in which them will be restored.
     *
     * @param nodes List of nodes to check.
     * @return Single<Pair<List<NameCollision>, List<MegaNode>>> containing:
     *  - First:    List of NameCollision with name collisions.
     *  - Second:   List of MegaNode without name collision.
     */
    fun checkRestorations(nodes: List<MegaNode>): Single<Pair<ArrayList<NameCollision>, List<MegaNode>>> =
        Single.create { emitter ->
            if (nodes.isEmpty()) {
                emitter.onError(NoPendingCollisionsException())
                return@create
            }

            val collisions = ArrayList<NameCollision>()
            val results = mutableListOf<MegaNode>()

            nodes.forEach { node ->
                if (emitter.isDisposed) {
                    return@create
                }

                val restoreHandle = node.restoreHandle
                val parent = getNodeUseCase.get(restoreHandle).blockingGetOrNull()

                if (parent != null && !megaApi.isInRubbish(parent)) {
                    check(node.name, parent).blockingSubscribeBy(
                        onError = { error ->
                            logError("No collision.", error)
                            results.add(node)
                        },
                        onSuccess = { handle ->
                            collisions.add(
                                NameCollision.Movement.getMovementCollision(
                                    handle,
                                    node,
                                    restoreHandle
                                )
                            )
                        }
                    )
                }
            }

            when {
                emitter.isDisposed -> return@create
                else -> emitter.onSuccess(Pair(collisions, results))
            }
        }

    /**
     * Checks a list of ShareInfo in order to know which names already exist
     * on the provided parent node.
     *
     * @param shareInfos    List of ShareInfo to check.
     * @param parentNode    Parent node in which to look.
     * @return Single<Pair<List<NameCollision>, List<ShareInfo>>> containing:
     *  - First:    List of NameCollision with name collisions.
     *  - Second:   List of ShareInfo without name collision.
     */
    fun checkShareInfoList(
        shareInfos: List<ShareInfo>,
        parentNode: MegaNode?
    ): Single<Pair<ArrayList<NameCollision>, List<ShareInfo>>> =
        Single.create { emitter ->
            if (parentNode == null) {
                emitter.onError(MegaNodeException.ParentDoesNotExistException())
                return@create
            }

            val collisions = ArrayList<NameCollision>()
            val results = ArrayList<ShareInfo>()

            for (shareInfo in shareInfos) {
                check(shareInfo.originalFileName, parentNode).blockingSubscribeBy(
                    onError = { error ->
                        if (error is MegaNodeException.ParentDoesNotExistException) {
                            emitter.onError(error)
                            return@blockingSubscribeBy
                        } else {
                            results.add(shareInfo)
                        }
                    },
                    onSuccess = { handle ->
                        collisions.add(
                            NameCollision.Upload.getUploadCollision(
                                handle,
                                shareInfo,
                                parentNode.handle
                            )
                        )
                    },
                )
            }

            emitter.onSuccess(Pair(collisions, results))
        }

    /**
     * Checks a list of [FolderContent.Data] in order to know which names already exist
     * on the provided parent node.
     *
     * @param parentHandle  Parent handle of the MegaNode in which the content will be uploaded.
     * @param uploadContent List of [FolderContent.Data] to check.
     * @return Single with the list of collisions if any and the list of [FolderContent.Data]
     * updated with them.
     */
    fun checkFolderUploadList(
        parentHandle: Long,
        uploadContent: MutableList<FolderContent.Data>
    ): Single<Pair<ArrayList<NameCollision>, MutableList<FolderContent.Data>>> =
        Single.create { emitter ->
            if (uploadContent.isEmpty()) {
                emitter.onError(EmptyFolderException())
                return@create
            }

            val collisions = ArrayList<NameCollision>()

            uploadContent.forEach { item ->
                if (emitter.isDisposed) {
                    return@create
                }

                item.name?.let {
                    check(it, parentHandle).blockingSubscribeBy(
                        onError = { error ->
                            if (error is MegaNodeException.ParentDoesNotExistException) {
                                emitter.onError(error)
                                return@blockingSubscribeBy
                            }
                        },
                        onSuccess = { handle ->
                            val collision = NameCollision.Upload.getUploadCollision(
                                handle,
                                item,
                                parentHandle
                            )

                            collisions.add(collision)
                            item.nameCollision = collision
                        },
                    )
                }
            }

            when {
                emitter.isDisposed -> return@create
                else -> emitter.onSuccess(Pair(collisions, uploadContent))
            }
        }
}