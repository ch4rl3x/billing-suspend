package de.charlex.billing

import com.android.billingclient.api.Purchase
import java.io.IOException

/**
 * Verifies that the data was signed with the given signature, and returns the verified
 * purchase.
 *
 * Note: It's strongly recommended to perform such check on your backend since hackers can
 * replace this method with "constant true" if they decompile/rebuild your app.
 *
 * @param base64PublicKey the base64-encoded public key to use for verifying.
 * @throws java.io.IOException if encoding algorithm is not supported or key specification
 * is invalid
 */
@Throws(IOException::class)
fun Purchase.verifySignature(base64PublicKey: String?) {
    BillingSecurity.verifyPurchase(base64PublicKey, originalJson, signature)
}
