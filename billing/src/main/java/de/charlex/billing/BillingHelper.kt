package de.charlex.billing

import android.app.Activity
import android.util.Log
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.ConsumeParams
import com.android.billingclient.api.ConsumeResult
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesResult
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.SkuDetails
import com.android.billingclient.api.SkuDetailsParams
import com.android.billingclient.api.acknowledgePurchase
import com.android.billingclient.api.consumePurchase
import com.android.billingclient.api.queryPurchasesAsync
import com.android.billingclient.api.querySkuDetails

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

/**
 * Helps to make all necessary functions from billingClient suspendable
 */
class BillingHelper(private val activity: Activity, billingClientBuilder: BillingClient.Builder.() -> Unit) : PurchasesUpdatedListener {

    private var billingClient: BillingClient

    init {
        val builder = BillingClient.newBuilder(activity)
        billingClientBuilder.invoke(builder)
        builder.setListener(this)
        billingClient = builder.build()
    }

    private var billingContinuation: Continuation<PurchasesResult>? = null
    private var validation: (Purchase) -> Boolean = { true }

    /**
     * Starts up BillingClient setup process suspended if necessary.
     *
     * @return Boolean
     *
     * true: The billing client is ready. You can query purchases.
     *
     * false: The billing client is NOT ready or disconnected.
     */
    private suspend fun startConnectionIfNecessary() = suspendCancellableCoroutine<Boolean> { continuation ->
        if (!billingClient.isReady) {
            billingClient.startConnection(object : BillingClientStateListener {
                override fun onBillingServiceDisconnected() {
                    Log.d("BillingHelper", "The billing client is disconnected.")
                    if (continuation.isActive) {
                        continuation.resume(false)
                    }
                }

                override fun onBillingSetupFinished(billingResult: BillingResult) {
                    if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                        Log.d("BillingHelper", "The billing client is ready. You can query purchases.")
                        continuation.resume(true)
                    } else {
                        Log.d("BillingHelper", "The billing client is NOT ready. ${billingResult.debugMessage}")
                        continuation.resume(false)
                    }
                }
            })
        } else {
            Log.d("BillingHelper", "The billing client is still ready")
            continuation.resume(true)
        }
    }

    /**
     * Closes the connection and releases all held resources such as service connections.
     *
     * Call this method once you are done with this BillingClient reference.
     */
    suspend fun endConnection() = withContext(Dispatchers.Main) {
        Log.d("BillingHelper", "The billing client is still ready")
        billingClient.endConnection()
    }

    /**
     * Acknowledges in-app purchases.
     *
     * Developers are required to acknowledge that they have granted entitlement for all in-app purchases for their application.
     *
     * **Warning!** All purchases require acknowledgement.
     * Failure to acknowledge a purchase will result in that purchase being refunded.
     *
     * For one-time products ensure you are using consume which acts
     * as an implicit acknowledgement or you can explicitly acknowledge the purchase via this method.
     *
     * For subscriptions use acknowledgePurchase(String).
     *
     * @param purchaseToken String Specify the token that identifies the purchase to be acknowledged.
     *
     * @return [BillingResult](https://developer.android.com/reference/com/android/billingclient/api/BillingResult) Result of the acknowledge operation
     *
     */
    suspend fun acknowledgePurchase(purchaseToken: String): BillingResult? = withContext(Dispatchers.IO) {
        val acknowledgePurchaseParams = AcknowledgePurchaseParams.newBuilder().setPurchaseToken(purchaseToken).build()
        return@withContext if (startConnectionIfNecessary()) {
            val result = billingClient.acknowledgePurchase(acknowledgePurchaseParams)
            if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                Log.d("BillingHelper", "Purchase acknowleged! (Token: $purchaseToken)")
            } else {
                Log.d("BillingHelper", "Purchase acknowlege: ${translateBillingResponseCodeToLogString(result.responseCode)}")
            }
            result
        } else {
            null
        }
    }

    /**
     * Consumes a given in-app product. Consuming can only be done on an item that's owned, and as a result of consumption, the user will no longer own it.
     *
     * **Warning!** All purchases require acknowledgement.
     * Failure to acknowledge a purchase will result in that purchase being refunded.
     *
     * For one-time products ensure you are using this method which acts
     * as an implicit acknowledgement or you can explicitly acknowledge the purchase via acknowledgePurchase(String).
     *
     * For subscriptions use acknowledgePurchase(String).
     *
     * @param purchaseToken String Specifies the token that identifies the purchase to be consumed.
     *
     * @return [BillingResult](https://developer.android.com/reference/com/android/billingclient/api/BillingResult) Result of the consume operation. [ITEM_NOT_OWNED](https://developer.android.com/reference/com/android/billingclient/api/BillingClient.BillingResponseCode#ITEM_NOT_OWNED) if the user does not currently own the item.
     */
    suspend fun consume(purchaseToken: String): ConsumeResult? = withContext(Dispatchers.IO) {
        val consumeParams = ConsumeParams.newBuilder().setPurchaseToken(purchaseToken).build()
        return@withContext if (startConnectionIfNecessary()) {
            billingClient.consumePurchase(consumeParams)
        } else {
            null
        }
    }

    /**
     * Returns purchases details for currently owned items bought within your app.
     * Only active subscriptions and non-consumed one-time purchases are returned.
     * This method uses a cache of Google Play Store app without initiating a network request.
     *
     * Note: It's recommended for security purposes to go through purchases verification on your backend (if you have one)
     * by calling one of the following APIs:
     *
     * https://developers.google.com/android-publisher/api-ref/purchases/products/get
     *
     * https://developers.google.com/android-publisher/api-ref/purchases/subscriptions/get
     *
     * @param skuType String The type of SKU, either "inapp" or "subs" as in [BillingClient.SkuType](https://developer.android.com/reference/com/android/billingclient/api/BillingClient.SkuType).
     *
     * @return [Purchase.PurchasesResult](https://developer.android.com/reference/com/android/billingclient/api/Purchase.PurchasesResult) The Purchase.PurchasesResult containing the list of purchases and the response code ([BillingClient.BillingResponseCode](https://developer.android.com/reference/com/android/billingclient/api/BillingClient.BillingResponseCode))
     */
    suspend fun queryPurchases(skuType: String): PurchasesResult? = withContext(Dispatchers.IO) {
        Log.d("BillingHelper", "queryPurchases")
        return@withContext if (startConnectionIfNecessary()) {
            Log.d("BillingHelper", "queryPurchases on billingClient")
            billingClient.queryPurchasesAsync(skuType)
        } else {
            null
        }
    }

    /**
     * For logging. Translates a given [responseCode](BillingClient.BillingResponseCode) to a readable string
     *
     * @param responseCode Int? The responseCode
     *
     * @return String A readable responseCode
     */
    private fun translateBillingResponseCodeToLogString(responseCode: Int?): String {
        return when (responseCode) {
            BillingClient.BillingResponseCode.SERVICE_TIMEOUT -> {
                "BillingClient.BillingResponseCode.SERVICE_TIMEOUT"
            }
            BillingClient.BillingResponseCode.FEATURE_NOT_SUPPORTED -> {
                "BillingClient.BillingResponseCode.FEATURE_NOT_SUPPORTED"
            }
            BillingClient.BillingResponseCode.SERVICE_DISCONNECTED -> {
                "BillingClient.BillingResponseCode.SERVICE_DISCONNECTED"
            }
            BillingClient.BillingResponseCode.USER_CANCELED -> {
                "BillingClient.BillingResponseCode.USER_CANCELED"
            }
            BillingClient.BillingResponseCode.SERVICE_UNAVAILABLE -> {
                "BillingClient.BillingResponseCode.SERVICE_UNAVAILABLE"
            }
            BillingClient.BillingResponseCode.BILLING_UNAVAILABLE -> {
                "BillingClient.BillingResponseCode.BILLING_UNAVAILABLE"
            }
            BillingClient.BillingResponseCode.ITEM_UNAVAILABLE -> {
                "BillingClient.BillingResponseCode.ITEM_UNAVAILABLE"
            }
            BillingClient.BillingResponseCode.DEVELOPER_ERROR -> {
                "BillingClient.BillingResponseCode.DEVELOPER_ERROR"
            }
            BillingClient.BillingResponseCode.ERROR -> {
                "BillingClient.BillingResponseCode.ERROR"
            }
            BillingClient.BillingResponseCode.ITEM_NOT_OWNED -> {
                "BillingClient.BillingResponseCode.ITEM_NOT_OWNED"
            }
            BillingClient.BillingResponseCode.OK -> {
                "BillingClient.BillingResponseCode.OK"
            }
            BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED -> {
                "BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED"
            }
            else -> {
                "Unknown BillingResponseCode: $responseCode"
            }
        }
    }

    /**
     * Performs a network query purchase SKUs
     *
     * @param sku String[] Specifies the SKUs to be purchased.
     * @param type String Specifies the [BillingClient.SkuType](https://developer.android.com/reference/com/android/billingclient/api/BillingClient.SkuType) of SKUs to query.
     *
     */
    suspend fun purchase(sku: String, type: String, validation: (Purchase) -> Boolean = { true }): PurchasesResult? {
        if (startConnectionIfNecessary()) {
            val skuDetails: SkuDetails? = querySkuDetails(sku, type)
            skuDetails?.let {
                Log.d("BillingHelper", it.toString())

                val purchaseResult = suspendCoroutine<PurchasesResult?> { continuation ->
                    billingContinuation = continuation
                    this@BillingHelper.validation = validation

                    val billingFlowParams = BillingFlowParams
                        .newBuilder()
                        .setSkuDetails(it)
                        .build()
                    billingClient.launchBillingFlow(activity, billingFlowParams)
                }

                purchaseResult?.let {
                    Log.d("BillingHelper", translateBillingResponseCodeToLogString(purchaseResult.billingResult.responseCode))
                }
                if (!purchaseResult?.billingResult?.debugMessage.isNullOrBlank()) {
                    Log.d("BillingHelper", "DebugMessage: ${purchaseResult?.billingResult?.debugMessage}")
                }
                return purchaseResult
            } ?: return null
        } else {
            return null
        }
    }

    /**
     * Get Sku Details
     *
     * @param sku String Specifies the SKU to get details for.
     * @param type String Specifies the [BillingClient.SkuType](https://developer.android.com/reference/com/android/billingclient/api/BillingClient.SkuType) of SKU to query.
     *
     */
    suspend fun querySkuDetails(sku: String, type: String): SkuDetails? = withContext(Dispatchers.IO) {
        return@withContext querySkuDetails(
            skuDetailParams = SkuDetailsParams.newBuilder().setSkusList(listOf(sku)).setType(type).build()
        )
    }

    suspend fun querySkuDetails(skuDetailParams: SkuDetailsParams): SkuDetails? = withContext(Dispatchers.IO) {
        if (skuDetailParams.skusList.size > 1) error("This function accepts only one sku per call")
        if (startConnectionIfNecessary()) {
            val skuDetailsResult = billingClient.querySkuDetails(skuDetailParams)
            Log.d("BillingHelper", "Billing Result: ${skuDetailsResult.skuDetailsList?.size}")
            return@withContext skuDetailsResult.skuDetailsList?.getOrNull(0)
        } else {
            return@withContext null
        }
    }

    suspend fun querySkuDetailsList(skus: List<String>, type: String): List<SkuDetails>? = withContext(Dispatchers.IO) {
        return@withContext querySkuDetailsList(
            skuDetailParams = SkuDetailsParams.newBuilder().setSkusList(skus).setType(type).build()
        )
    }

    suspend fun querySkuDetailsList(skuDetailParams: SkuDetailsParams): List<SkuDetails>? = withContext(Dispatchers.IO) {
        if (startConnectionIfNecessary()) {
            val skuDetailsResult = billingClient.querySkuDetails(skuDetailParams)
            Log.d("BillingHelper", "Billing Result: ${skuDetailsResult.skuDetailsList?.size}")
            return@withContext skuDetailsResult.skuDetailsList
        } else {
            return@withContext null
        }
    }

    override fun onPurchasesUpdated(billingResult: BillingResult, purchases: List<Purchase>?) {
        purchases?.forEach { purchase ->
            if (!validation(purchase)) {
                Log.e("BillingHelper", "Got a purchase: $purchase; but signature is bad.")
                billingContinuation?.resumeWith(Result.failure(SecurityException("No valid Signature")))
                return
            }
        }
        billingContinuation?.resume(PurchasesResult(billingResult, purchases ?: emptyList()))
    }
}
