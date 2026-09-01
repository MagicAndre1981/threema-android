package ch.threema.app.errorreporting

import android.content.Context
import android.os.DeadObjectException
import ch.threema.app.BuildConfig
import ch.threema.common.TimeProvider
import ch.threema.common.UUIDGenerator
import ch.threema.logging.UncaughtExceptionsLogger

class ThreemaUncaughtExceptionHandler(
    private val appContext: Context,
) : Thread.UncaughtExceptionHandler {
    private val defaultHandler: Thread.UncaughtExceptionHandler? = Thread.getDefaultUncaughtExceptionHandler()

    override fun uncaughtException(t: Thread, e: Throwable) {
        UncaughtExceptionsLogger.logUnhandledException(e)

        if (shouldCreateErrorReport(e)) {
            storeExceptionForErrorReporting(e)
        }

        defaultHandler?.uncaughtException(t, e)
    }

    private fun shouldCreateErrorReport(e: Throwable): Boolean {
        if (!BuildConfig.ERROR_REPORTING_SUPPORTED) {
            return false
        }
        return e !is DeadObjectException && e.cause !is DeadObjectException
    }

    private fun storeExceptionForErrorReporting(e: Throwable) {
        // Intentionally not using Koin here, as it might not be initialized yet at this point
        ErrorRecordStoreImpl.create(
            context = appContext,
            timeProvider = TimeProvider.default,
            uuidGenerator = UUIDGenerator.default,
        )
            .storeFatalError(e)
    }
}
