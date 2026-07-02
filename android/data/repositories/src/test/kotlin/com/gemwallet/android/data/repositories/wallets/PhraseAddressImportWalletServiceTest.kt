package com.gemwallet.android.data.repositories.wallets

import com.gemwallet.android.application.PasswordStore
import com.gemwallet.android.application.wallet_import.coordinators.SyncWalletImport
import com.gemwallet.android.blockchain.operators.StorePhraseOperator
import com.gemwallet.android.blockchain.operators.StoredWalletSecret
import com.gemwallet.android.blockchain.operators.ValidateAddressOperator
import com.gemwallet.android.blockchain.operators.ValidatePhraseOperator
import com.gemwallet.android.cases.device.InvalidateSubscriptions
import com.gemwallet.android.data.repositories.assets.AssetsRepository
import com.gemwallet.android.data.repositories.session.SessionRepository
import com.gemwallet.android.model.ImportType
import com.gemwallet.android.testkit.mockWallet
import com.wallet.core.primitives.WalletType
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerifyOrder
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Test

class PhraseAddressImportWalletServiceTest {

    private val walletsRepository = mockk<WalletsRepository>()
    private val assetsRepository = mockk<AssetsRepository>()
    private val sessionRepository = mockk<SessionRepository>()
    private val storePhraseOperator = mockk<StorePhraseOperator>()
    private val phraseValidate = mockk<ValidatePhraseOperator>()
    private val addressValidate = mockk<ValidateAddressOperator>()
    private val passwordStore = mockk<PasswordStore>()
    private val invalidateSubscriptions = mockk<InvalidateSubscriptions>(relaxed = true)
    private val walletImportSync = mockk<SyncWalletImport>(relaxed = true)

    private val wallet = mockWallet(id = "multicoin_0xabc")

    private val subject = PhraseAddressImportWalletService(
        walletsRepository = walletsRepository,
        assetsRepository = assetsRepository,
        sessionRepository = sessionRepository,
        storePhraseOperator = storePhraseOperator,
        phraseValidate = phraseValidate,
        addressValidate = addressValidate,
        passwordStore = passwordStore,
        invalidateSubscriptions = invalidateSubscriptions,
        walletImportSync = walletImportSync,
    )

    private fun stubHappyPath() {
        every { phraseValidate(any()) } returns Result.success(true)
        coEvery { walletsRepository.addControlled(any(), any(), any(), any(), any()) } returns wallet
        every { passwordStore.createPassword(any()) } returns "password"
        coEvery { storePhraseOperator(any(), any(), any()) } returns Result.success(StoredWalletSecret("keystore"))
        coEvery { assetsRepository.createAssets(wallet) } just Runs
        coEvery { sessionRepository.setWallet(wallet) } just Runs
    }

    @Test
    fun createWallet_invalidatesSubscriptionsBeforeActivatingSession() = runTest {
        stubHappyPath()

        subject.createWallet("Wallet", "phrase words for the new wallet flow")

        coVerifyOrder {
            invalidateSubscriptions()
            sessionRepository.setWallet(wallet)
        }
    }

    @Test
    fun importWallet_invalidatesSubscriptionsBeforeActivatingSession() = runTest {
        stubHappyPath()

        subject.importWallet(ImportType(WalletType.Multicoin), "Wallet", "phrase words for the wallet flow")

        coVerifyOrder {
            invalidateSubscriptions()
            sessionRepository.setWallet(wallet)
        }
    }
}
