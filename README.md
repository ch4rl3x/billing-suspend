# Billing-Suspend
BillingHelper is an helper to easy use coroutines with the BillingClient.
It also includes a client-side token validation, if there is not server available

<a href="https://github.com/Ch4rl3x/BillingHelper/actions/workflows/snapshot.yml"><img src="https://github.com/Ch4rl3x/BillingHelper/actions/workflows/snapshot.yml/badge.svg" alt="Build"></a>

<a href="https://www.codefactor.io/repository/github/ch4rl3x/billing-suspend"><img src="https://www.codefactor.io/repository/github/ch4rl3x/billing-suspend/badge" alt="CodeFactor" /></a>
<a href="https://repo1.maven.org/maven2/de/charlex/billing/billing-suspend/"><img src="https://img.shields.io/maven-central/v/de.charlex.billing/billing-suspend" alt="Maven Central" /></a>

## Dependency

Add the library to your module `build.gradle.kts`
```kotlin
dependencies {
    implementation("de.charlex.billing:billing-suspend:9.1.0-1.0.0")
}
```

Built against Play Billing Library 9.1.0. Google requires new apps and updates to
ship Play Billing Library 8 or later from August 31, 2026.

## Features
- Suspendable Billing Access
- Lifecycle-aware connection handling
- Client-Side Token Validation

## Migrating from 7.0.0-x

- `queryPurchaseHistory` was removed. Google removed `queryPurchaseHistoryAsync` in Play
  Billing Library 8; use `queryPurchases`, the
  [voided purchases server API](https://developers.google.com/android-publisher/voided-purchases)
  or your own backend instead.
- `initilize` was renamed to `initialize` (the old name still works but is deprecated).
- `billingClientStatus` is now a `StateFlow<Int>` instead of a `MutableSharedFlow<Int>`.
- `purchase` and `showInAppMessages` now return a `BillingResult?` instead of `Unit`.
- `endConnection` is now callable on `BillingHelper` itself.
- `enablePendingPurchases()` without arguments no longer exists in Play Billing Library 8;
  pass `PendingPurchasesParams` as shown below.

## Usage BillingHelper

```kotlin
val billingHelper = BillingHelper(
    context = this,
    billingClientBuilder = {
        enablePendingPurchases(
            PendingPurchasesParams.newBuilder()
                .enableOneTimeProducts()
                .build()
        )
    },
    onPurchasesResult = { purchasesResult ->
        // Called for every purchase update, e.g. after the billing flow finished
    }
)

// Connects the client and keeps it connected while the lifecycle is at least STARTED
billingHelper.initialize(lifecycleOwner)
```

Reconnecting after a dropped connection is handled by the Play Billing Library itself
(`enableAutoServiceReconnection()`, enabled by default here), so `initialize` only
establishes the initial connection.

Every suspending call waits up to 5 seconds for the client to become ready and returns
`null` if it does not.

Release the client when you are done with it — it cannot be reused afterwards:

```kotlin
billingHelper.endConnection()
```

Query and acknowledge existing purchases:

```kotlin
val purchasesResult = billingHelper.queryPurchases(BillingClient.ProductType.SUBS)

purchasesResult?.purchasesList?.forEach {
    if (it.isAcknowledged.not()) {
        billingHelper.acknowledgePurchase(it.purchaseToken)
    }
}
```

Launch a purchase:

```kotlin
val productDetails = billingHelper.queryProductDetails(
    productId = "productId_here",
    productType = BillingClient.ProductType.SUBS
)?.firstOrNull()

productDetails?.let {
    // The purchase itself is delivered to the onPurchasesResult callback above;
    // this only reports whether the flow could be launched.
    val billingResult = billingHelper.purchase(
        activity = activity,
        productDetails = it,
        offerToken = it.subscriptionOfferDetails?.firstOrNull()?.offerToken
    )
}
```

Show in-app messages Google Play has queued for the user, e.g. to recover a subscription
whose payment method was declined:

```kotlin
billingHelper.showInAppMessages(activity) { result ->
    // e.g. InAppMessageResult.InAppMessageResponseCode.SUBSCRIPTION_STATUS_UPDATED
}
```

## Usage BillingSecurity (Not recommended. Use Server-Side Validation instead)

```kotlin
onPurchasesResult = { purchasesResult ->
    purchasesResult.purchasesList.forEach { purchase ->
        BillingSecurity.verifyPurchase(PUBLIC_BASE64_RSA_KEY, purchase.originalJson, purchase.signature)
    }
}
```

That's it!

License
--------

    Copyright 2020 Alexander Karkossa

    Licensed under the Apache License, Version 2.0 (the "License");
    you may not use this file except in compliance with the License.
    You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

    Unless required by applicable law or agreed to in writing, software
    distributed under the License is distributed on an "AS IS" BASIS,
    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
    See the License for the specific language governing permissions and
    limitations under the License.
