package ch.threema.app.notifications

import android.content.Context
import ch.threema.app.di.injectNullableNonBinding
import ch.threema.app.preference.service.PreferenceService
import ch.threema.app.services.ContactService
import ch.threema.app.services.ConversationCategoryService
import ch.threema.app.services.GroupService
import ch.threema.app.services.notification.ConversationNotification
import ch.threema.app.utils.ConversationNotificationUtil
import ch.threema.storage.models.AbstractMessageModel
import org.koin.core.component.KoinComponent

// TODO(ANDR-4691): This class needs refactoring
class ConversationNotificationConverter(
    private val appContext: Context,
    private val preferenceService: PreferenceService,
) : KoinComponent {
    private val contactService: ContactService? by injectNullableNonBinding()
    private val groupService: GroupService? by injectNullableNonBinding()
    private val conversationCategoryService: ConversationCategoryService? by injectNullableNonBinding()

    fun convert(message: AbstractMessageModel): ConversationNotification? =
        ConversationNotificationUtil.convert(
            appContext,
            message,
            contactService ?: return null,
            groupService ?: return null,
            conversationCategoryService ?: return null,
            preferenceService.getContactNameFormat(),
        )
}
