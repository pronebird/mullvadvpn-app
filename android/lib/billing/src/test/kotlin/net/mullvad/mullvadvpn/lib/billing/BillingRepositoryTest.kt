package net.mullvad.mullvadvpn.lib.billing

import android.app.Activity
import android.content.Context
import app.cash.turbine.test
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClient.BillingResponseCode
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.ProductDetailsResult
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesResult
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryPurchasesParams
import com.android.billingclient.api.queryProductDetails
import com.android.billingclient.api.queryPurchasesAsync
import io.mockk.CapturingSlot
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import io.mockk.verify
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import net.mullvad.mullvadvpn.lib.billing.model.BillingException
import net.mullvad.mullvadvpn.lib.billing.model.PurchaseEvent
import net.mullvad.mullvadvpn.lib.common.test.TestCoroutineRule
import net.mullvad.mullvadvpn.lib.common.test.assertLists
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(TestCoroutineRule::class)
class BillingRepositoryTest {

    private val mockContext: Context = mockk()
    private lateinit var billingRepository: BillingRepository

    private val mockBillingClientBuilder: BillingClient.Builder = mockk(relaxed = true)
    private val mockBillingClient: BillingClient = mockk()

    private val purchaseUpdatedListenerSlot: CapturingSlot<PurchasesUpdatedListener> =
        CapturingSlot()

    @BeforeEach
    fun setup() {
        mockkStatic(BILLING_CLIENT_CLASS)
        mockkStatic(BILLING_CLIENT_KOTLIN_CLASS)
        mockkStatic(BILLING_FLOW_PARAMS)

        every { BillingClient.newBuilder(any()) } returns mockBillingClientBuilder
        every { mockBillingClientBuilder.enablePendingPurchases(any()) } returns
            mockBillingClientBuilder
        every { mockBillingClientBuilder.setListener(capture(purchaseUpdatedListenerSlot)) } returns
            mockBillingClientBuilder
        every { mockBillingClientBuilder.build() } returns mockBillingClient

        billingRepository = BillingRepository(mockContext)
    }

    @AfterEach
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `when billing client query product details returns OK query products should return OK`() =
        runTest {
            // Arrange
            val mockBillingResult: BillingResult = mockk()
            val mockProductDetails: ProductDetails = mockk()
            val expectedProductDetailsResult: ProductDetailsResult = mockk()
            val productId = "TEST"
            val price = "44.4"

            every { mockBillingResult.responseCode } returns BillingResponseCode.OK
            every { mockBillingClient.isReady } returns true
            every { mockBillingClient.connectionState } returns
                BillingClient.ConnectionState.CONNECTED
            coEvery { mockBillingClient.queryProductDetails(any()) } returns
                expectedProductDetailsResult
            every { expectedProductDetailsResult.billingResult } returns mockBillingResult
            every { expectedProductDetailsResult.productDetailsList } returns
                listOf(mockProductDetails)
            every { mockProductDetails.productId } returns productId
            every { mockProductDetails.oneTimePurchaseOfferDetails?.formattedPrice } returns price

            // Act
            val result = billingRepository.queryProducts(listOf(productId))

            // Assert
            assertEquals(expectedProductDetailsResult, result)
        }

    @Test
    fun `when billing client query product details returns empty list query products should return empty list`() =
        runTest {
            // Arrange
            val mockBillingResult: BillingResult = mockk()
            val mockProductDetailsResult: ProductDetailsResult = mockk()

            every { mockBillingResult.responseCode } returns BillingResponseCode.ITEM_UNAVAILABLE
            every { mockBillingClient.isReady } returns true
            every { mockBillingClient.connectionState } returns
                BillingClient.ConnectionState.CONNECTED
            coEvery { mockBillingClient.queryProductDetails(any()) } returns
                mockProductDetailsResult
            every { mockProductDetailsResult.billingResult } returns mockBillingResult
            every { mockProductDetailsResult.productDetailsList } returns emptyList()

            // Act
            val result = billingRepository.queryProducts(listOf("TEST"))

            // Assert
            assertEquals(mockProductDetailsResult, result)
        }

