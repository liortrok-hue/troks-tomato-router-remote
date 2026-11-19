package com.troks.tomatorouters.data

import android.app.Activity
import android.content.Context
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.ConsumeParams
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.android.billingclient.api.consumePurchase
import com.android.billingclient.api.queryProductDetails
import com.android.billingclient.api.queryPurchasesAsync
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Optional donations through Google Play Billing (Play's payments policy requires it for in-app donations).
 *
 * Each amount is a one-time, consumable product, so the same person can donate again.
 * The product IDs must exist as active one-time products in Play Console
 * (Monetize > Products > One-time products); prices come from Play in the user's currency.
 * Donations unlock nothing in the app.
 */
class Donations(context: Context, private val scope: CoroutineScope) {

    enum class Result { THANKS, PENDING, CANCELED, ERROR }

    /** Donation amounts, cheapest first, once loaded from Play. Empty when Play isn't available. */
    private val _products = MutableStateFlow<List<ProductDetails>?>(null)
    val products: StateFlow<List<ProductDetails>?> = _products

    private val _result = MutableStateFlow<Result?>(null)
    val result: StateFlow<Result?> = _result

    private val client: BillingClient = BillingClient.newBuilder(context.applicationContext)
        .setListener(PurchasesUpdatedListener { billing, purchases -> onPurchases(billing, purchases) })
        .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
        .enableAutoServiceReconnection()
        .build()

    private suspend fun connect(): Boolean {
        if (client.isReady) return true
        return suspendCancellableCoroutine { cont ->
            client.startConnection(object : BillingClientStateListener {
                override fun onBillingSetupFinished(result: BillingResult) {
                    if (cont.isActive) cont.resume(result.responseCode == BillingClient.BillingResponseCode.OK)
                }
                override fun onBillingServiceDisconnected() {
                    if (cont.isActive) cont.resume(false)
                }
            })
        }
    }

    /** Loads the donation amounts (and finishes any donation that was paid but not yet completed). */
    fun load() {
        scope.launch {
            if (!connect()) { _products.value = emptyList(); return@launch }
            finishOpenPurchases()
            val params = QueryProductDetailsParams.newBuilder().setProductList(
                PRODUCT_IDS.map {
                    QueryProductDetailsParams.Product.newBuilder()
                        .setProductId(it)
                        .setProductType(BillingClient.ProductType.INAPP)
                        .build()
                }
            ).build()
            val res = runCatching { client.queryProductDetails(params) }.getOrNull()
            _products.value = res?.productDetailsList.orEmpty()
                .sortedBy { it.oneTimePurchaseOfferDetails?.priceAmountMicros ?: Long.MAX_VALUE }
        }
    }

    fun donate(activity: Activity, product: ProductDetails) {
        _result.value = null
        val params = BillingFlowParams.newBuilder().setProductDetailsParamsList(
            listOf(BillingFlowParams.ProductDetailsParams.newBuilder().setProductDetails(product).build())
        ).build()
        val r = client.launchBillingFlow(activity, params)
        if (r.responseCode != BillingClient.BillingResponseCode.OK) _result.value = Result.ERROR
    }

    fun clearResult() { _result.value = null }

    private fun onPurchases(billing: BillingResult, purchases: List<Purchase>?) {
        when (billing.responseCode) {
            BillingClient.BillingResponseCode.OK -> purchases.orEmpty().forEach { p ->
                when (p.purchaseState) {
                    Purchase.PurchaseState.PURCHASED -> { _result.value = Result.THANKS; scope.launch { consume(p) } }
                    Purchase.PurchaseState.PENDING -> _result.value = Result.PENDING
                    else -> {}
                }
            }
            BillingClient.BillingResponseCode.USER_CANCELED -> _result.value = Result.CANCELED
            else -> _result.value = Result.ERROR
        }
    }

    /** A donation that was paid while the app was closed (or a pending one that went through). */
    private suspend fun finishOpenPurchases() {
        val res = runCatching {
            client.queryPurchasesAsync(QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.INAPP).build())
        }.getOrNull() ?: return
        res.purchasesList.filter { it.purchaseState == Purchase.PurchaseState.PURCHASED }.forEach { consume(it) }
    }

    /** Consuming completes the donation (Play refunds unacknowledged ones) and allows donating again. */
    private suspend fun consume(p: Purchase) {
        runCatching { client.consumePurchase(ConsumeParams.newBuilder().setPurchaseToken(p.purchaseToken).build()) }
    }

    fun close() { runCatching { client.endConnection() } }

    companion object {
        /** Create these in Play Console as one-time products, e.g. ₪10 / ₪20 / ₪50. */
        val PRODUCT_IDS = listOf("donate_small", "donate_medium", "donate_large")
    }
}
