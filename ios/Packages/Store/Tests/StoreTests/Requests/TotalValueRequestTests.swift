// Copyright (c). Gem Wallet. All rights reserved.

import Primitives
import PrimitivesTestKit
import Store
import StoreTestKit
import Testing

struct TotalValueRequestTests {
    @Test
    func walletBalanceWithPrice() throws {
        let db = DB.mockAssets()
        let fiatRateStore = FiatRateStore(db: db)
        let priceStore = PriceStore(db: db)

        try fiatRateStore.add([FiatRate(symbol: Currency.usd.rawValue, rate: 1)])

        let ethId = AssetId(chain: .ethereum)
        try priceStore.updatePrice(
            price: AssetPrice(assetId: ethId, price: 1100, priceChangePercentage24h: 10, updatedAt: .now),
            currency: Currency.usd.rawValue,
        )

        try db.dbQueue.read { db in
            let result = try TotalValueRequest(walletId: .mock(), type: .wallet).fetch(db)

            #expect(result.value == 3300)
            #expect(result.pnlAmount == 300)
            #expect(result.pnlPercentage == 10)
        }
    }

    @Test
    func walletBalanceWithoutPrice() throws {
        let db = DB.mockAssets()

        try db.dbQueue.read { db in
            let result = try TotalValueRequest(walletId: .mock(), type: .wallet).fetch(db)

            #expect(result.value == 0)
            #expect(result.pnlAmount == 0)
            #expect(result.pnlPercentage == 0)
        }
    }

    @Test
    func walletBalanceZeroChange() throws {
        let db = DB.mockAssets()
        let fiatRateStore = FiatRateStore(db: db)
        let priceStore = PriceStore(db: db)

        try fiatRateStore.add([FiatRate(symbol: Currency.usd.rawValue, rate: 1)])

        let ethId = AssetId(chain: .ethereum)
        try priceStore.updatePrice(
            price: AssetPrice(assetId: ethId, price: 1100, priceChangePercentage24h: 0, updatedAt: .now),
            currency: Currency.usd.rawValue,
        )

        try db.dbQueue.read { db in
            let result = try TotalValueRequest(walletId: .mock(), type: .wallet).fetch(db)

            #expect(result.value == 3300)
            #expect(result.pnlAmount == 0)
            #expect(result.pnlPercentage == 0)
        }
    }

    @Test
    func walletBalanceIncludesPerpetualCollateralAndExcludesDisabled() throws {
        let db = try DB.mockAssetsWithPerpetualCollateralBalance()

        try db.dbQueue.read { db in
            let result = try TotalValueRequest(walletId: .mock(), type: .wallet).fetch(db)

            // ethereum (3 * 100) + perpetual (50 + 25); bnb is disabled
            #expect(result.value == 375)
            #expect(result.pnlAmount == 0)
            #expect(result.pnlPercentage == 0)
        }
    }

    @Test
    func perpetualBalanceUsesCollateralOnly() throws {
        let db = try DB.mockAssetsWithPerpetualCollateralBalance()

        try db.dbQueue.read { db in
            let result = try TotalValueRequest(walletId: .mock(), type: .perpetual).fetch(db)

            #expect(result.value == 75)
            #expect(result.pnlAmount == 0)
            #expect(result.pnlPercentage == 0)
        }
    }

    @Test
    func perpetualWalletBalanceSplitsTotalAndAvailable() throws {
        let db = try DB.mockAssetsWithPerpetualCollateralBalance()

        try db.dbQueue.read { db in
            let result = try PerpetualWalletBalanceRequest(walletId: .mock()).fetch(db)

            #expect(result.total == 75)
            #expect(result.available == 50)
        }
    }

    @Test
    func earnBalanceSumsStakedAndEarn() throws {
        let db = try DB.mockAssetsWithEarnBalance()

        try db.dbQueue.read { db in
            let result = try TotalValueRequest(walletId: .mock(), type: .earn).fetch(db)

            #expect(result.value == 330)
            #expect(result.pnlAmount == 30)
            #expect(result.pnlPercentage == 10)
        }
    }

    @Test
    func earnAmountExcludedWhenUnderlyingAssetEnabled() throws {
        let ton = AssetId(chain: .ton)
        let tsTON = AssetId(chain: .ton, tokenId: "0:BDF3FA8098D129B54B4F73B5BAC5D1E1FD91EB054169C3916DFC8CCD536D1000")
        let underlyingAssetIdsByBackedAssetId = [
            ton.identifier: [tsTON.identifier],
        ]
        let db = DB.mockAssets(assets: [
            .mock(asset: .mock(id: ton, name: "Toncoin", symbol: "TON", decimals: 9)),
            .mock(asset: .mock(id: tsTON, name: "Tonstakers TON", symbol: "tsTON", decimals: 9, type: .jetton)),
        ])
        let fiatRateStore = FiatRateStore(db: db)
        let priceStore = PriceStore(db: db)
        let balanceStore = BalanceStore(db: db)

        try fiatRateStore.add([FiatRate(symbol: Currency.usd.rawValue, rate: 1)])
        try priceStore.updatePrice(price: AssetPrice(assetId: ton, price: 2, priceChangePercentage24h: 0, updatedAt: .now), currency: Currency.usd.rawValue)
        try priceStore.updatePrice(price: AssetPrice(assetId: tsTON, price: 2.2, priceChangePercentage24h: 0, updatedAt: .now), currency: Currency.usd.rawValue)
        try balanceStore.setIsEnabled(walletId: .mock(), assetIds: [tsTON], value: false)
        try balanceStore.updateBalances(
            [
                UpdateBalance(assetId: ton, type: .earn(UpdateEarnBalance(balance: UpdateBalanceValue(value: "1200000000", amount: 1.2))), updatedAt: .now, isActive: true),
                UpdateBalance(assetId: tsTON, type: .token(UpdateTokenBalance(available: UpdateBalanceValue(value: "1000000000", amount: 1))), updatedAt: .now, isActive: true),
            ],
            for: .mock(),
        )

        try db.dbQueue.read { db in
            let result = try TotalValueRequest(walletId: .mock(), type: .wallet, earnUnderlyingAssetIdsByBackedAssetId: underlyingAssetIdsByBackedAssetId).fetch(db)

            #expect(result.value == 2.4)
        }
        try balanceStore.setIsEnabled(walletId: .mock(), assetIds: [tsTON], value: true)

        try db.dbQueue.read { db in
            let result = try TotalValueRequest(walletId: .mock(), type: .wallet, earnUnderlyingAssetIdsByBackedAssetId: underlyingAssetIdsByBackedAssetId).fetch(db)

            #expect(result.value == 2.2)
        }
    }
}
