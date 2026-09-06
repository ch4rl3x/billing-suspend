package de.charlex.billing

import android.app.Activity
import android.content.Context
import android.util.Log
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClient.ProductType
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.ConsumeParams
import com.android.billingclient.api.ConsumeResult
import com.android.billingclient.api.InAppMessageParams
import com.android.billingclient.api.InAppMessageResult
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesResult
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryProductDetailsParams.Product
import com.android.billingclient.api.QueryPurchasesParams
import com.android.billingclient.api.acknowledgePurchase
import com.android.billingclient.api.consumePurchase
import com.android.billingclient.api.queryProductDetails
import com.android.billingclient.api.queryPurchasesAsync
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration.Companion.milliseconds

class BillingHelper(
    context: Context,
    billingClientBuilder: BillingClient.Builder.() -> Unit,
    onPurchasesResult: (purchasesResult: PurchasesResult) -> Unit
) : BillingClientStateListener {

    private val _billingClientStatus = MutableStateFlow(BillingClient.BillingResponseCode.SERVICE_DISCONNECTED)

    /**
     * The latest [BillingClient.BillingResponseCode] reported by the billing service.
     *
     * Starts out as [BillingClient.BillingResponseCode.SERVICE_DISCONNECTED] and turns into
     * [BillingClient.BillingResponseCode.OK] once the connection is established.
     */
    val billingClientStatus: StateFlow<Int> = _billingClientStatus.asStateFlow()

    override fun onBillingSetupFinished(billingResult: BillingResult) {
        _billingClientStatus.value = billingResult.responseCode
    }

    override fun onBillingServiceDisconnected() {
        _billingClientStatus.value = BillingClient.BillingResponseCode.SERVICE_DISCONNECTED
    }

    private var billingClient: BillingClient

    init {
        billingClient = BillingClient.newBuilder(context).apply {
            // Since Play Billing Library 8 the client re-establishes a dropped
            // connection on its own, so only the initial connect is our job.
            enableAutoServiceReconnection()
            billingClientBuilder.invoke(this)
            setListener { billingResult, purchases ->
                Log.d("BillingHelper", translateBillingResponseCodeToLogString(billingResult.responseCode))

                if (billingResult.debugMessage.isNotBlank()) {
                    Log.d("BillingHelper", "DebugMessage: ${billingResult.debugMessage}")
                }

                onPurchasesResult(PurchasesResult(billingResult, purchases ?: emptyList()))
            }
        }.build()
    }

    @Deprecated(
        message = "Renamed to initialize",
        replaceWith = ReplaceWith("initialize(lifecycleOwner)")
    )
    fun initilize(lifecycleOwner: LifecycleOwner) = initialize(lifecycleOwner)

    /**
     * Connects the [BillingClient] and keeps it connected while [lifecycleOwner] is at least
     * [Lifecycle.State.STARTED].
     *
     * Re-connecting after a dropped connection is handled by the billing library itself since
     * Play Billing Library 8, so this only has to establish the initial connection.
     */
    fun initialize(lifecycleOwner: LifecycleOwner) {
        _billingClientStatus.value = BillingClient.BillingResponseCode.SERVICE_DISCONNECTED
        lifecycleOwner.lifecycleScope.launch {
            // repeatOnLifecycle launches the block in a new coroutine every time the
            // lifecycle is in the STARTED state (or above) and cancels it when it's STOPPED.
            lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                // Trigger the flow and start listening for values.
                // This happens when lifecycle is STARTED and stops
                // collecting when the lifecycle is STOPPED

                billingClientStatus.collect {
                    when (it) {
                        BillingClient.BillingResponseCode.OK -> {}
                        else -> if (billingClient.connectionState == BillingClient.ConnectionState.DISCONNECTED) {
                            billingClient.startConnection(this@BillingHelper)
                        }
                    }
                }
            }
        }
    }

    private suspend fun isBillingClientConnected(): Boolean =
        withTimeoutOrNull(5_000.milliseconds) {
            billingClientStatus.first { it == BillingClient.BillingResponseCode.OK }
            true
        } ?: false

    /**
     * Closes the connection to the billing service and releases its resources.
     *
     * The [BillingClient] cannot be reused afterwards, so a new [BillingHelper] is needed to
     * make further calls.
     */
    suspend fun endConnection() = withContext(Dispatchers.Main) {
        Log.d("BillingHelper", "Ending the billing client connection")
        billingClient.endConnection()
    }

    /**
     * Shows any in-app messages Google Play has queued for the current user, e.g. to recover a
     * subscription whose payment method was declined.
     *
     * @return [BillingResult](https://developer.android.com/reference/com/android/billingclient/api/BillingResult) Result of the request, or `null` if the billing client is not connected.
     */
    suspend fun showInAppMessages(
        activity: Activity,
        inAppMessageParams: InAppMessageParams = InAppMessageParams.newBuilder()
            .addInAppMessageCategoryToShow(InAppMessageParams.InAppMessageCategoryId.TRANSACTIONAL)
            .build(),
        resultHandler: (InAppMessageResult) -> Unit
    ): BillingResult? = withContext(Dispatchers.Main) {
        return@withContext if (isBillingClientConnected()) {
            billingClient.showInAppMessages(
                activity,
                inAppMessageParams,
                resultHandler
            )
        } else {
            null
        }
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
        return@withContext if (isBillingClientConnected()) {
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
        return@withContext if (isBillingClientConnected()) {
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
     * @param productType String The type of the product, either "inapp" or "subs" as in [BillingClient.ProductType](https://developer.android.com/reference/com/android/billingclient/api/BillingClient.ProductType).
     *
     * @return [Purchase.PurchasesResult](https://developer.android.com/reference/com/android/billingclient/api/Purchase.PurchasesResult) The Purchase.PurchasesResult containing the list of purchases and the response code ([BillingClient.BillingResponseCode](https://developer.android.com/reference/com/android/billingclient/api/BillingClient.BillingResponseCode))
     */
    suspend fun queryPurchases(@ProductType productType: String): PurchasesResult? = withContext(Dispatchers.IO) {
        Log.d("BillingHelper", "queryPurchases")
        return@withContext if (isBillingClientConnected()) {
            Log.d("BillingHelper", "queryPurchases on billingClient")
            billingClient.queryPurchasesAsync(
                QueryPurchasesParams.newBuilder()
                    .setProductType(productType)
                    .build()
            )
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
     * Launches the Google Play billing flow for [productDetails].
     *
     * The result of the purchase itself is not returned here, it is delivered to the
     * `onPurchasesResult` callback passed to the [BillingHelper] constructor.
     *
     * @param offerToken String? To get an offer token, call [ProductDetails.subscriptionOfferDetails]
     * for a list of offers that are available to the user.
     *
     * @return [BillingResult](https://developer.android.com/reference/com/android/billingclient/api/BillingResult) Result of launching the flow, or `null` if the billing client is not connected.
     */
    suspend fun purchase(
        activity: Activity,
        productDetails: ProductDetails,
        offerToken: String? = null,
        isOfferPersonalized: Boolean = false
    ): BillingResult? = withContext(Dispatchers.Main) {
        return@withContext if (isBillingClientConnected()) {
            Log.d("BillingHelper", "purchase ${productDetails.name}")

            val productDetailsParamsList = listOf(
                BillingFlowParams.ProductDetailsParams.newBuilder()
                    // retrieve a value for "productDetails" by calling queryProductDetailsAsync()
                    .setProductDetails(productDetails)
                    .apply {
                        offerToken?.let {
                            setOfferToken(offerToken)
                        }
                    }.build()
            )

            val billingFlowParams = BillingFlowParams
                .newBuilder()
                .setProductDetailsParamsList(productDetailsParamsList)
                .setIsOfferPersonalized(isOfferPersonalized)
                .build()
            // launchBillingFlow has to be called from the main thread
            billingClient.launchBillingFlow(activity, billingFlowParams)
        } else {
            null
        }
    }

    /**
     * Get Sku Details
     *
     * @param productId String Specifies the productId to get details for.
     * @param type String Specifies the [BillingClient.ProductType](https://developer.android.com/reference/com/android/billingclient/api/BillingClient.ProductType) to query.
     *
     */
    suspend fun queryProductDetails(productId: String, @ProductType productType: String): List<ProductDetails>? = withContext(Dispatchers.IO) {
        return@withContext queryProductDetails(
            productDetailsParams = QueryProductDetailsParams.newBuilder().setProductList(
                listOf(
                    Product.newBuilder().setProductId(
                        productId
                    ).setProductType(
                        productType
                    ).build()
                )
            ).build()
        )
    }

    suspend fun queryProductDetails(productDetailsParams: QueryProductDetailsParams): List<ProductDetails>? = withContext(Dispatchers.IO) {
        if (isBillingClientConnected()) {
            val productDetailsResult = billingClient.queryProductDetails(productDetailsParams)
            Log.d("BillingHelper", "Billing Result: ${productDetailsResult.productDetailsList?.size}")
            return@withContext productDetailsResult.productDetailsList
        } else {
            return@withContext null
        }
    }
}
