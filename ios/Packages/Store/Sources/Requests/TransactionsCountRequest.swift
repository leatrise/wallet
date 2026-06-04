// Copyright (c). Gem Wallet. All rights reserved.

import Foundation
import GRDB
import Primitives

public struct TransactionsCountRequest: DatabaseQueryable {
    public var walletId: WalletId
    private let states: [TransactionState]

    public init(
        walletId: WalletId,
        state: TransactionState,
    ) {
        self.init(walletId: walletId, states: [state])
    }

    public init(
        walletId: WalletId,
        states: [TransactionState],
    ) {
        self.walletId = walletId
        self.states = states
    }

    public func fetch(_ db: Database) throws -> Int {
        try TransactionRecord
            .filter(TransactionRecord.Columns.walletId == walletId.id)
            .filter(states.map(\.rawValue).contains(TransactionRecord.Columns.state))
            .fetchCount(db)
    }
}

extension TransactionsCountRequest: Equatable {}
