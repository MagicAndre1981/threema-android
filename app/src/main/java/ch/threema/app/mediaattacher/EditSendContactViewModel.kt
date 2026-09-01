package ch.threema.app.mediaattacher

import android.content.Context
import android.net.Uri
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ch.threema.app.files.AppDirectoryProvider
import ch.threema.app.utils.FileUtil
import ch.threema.app.utils.VCardExtractor
import ch.threema.base.utils.getThreemaLogger
import ch.threema.common.DispatcherProvider
import ch.threema.common.takeUnlessEmpty
import ezvcard.Ezvcard
import ezvcard.VCard
import ezvcard.property.FormattedName
import ezvcard.property.StructuredName
import ezvcard.property.VCardProperty
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val logger = getThreemaLogger("EditSendContactViewModel")

/**
 * Contains the data needed in the EditSendContactActivity.
 */
class EditSendContactViewModel(
    private val appContext: Context,
    private val vCardExtractor: VCardExtractor,
    private val appDirectoryProvider: AppDirectoryProvider,
    private val dispatcherProvider: DispatcherProvider,
) : ViewModel() {
    /* The currently shown formatted name in the edit texts */
    private val formattedName: MutableLiveData<FormattedName> = MutableLiveData()

    /* The currently shown structured name in the edit texts */
    private val structuredName: MutableLiveData<StructuredName> = MutableLiveData()

    /* The properties (except the name properties) */
    private val properties: MutableLiveData<MutableMap<VCardProperty, Boolean>> = MutableLiveData()

    /* The modified contact that should be sent */
    private val modifiedContact: MutableLiveData<Pair<String, File>> = MutableLiveData()

    /* The state of the bottom sheet */
    var bottomSheetExpanded: Boolean = false

    /**
     * Get formatted name live data
     */
    fun getFormattedName(): LiveData<FormattedName> = formattedName

    /**
     * Get structured name live data
     */
    fun getStructuredName(): LiveData<StructuredName> = structuredName

    /**
     * Get property live data
     */
    fun getProperties(): LiveData<MutableMap<VCardProperty, Boolean>> = properties

    /**
     * Get the modified contact (ready to be sent)
     * @return a pair with the name of the contact and a vCard file
     */
    fun getModifiedContact(): LiveData<Pair<String, File>> = modifiedContact

    /**
     * Initializes the view model based on the given contact uri.
     */
    fun initializeContact(contactUri: Uri) {
        if (properties.value != null) {
            return
        }

        viewModelScope.launch {
            val vCard = readVCard(contactUri)

            if (vCard == null) {
                logger.warn("vCard was null")
                structuredName.postValue(StructuredName())
            } else if (createFormattedName(vCard) == null && !vCard.formattedName?.value.isNullOrEmpty()) {
                formattedName.postValue(vCard.formattedName)
            } else {
                structuredName.postValue(vCard.structuredName ?: StructuredName())
            }

            properties.postValue(
                vCard?.properties?.associateWith { true }?.toMutableMap() ?: mutableMapOf(),
            )
        }
    }

    private suspend fun readVCard(contactUri: Uri): VCard? = withContext(dispatcherProvider.io) {
        appContext.contentResolver.openInputStream(contactUri)
            .use { inputStream ->
                BufferedReader(InputStreamReader(inputStream)).useLines { l -> l.joinToString("\n") }
            }
            .let { vCardString ->
                Ezvcard.parse(vCardString).first()
            }
    }

    /**
     * Get the formatted name and the vCard as file containing the selected properties.
     */
    fun prepareFinalVCard(contactUri: Uri) {
        viewModelScope.launch(dispatcherProvider.io) {
            val vCard = VCard()
            if (structuredName.value != null) {
                vCard.setProperty(structuredName.value)
                vCard.setProperty(
                    FormattedName(
                        createFormattedName(vCard) ?: "",
                    ),
                )
            } else if (formattedName.value != null) {
                vCard.setProperty(formattedName.value)
            }

            // Add selected properties to the vcard
            properties.value?.filter { it.value }?.map { it.key }?.forEach {
                vCard.addProperty(it)
            }

            val mimeType = FileUtil.getMimeTypeFromUri(appContext, contactUri)
            val modifiedContactFile = File(appDirectoryProvider.cacheDirectory, FileUtil.getDefaultFilename(mimeType))

            val writer = Ezvcard.write(vCard).prodId(false)
            writer.go(modifiedContactFile)

            modifiedContact.postValue((vCard.formattedName?.value ?: "") to modifiedContactFile)
        }
    }

    /**
     * Create the formatted name (FN) based on the structured name (N).
     */
    private fun createFormattedName(vcard: VCard): String? =
        vcard.structuredName
            ?.let { structuredName ->
                try {
                    vCardExtractor.getText(structuredName, false)
                        .trim()
                        .takeUnlessEmpty()
                } catch (e: Exception) {
                    if (e !is VCardExtractor.VCardExtractionException) {
                        logger.error("Could not extract name of contact", e)
                    }
                    null
                }
            }
}
