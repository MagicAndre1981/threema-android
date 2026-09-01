package ch.threema.app.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.lifecycleScope
import ch.threema.app.R
import ch.threema.app.activities.RecipientListBaseActivity
import ch.threema.app.adapters.RecentListAdapter
import ch.threema.app.di.awaitAppFullyReady
import ch.threema.app.preference.service.PreferenceService
import ch.threema.app.services.ContactService
import ch.threema.app.services.ConversationService
import ch.threema.app.services.DistributionListService
import ch.threema.app.services.GroupService
import ch.threema.common.DispatcherProvider
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.android.ext.android.inject

class RecentListFragment : RecipientListFragment() {
    private val preferenceService: PreferenceService by inject()
    private val conversationService: ConversationService by inject()
    private val contactService: ContactService by inject()
    private val groupService: GroupService by inject()
    private val distributionListService: DistributionListService by inject()
    private val dispatcherProvider: DispatcherProvider by inject()

    private var showDistributionLists = false

    override fun isMultiSelectAllowed() = multiSelect

    override fun getBundleName() = "RecentListState"

    override fun getEmptyText() = R.string.no_recent_conversations

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View? {
        showDistributionLists = (getActivity() as RecipientListBaseActivity).showDistributionLists
        return super.onCreateView(inflater, container, savedInstanceState)
    }

    override fun createListAdapter(checkedItemPositions: ArrayList<Int>?) {
        lifecycleScope.launch {
            awaitAppFullyReady()

            val filter: ConversationService.Filter = object : ConversationService.Filter {
                override fun noDistributionLists(): Boolean =
                    !showDistributionLists

                override fun noHiddenConversations(): Boolean =
                    preferenceService.arePrivateChatsHidden()

                override fun noInvalid() = true
            }

            val conversations = withContext(dispatcherProvider.io) {
                conversationService.getAll(false, filter)
            }

            adapter = RecentListAdapter(
                activity,
                conversations,
                checkedItemPositions,
                contactService,
                groupService,
                distributionListService,
                preferenceService,
                this@RecentListFragment,
            )
            setListAdapter(adapter)

            if (listInstanceState != null) {
                if (isAdded && view != null && getActivity() != null) {
                    listView.onRestoreInstanceState(listInstanceState)
                }
                listInstanceState = null
                restoreCheckedItems(checkedItemPositions)
            }
        }
    }
}
