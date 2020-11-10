# BillingHelper
BillingHelper is an helper to easy use coroutines with the BillingClient.
It also includes a client-side token validation, if there is not server available

<a href="https://github.com/Ch4rl3x/BillingHelper/actions?query=workflow%3ALint"><img src="https://github.com/Ch4rl3x/BillingHelper/workflows/Lint/badge.svg" alt="Lint"></a>
<a href="https://github.com/Ch4rl3x/BillingHelper/actions?query=workflow%3AKtlint"><img src="https://github.com/Ch4rl3x/BillingHelper/workflows/Ktlint/badge.svg" alt="Ktlint"></a>

[![](https://jitpack.io/v/Ch4rl3x/BillingHelper.svg)](https://jitpack.io/#Ch4rl3x/BillingHelper)

## Dependency

Add this in your root `build.gradle` file (**not** your module `build.gradle` file):

```gradle
allprojects {
	repositories {
        maven { url "https://jitpack.io" }
    }
}
```

Then, add the library to your module `build.gradle`
```gradle
dependencies {
    implementation 'com.github.Ch4rl3x:BillingHelper:latest.release.here'
}
```

## Features
- Suspendable Billing Access
- Client-Side Token Validation

## Usage BillingHelper

```kotlin
val billingHelper: BillingHelper = BillingHelper(activity) {
    enablePendingPurchases()
}

val purchases = billingHelper.queryPurchases(BillingClient.SkuType.SUBS)

purchases?.forEach {
    if (it.isAcknowledged.not()) {
        billingHelper.acknowledgePurchase(it.purchaseToken)
    }
}

billingHelper.purchase(sku = arrayOf("productId_here"), type = BillingClient.SkuType.SUBS)
```

## Usage BillingSecurity (Not recommended. Use Server-Side Validation instead)

```kotlin
billingHelper.purchase(sku = arrayOf("productId_here"), type = BillingClient.SkuType.SUBS) { purchase ->
    BillingSecurity.verifyPurchase(PUBLIC_BASE64_RSA_KEY, purchase.originalJson, purchase.signature)
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
