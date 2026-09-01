package ch.threema.app.shortcuts

import android.os.DeadObjectException
import ch.threema.app.di.injectNullableNonBinding
import ch.threema.app.eventbus.GlobalEventFlows
import ch.threema.app.eventbus.events.ContactEvent
import ch.threema.app.eventbus.events.DistributionListEvent
import ch.threema.app.eventbus.events.GroupEvent
import ch.threema.app.messagereceiver.MessageReceiver
import ch.threema.app.monitors.Monitor
import ch.threema.app.preference.service.PreferenceService
import ch.threema.app.services.ContactService
import ch.threema.app.services.DistributionListService
import ch.threema.app.services.GroupService
import ch.threema.app.utils.ShortcutUtil
import ch.threema.base.utils.getThreemaLogger
import ch.threema.data.datatypes.GroupIdentity
import ch.threema.domain.types.Identity
import ch.threema.storage.models.AbstractMessageModel
import ch.threema.storage.models.DistributionListModel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent

private val logger = getThreemaLogger("ShortcutsUpdaterMonitor")

// TODO(ANDR-4705): Refactor or wrap ShortcutUtil to make it injectable and ensure that updatePinnedShortcut is only called from here.
//  Once that is done, we should write unit tests for this class.
class ShortcutsUpdaterMonitor(
    private val globalEventFlows: GlobalEventFlows,
    private val preferenceService: PreferenceService,
) : Monitor("ShortcutsUpdaterMonitor"), KoinComponent {
    private val contactService: ContactService? by injectNullableNonBinding()
    private val groupService: GroupService? by injectNullableNonBinding()
    private val distributionListService: DistributionListService? by injectNullableNonBinding()

    override suspend fun run(): Unit = coroutineScope {
        launch {
            globalEventFlows.contacts.collect { event ->
                when (event) {
                    is ContactEvent.ContactProfilePictureUpdated,
                    is ContactEvent.ContactUpdated,
                    -> onContactUpdated(event.identity)
                    is ContactEvent.ContactRemoved,
                    is ContactEvent.NewContact,
                    -> Unit
                }
            }
        }
        launch {
            globalEventFlows.groups.collect { event ->
                when (event) {
                    is GroupEvent.GroupRenamed -> onGroupUpdated(event.groupIdentity)
                    is GroupEvent.GroupProfilePictureUpdated -> onGroupUpdated(event.groupIdentity)
                    is GroupEvent.NewMember -> onGroupMembersChanged(event.groupIdentity)
                    is GroupEvent.MemberKicked -> onGroupMembersChanged(event.groupIdentity)
                    is GroupEvent.MemberLeft -> onGroupMembersChanged(event.groupIdentity)
                    is GroupEvent.GroupStateChanged,
                    is GroupEvent.GroupRemoved,
                    is GroupEvent.GroupUpdated,
                    is GroupEvent.NewGroup,
                    is GroupEvent.UserLeftGroup,
                    -> Unit
                }
            }
        }
        launch {
            globalEventFlows.distributionLists.collect { event ->
                when (event) {
                    is DistributionListEvent.DistributionListUpdated -> onDistributionListUpdated(event.distributionList)
                    is DistributionListEvent.NewDistributionList,
                    is DistributionListEvent.DistributionListRemoved,
                    -> Unit
                }
            }
        }
    }

    private fun onContactUpdated(identity: Identity) {
        val receiver = contactService?.createReceiver(identity.value) ?: return
        updatePinnedShortcuts(receiver)
        updateShareTargetShortcuts(receiver)
    }

    private fun onGroupUpdated(groupIdentity: GroupIdentity) {
        val messageReceiver = groupService?.createReceiver(groupIdentity) ?: return
        updatePinnedShortcuts(messageReceiver)
    }

    private fun onGroupMembersChanged(groupIdentity: GroupIdentity) {
        val group = groupService?.getByGroupIdentity(groupIdentity) ?: return
        // When a group has no name, a fallback name is derived from the members' names.
        // Therefore, if the members change, we also need to update the shortcuts for that group.
        if (group.name.isNullOrEmpty()) {
            onGroupUpdated(groupIdentity)
        }
    }

    private fun onDistributionListUpdated(distributionList: DistributionListModel) {
        val distributionListService = distributionListService ?: return
        val receiver = distributionListService.createReceiver(distributionList)
        updatePinnedShortcuts(receiver)
    }

    private fun updateShareTargetShortcuts(
        receiver: MessageReceiver<out AbstractMessageModel>,
    ) {
        try {
            ShortcutUtil.updateShareTargetShortcut(
                receiver,
                preferenceService.getContactNameFormat(),
            )
        } catch (e: DeadObjectException) {
            // DeadObjectException is expected to occur occasionally and there is nothing we can do about it
            logger.warn("Failed to update shortcuts", e)
        }
    }

    private fun updatePinnedShortcuts(
        receiver: MessageReceiver<out AbstractMessageModel>,
    ) {
        ShortcutUtil.updatePinnedShortcut(
            receiver,
            preferenceService.getContactNameFormat(),
        )
    }
}
