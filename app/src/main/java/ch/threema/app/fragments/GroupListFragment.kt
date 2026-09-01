package ch.threema.app.fragments

import androidx.lifecycle.lifecycleScope
import ch.threema.app.R
import ch.threema.app.activities.GroupAddActivity
import ch.threema.app.adapters.GroupListAdapter
import ch.threema.app.di.awaitAppFullyReady
import ch.threema.app.preference.service.PreferenceService
import ch.threema.app.services.GroupService
import ch.threema.app.services.GroupService.GroupFilter
import ch.threema.common.DispatcherProvider
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.android.ext.android.inject

class GroupListFragment : RecipientListFragment() {
    private val groupService: GroupService by inject()
    private val preferenceService: PreferenceService by inject()
    private val dispatcherProvider: DispatcherProvider by inject()

    override fun isMultiSelectAllowed() = multiSelect

    override fun getBundleName() = "GroupListState"

    override fun getEmptyText() = R.string.no_matching_groups

    override fun getAddIcon() = R.drawable.ic_group_outline

    override fun getAddText() = R.string.title_addgroup

    override fun getAddIntent() = GroupAddActivity.createIntent(requireContext())

    override fun createListAdapter(checkedItemPositions: ArrayList<Int>?) {
        lifecycleScope.launch {
            awaitAppFullyReady()

            val groupModels = withContext(dispatcherProvider.io) {
                groupService.getAll(
                    object : GroupFilter {
                        override fun sortByDate() = false

                        override fun sortByName() = true

                        override fun sortAscending() = true

                        override fun includeLeftGroups() = false
                    },
                )
            }
            adapter = GroupListAdapter(
                activity,
                groupModels,
                checkedItemPositions,
                groupService,
                preferenceService,
                this@GroupListFragment,
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