    @Test
    fun `when billing client query product details returns billing unavailable query products should return billing unavailable`() =
        runTest {
            // Arrange
            val mockBillingResult: BillingResult = mockk()
            val mockProductDetailsResult: ProductDetailsResult = mockk()

            every { mockBillingResult.responseCode } returns BillingResponseCode.BILLING_UNAVAILABLE
            every { mockBillingClient.isReady } returns true
            every { mockBillingClient.connectionState } returns
                BillingClient.ConnectionState.CONNECTED
            coEvery { mockBillingClient.queryProductDetails(any()) } returns
                mockProductDetailsResult
            every { mockProductDetailsResult.billingResult } returns mockBillingResult
            every { mockProductDetailsResult.productDetailsList } returns emptyList()

            // Act
            val result = billingRepository.queryProducts(listOf("TEST"))

            // Assert
            assertEquals(mockProductDetailsResult, result)
        }

    @Test
    fun `when billing client launch billing flow returns OK start purchase flow should return OK`() =
        runTest {
            // Arrange
            val mockProductBillingResult: BillingResult = mockk()
            val mockBillingResult: BillingResult = mockk()
            val transactionId = "MOCK22"
            val mockProductDetails: ProductDetails = mockk(relaxed = true)
            val mockActivityProvider: () -> Activity = mockk()
            every { mockBillingResult.responseCode } returns BillingResponseCode.OK
            every { mockBillingClient.isReady } returns true
            every { mockBillingClient.connectionState } returns
                BillingClient.ConnectionState.CONNECTED
            every { mockBillingClient.launchBillingFlow(any(), any()) } returns mockBillingResult
            every { BillingFlowParams.newBuilder() } returns mockk(relaxed = true)
            every { mockProductBillingResult.responseCode } returns BillingResponseCode.OK
            every { mockActivityProvider() } returns mockk()

            // Act
            val result =
                billingRepository.startPurchaseFlow(
                    mockProductDetails,
                    transactionId,
                    mockActivityProvider,
                )

            // Assert
            assertEquals(mockBillingResult, result)
        }

    @Test
    fun `when billing client launch billing flow returns unavailable start purchase flow should return unavailable`() =
        runTest {
            // Arrange
            val mockBillingResult: BillingResult = mockk()
            val transactionId = "MOCK22"
            val mockProductDetails: ProductDetails = mockk(relaxed = true)
            val mockActivityProvider: () -> Activity = mockk()
            every { mockBillingResult.responseCode } returns BillingResponseCode.BILLING_UNAVAILABLE
            every { mockBillingClient.isReady } returns true
            every { mockBillingClient.connectionState } returns
                BillingClient.ConnectionState.CONNECTED
            every { mockBillingClient.launchBillingFlow(any(), any()) } returns mockBillingResult
            every { BillingFlowParams.newBuilder() } returns mockk(relaxed = true)
            every { mockActivityProvider() } returns mockk()

            // Act
            val result =
                billingRepository.startPurchaseFlow(
                    mockProductDetails,
                    transactionId,
                    mockActivityProvider,
                )

            // Assert
            assertEquals(mockBillingResult, result)
        }

    @Test
    fun `starting purchase flow with empty transaction id should return error`() = runTest {
        // Arrange
        val transactionId = ""
        val mockProductDetails: ProductDetails = mockk(relaxed = true)
        val mockActivityProvider: () -> Activity = mockk()
        every { mockBillingClient.isReady } returns true
        every { mockBillingClient.connectionState } returns BillingClient.ConnectionState.CONNECTED

        // Act
        val result =
            billingRepository.startPurchaseFlow(
                mockProductDetails,
                transactionId,
                mockActivityProvider,
            )

        // Assert
        assertEquals(BillingResponseCode.ERROR, result.responseCode)
    }

