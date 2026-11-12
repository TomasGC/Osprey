package app.osprey.android

import android.os.Bundle
import android.util.Log
import androidx.test.runner.AndroidJUnitRunner
import app.osprey.android.filesystem.SafGrant

private const val TAG = "OspreyRunner"

/** The test runner: when the run ends, it removes the folder and grant the picker helper made on the device. */
class OspreyInstrumentationRunner : AndroidJUnitRunner() {
    override fun finish(resultCode: Int, results: Bundle?) {
        try {
            SafGrant.cleanUp(targetContext)
        } catch (e: SecurityException) {
            // The results are what the run is for: a cleanup that fails is logged and the next picker flow retries it.
            Log.w(TAG, "could not clean up the picker helper's folder and grant", e)
        }
        super.finish(resultCode, results)
    }
}
