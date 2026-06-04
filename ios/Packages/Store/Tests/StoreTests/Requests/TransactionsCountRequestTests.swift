// Copyright (c). Gem Wallet. All rights reserved.

import Primitives
@testable import Store
import StoreTestKit
import Testing

struct TransactionsCountRequestTests {
    @Test
    func countsSelectedStates() throws {
        let db = DB.mockAssets()
        let walletId = WalletId.multicoin(address: "0x0000000000000000000000000000000000000000")
        let store = TransactionStore(db: db)

        try store.addTransactions(walletId: walletId, transactions: [
            .mock(transactionId: TransactionId(chain: .bitcoin, hash: "1"), state: .pending),
            .mock(transactionId: TransactionId(chain: .bitcoin, hash: "2"), state: .inTransit),
            .mock(transactionId: TransactionId(chain: .bitcoin, hash: "3"), state: .confirmed),
        ])

        let count = try db.dbQueue.read { db in
            try TransactionsCountRequest(walletId: walletId, states: [.pending, .inTransit]).fetch(db)
        }

        #expect(count == 2)
    }
}
