// Copyright (c). Gem Wallet. All rights reserved.

import GRDB
import Primitives

public struct TotalValueRequest: DatabaseQueryable, Equatable {
    public var walletId: WalletId
    public var type: TotalValueType
    public var earnUnderlyingAssetIdsByBackedAssetId: [String: [String]]

    public init(
        walletId: WalletId,
        type: TotalValueType,
        earnUnderlyingAssetIdsByBackedAssetId: [String: [String]] = [:],
    ) {
        self.walletId = walletId
        self.type = type
        self.earnUnderlyingAssetIdsByBackedAssetId = earnUnderlyingAssetIdsByBackedAssetId
    }

    public func fetch(_ db: Database) throws -> TotalFiatValue {
        switch type {
        case .perpetual:
            return try BalanceCalculator.totalFiatValue([perpetualFiatValue(db)])
        case .wallet:
            let assets = try walletFiatValues(db)
            return try BalanceCalculator.totalFiatValue(assets + [perpetualFiatValue(db)])
        case .earn:
            let assets = try assetRecords(db).compactMap {
                AssetFiatValue(record: $0, amount: $0.balance.stakedAmount + $0.balance.earnAmount)
            }
            return BalanceCalculator.totalFiatValue(assets)
        }
    }

    private func assetRecords(_ db: Database) throws -> [AssetRecordInfoMinimal] {
        try AssetRecord
            .including(optional: AssetRecord.price)
            .including(optional: AssetRecord.balance)
            .joining(required: AssetRecord.balance
                .filter(BalanceRecord.Columns.walletId == walletId.id)
                .filter(BalanceRecord.Columns.isEnabled == true))
            .asRequest(of: AssetRecordInfoMinimal.self)
            .fetchAll(db)
    }

    private func walletAmount(record: AssetRecordInfoMinimal, excludedBackedAssetIds: Set<String>) -> Double {
        excludedBackedAssetIds.contains(record.asset.id)
            ? record.balance.totalAmount - record.balance.earnAmount
            : record.balance.totalAmount
    }

    private func excludedBackedAssetIds(records: [AssetRecordInfoMinimal]) -> Set<String> {
        let enabledAssetIds = Set(records.map(\.asset.id))
        return Set(earnUnderlyingAssetIdsByBackedAssetId.compactMap { backedAssetId, underlyingAssetIds in
            underlyingAssetIds.contains(where: enabledAssetIds.contains) ? backedAssetId : nil
        })
    }

    private func walletFiatValues(_ db: Database) throws -> [AssetFiatValue] {
        let records = try assetRecords(db)

        if earnUnderlyingAssetIdsByBackedAssetId.isEmpty {
            return records.compactMap {
                AssetFiatValue(record: $0, amount: $0.balance.totalAmount)
            }
        }

        let excludedBackedAssetIds = excludedBackedAssetIds(records: records)
        return records.compactMap {
            AssetFiatValue(record: $0, amount: walletAmount(record: $0, excludedBackedAssetIds: excludedBackedAssetIds))
        }
    }

    private func perpetualFiatValue(_ db: Database) throws -> AssetFiatValue {
        let balance = try PerpetualWalletBalanceRequest(walletId: walletId).fetch(db)
        return AssetFiatValue(amount: balance.total, price: 1, priceChangePercentage24h: 0)
    }
}

extension AssetFiatValue {
    init?(record: AssetRecordInfoMinimal, amount: Double) {
        guard let price = record.price else { return nil }
        self.init(
            amount: amount,
            price: price.price,
            priceChangePercentage24h: price.priceChangePercentage24h,
        )
    }
}
