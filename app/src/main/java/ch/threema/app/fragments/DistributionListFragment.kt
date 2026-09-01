package ch.threema.app.fragments

import androidx.lifecycle.lifecycleScope
import ch.threema.app.R
import ch.threema.app.activities.DistributionListAddActivity
import ch.threema.app.adapters.DistributionListAdapter
import ch.threema.app.di.awaitAppFullyReady
import ch.threema.app.preference.service.PreferenceService
import ch.threema.app.services.DistributionListService
import ch.threema.common.DispatcherProvider
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.android.ext.android.inject

class DistributionListFragment : RecipientListFragment() {
    private val distributionListService: DistributionListService by inject()
    private val preferenceService: PreferenceService by inject()
    private val dispatcherProvider: DispatcherProvider by inject()

    override fun isMultiSelectAllowed() = false

    override fun getBundleName() = "DistListState"

    override fun getEmptyText() = R.string.no_matching_distribution_lists

    override fun getAddIcon() = R.drawable.ic_distribution_list

    override fun getAddText() = R.string.title_add_distribution_list

    override fun getAddIntent() = DistributionListAddActivity.createIntent(requireContext())

    override fun createListAdapter(checkedItemPositions: ArrayList<Int>?) {
        lifecycleScope.launch {
            awaitAppFullyReady()

            val distributionListModels = withContext(dispatcherProvider.io) {
                distributionListService.getAll(
                    object : DistributionListService.DistributionListFilter {
                        override fun sortingByDate() = true

                        override fun sortingAscending() = false

                        override fun showHidden() = false
                    },
                )
            }
            adapter = DistributionListAdapter(
                activity,
                distributionListModels,
                checkedItemPositions,
                distributionListService,
                preferenceService,
                this@DistributionListFragment,
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