    @Test
    fun `when billing client query purchases returns OK query purchases should return OK`() =
        runTest {
            // Arrange
            val mockResult: PurchasesResult = mockk()
            val mockPurchase: Purchase = mockk()
            every { mockResult.billingResult.responseCode } returns BillingResponseCode.OK
            every { mockResult.purchasesList } returns listOf(mockPurchase)
            every { mockBillingClient.isReady } returns true
            every { mockBillingClient.connectionState } returns
                BillingClient.ConnectionState.CONNECTED
            coEvery { mockBillingClient.queryPurchasesAsync(any<QueryPurchasesParams>()) } returns
                mockResult
            every { BillingFlowParams.newBuilder() } returns mockk(relaxed = true)

            // Act
            val result = billingRepository.queryPurchases()

            // Assert
            assertEquals(mockResult, result)
        }

    @Test
    fun `when billing client query purchases returns empty list query purchases should return empty list`() =
        runTest {
            // Arrange
            val mockResult: PurchasesResult = mockk()
            every { mockResult.billingResult.responseCode } returns BillingResponseCode.OK
            every { mockResult.purchasesList } returns emptyList()
            every { mockBillingClient.isReady } returns true
            every { mockBillingClient.connectionState } returns
                BillingClient.ConnectionState.CONNECTED
            coEvery { mockBillingClient.queryPurchasesAsync(any<QueryPurchasesParams>()) } returns
                mockResult
            every { BillingFlowParams.newBuilder() } returns mockk(relaxed = true)

            // Act
            val result = billingRepository.queryPurchases()

            // Assert
            assertEquals(mockResult, result)
        }

    @Test
    fun `when billing client query purchases returns unavailable query purchases should return unavailable`() =
        runTest {
            // Arrange
            val responseCode = BillingResponseCode.ITEM_UNAVAILABLE
            val message = "ERROR"
            val expectedError = BillingException(responseCode, message)
            val mockResult: PurchasesResult = mockk()
            every { mockResult.billingResult.responseCode } returns responseCode
            every { mockResult.billingResult.debugMessage } returns message
            every { mockResult.purchasesList } returns emptyList()
            every { mockBillingClient.isReady } returns true
            every { mockBillingClient.connectionState } returns
                BillingClient.ConnectionState.CONNECTED
            coEvery { mockBillingClient.queryPurchasesAsync(any<QueryPurchasesParams>()) } returns
                mockResult
            every { BillingFlowParams.newBuilder() } returns mockk(relaxed = true)

            // Act
            val result = billingRepository.queryPurchases()

            // Assert
            assertEquals(
                expectedError.toBillingResult().responseCode,
                result.billingResult.responseCode,
            )
            assertEquals(expectedError.message, result.billingResult.debugMessage)
        }

    @Test
    fun `when onPurchasesUpdated returns OK purchase event should return completed`() = runTest {
        // Arrange
        val mockPurchase: Purchase = mockk()
        val mockPurchaseList = listOf(mockPurchase)
        val mockBillingResult: BillingResult = mockk()
        every { mockBillingResult.responseCode } returns BillingResponseCode.OK

        // Act, Assert
        billingRepository.purchaseEvents.test {
            purchaseUpdatedListenerSlot.captured.onPurchasesUpdated(
                mockBillingResult,
                mockPurchaseList,
            )
            val result = awaitItem()
            assertIs<PurchaseEvent.Completed>(result)
            assertLists(mockPurchaseList, result.purchases)
        }
    }

    @Test
    fun `when onPurchasesUpdated returns user canceled purchase event should return user canceled`() =
        runTest {
            // Arrange
            val mockBillingResult: BillingResult = mockk()
            val mockResponseCode: Int = BillingResponseCode.USER_CANCELED
            every { mockBillingResult.responseCode } returns mockResponseCode

            // Act, Assert
            billingRepository.purchaseEvents.test {
                purchaseUpdatedListenerSlot.captured.onPurchasesUpdated(mockBillingResult, null)
                val result = awaitItem()
                assertIs<PurchaseEvent.UserCanceled>(result)
            }
        }

