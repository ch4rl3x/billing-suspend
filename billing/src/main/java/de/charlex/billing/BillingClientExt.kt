package de.charlex.billing

import android.util.Log
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

/**
 * Starts up BillingClient setup process suspended if necessary.
 *
 * @return Boolean
 *
 * true: The billing client is ready. You can query purchases.
 *
 * false: The billing client is NOT ready or disconnected.
 */
suspend fun BillingClient.startConnectionIfNecessary() = suspendCancellableCoroutine<Boolean> { continuation ->
    if (!isReady) {
        startConnection(object : BillingClientStateListener {
            override fun onBillingServiceDisconnected() {
                Log.d("BillingHelper", "The billing client is disconnected.")
                if (continuation.isActive) {
                    continuation.resume(false)
                }
            }

            override fun onBillingSetupFinished(billingResult: BillingResult) {
                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    Log.d("BillingHelper", "The billing client is ready. You can query purchases.")
                    if (continuation.isActive) {
                        continuation.resume(true)
                    }
                } else {
                    Log.d("BillingHelper", "The billing client is NOT ready. ${billingResult.debugMessage}")
                    if (continuation.isActive) {
                        continuation.resume(false)
                    }
                }
            }
        })
    } else {
        Log.d("BillingHelper", "The billing client is still ready")
        if (continuation.isActive) {
            continuation.resume(true)
        }
    }
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
