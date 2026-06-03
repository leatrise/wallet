package com.gemwallet.android.data.repositories.bridge

import com.wallet.core.primitives.Account

internal data class WalletConnectSupportedAccount(
    val address: String,
    val namespace: String,
    val reference: String,
    val methods: List<String>,
) {

    companion object {
        fun create(account: Account): WalletConnectSupportedAccount? {
            val namespace = account.chain.getNameSpace() ?: return null
            val reference = account.chain.getReference() ?: return null
            val methods = namespace.methods.map { it.string }
            return WalletConnectSupportedAccount(account.address, namespace.string, reference, methods)
        }
    }
}