    @Test
    fun `when onPurchasesUpdated returns error purchase event should return error`() = runTest {
        // Arrange
        val mockDebugMessage = "ERROR"
        val mockBillingResult: BillingResult = mockk()
        val mockResponseCode: Int = BillingResponseCode.ERROR
        val expectedError =
            BillingException(responseCode = mockResponseCode, message = mockDebugMessage)
        every { mockBillingResult.responseCode } returns mockResponseCode
        every { mockBillingResult.debugMessage } returns mockDebugMessage

        // Act, Assert
        billingRepository.purchaseEvents.test {
            purchaseUpdatedListenerSlot.captured.onPurchasesUpdated(mockBillingResult, null)
            val result = awaitItem()
            assertIs<PurchaseEvent.Error>(result)
            assertEquals(expectedError.message, result.exception.message)
        }
    }

    @Test
    fun `when billing client is not ready start connection should be called`() = runTest {
        // Arrange
        val mockStartConnectionResult: BillingResult = mockk()
        every { mockBillingClient.isReady } returns false
        every { mockBillingClient.connectionState } returns
            BillingClient.ConnectionState.DISCONNECTED
        every { mockBillingClient.startConnection(any()) } answers
            {
                firstArg<BillingClientStateListener>()
                    .onBillingSetupFinished(mockStartConnectionResult)
            }
        every { mockStartConnectionResult.responseCode } returns BillingResponseCode.OK
        coEvery { mockBillingClient.queryPurchasesAsync(any<QueryPurchasesParams>()) } returns
            mockk(relaxed = true)

        // Act
        billingRepository.queryPurchases()

        // Assert
        verify { mockBillingClient.startConnection(any()) }
        coVerify { mockBillingClient.queryPurchasesAsync(any<QueryPurchasesParams>()) }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `ensure only one billing client start connection is called`() =
        runTest(UnconfinedTestDispatcher()) {
            // Arrange
            var hasConnected = false
            val mockStartConnectionResult: BillingResult = mockk()
            every { mockBillingClient.isReady } answers { hasConnected }
            every { mockBillingClient.connectionState } answers
                {
                    if (hasConnected) {
                        BillingClient.ConnectionState.CONNECTED
                    } else {
                        BillingClient.ConnectionState.DISCONNECTED
                    }
                }
            every { mockBillingClient.startConnection(any()) } answers
                {
                    hasConnected = true
                    firstArg<BillingClientStateListener>()
                        .onBillingSetupFinished(mockStartConnectionResult)
                }
            every { mockStartConnectionResult.responseCode } returns BillingResponseCode.OK
            coEvery { mockBillingClient.queryPurchasesAsync(any<QueryPurchasesParams>()) } returns
                mockk(relaxed = true)
            coEvery { mockBillingClient.queryProductDetails(any()) } returns mockk(relaxed = true)

            // Act
            launch { billingRepository.queryPurchases() }
            launch { billingRepository.queryProducts(listOf("MOCK")) }

            // Assert
            verify(exactly = 1) { mockBillingClient.startConnection(any()) }
            coVerify { mockBillingClient.queryPurchasesAsync(any<QueryPurchasesParams>()) }
            coVerify { mockBillingClient.queryProductDetails(any()) }
        }

    companion object {
        private const val BILLING_CLIENT_CLASS = "com.android.billingclient.api.BillingClient"
        private const val BILLING_CLIENT_KOTLIN_CLASS =
            "com.android.billingclient.api.BillingClientKotlinKt"
        private const val BILLING_FLOW_PARAMS = "com.android.billingclient.api.BillingFlowParams"
    }
}
