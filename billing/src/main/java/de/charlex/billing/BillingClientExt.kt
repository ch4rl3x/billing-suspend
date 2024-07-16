package de.charlex.billing

import android.util.Log
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

fun BillingClient.onConnected(block: () -> Unit) {
    startConnection(object : BillingClientStateListener {
        override fun onBillingSetupFinished(billingResult: BillingResult) {
            block()
        }
        override fun onBillingServiceDisconnected() {
            // Try to restart the connection on the next request to
            // Google Play by calling the startConnection() method.
        }
    })
}

/**
 * Closes the connection and releases all held resources such as service connections.
 *
 * Call this method once you are done with this BillingClient reference.
 */
suspend fun BillingClient.endConnection() = withContext(Dispatchers.Main) {
    Log.d("BillingHelper", "The billing client is still ready")
    endConnection()
}
