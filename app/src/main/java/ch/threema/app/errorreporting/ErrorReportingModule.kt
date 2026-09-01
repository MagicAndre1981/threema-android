package ch.threema.app.errorreporting

import android.os.Build
import ch.threema.app.BuildConfig
import ch.threema.app.BuildFlavor
import ch.threema.logging.BaseErrorRecordStore
import org.koin.core.module.dsl.factoryOf
import org.koin.dsl.bind
import org.koin.dsl.module

val errorReportingModule = module {
    factoryOf(::ErrorReportingHelper)
    factoryOf(::RecentErrorTypeIdStore)
    factoryOf(::ErrorReportDetailsProvider)
    factoryOf(SendErrorReportWorker::Scheduler)
    single<ErrorRecordStore> {
        ErrorRecordStoreImpl.create(
            context = get(),
            timeProvider = get(),
            uuidGenerator = get(),
        )
    } bind BaseErrorRecordStore::class
    factoryOf(::SentryService)
    factoryOf(::SentryIdProvider)
    factory {
        SentryService.Config(
            host = BuildConfig.SENTRY_HOST,
            projectId = BuildConfig.SENTRY_PROJECT_ID,
            publicApiKey = BuildConfig.SENTRY_PUBLIC_API_KEY,
        )
    }
    factory {
        SentryService.MetaInfo(
            androidSdkVersion = Build.VERSION.SDK_INT,
            release = "threema-android@${BuildConfig.BASE_VERSION_NAME}${BuildConfig.VERSION_NAME_BETA_SUFFIX}",
            versionCode = BuildConfig.DEFAULT_VERSION_CODE,
            buildFlavor = BuildFlavor.current.fullDisplayName,
            deviceModel = Build.MODEL,
        )
    }
}
