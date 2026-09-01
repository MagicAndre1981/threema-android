package ch.threema.app.mediaattacher

import android.content.Context
import android.text.format.DateFormat
import ch.threema.app.utils.VCardExtractor
import org.koin.core.module.dsl.viewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val mediaAttacherFeatureModule = module {
    viewModel<EditSendContactViewModel> {
        val appContext = get<Context>()
        EditSendContactViewModel(
            appContext = appContext,
            appDirectoryProvider = get(),
            vCardExtractor = VCardExtractor(DateFormat.getDateFormat(appContext), appContext.resources),
            dispatcherProvider = get(),
        )
    }
    viewModelOf(::MediaAttachViewModel)
}
