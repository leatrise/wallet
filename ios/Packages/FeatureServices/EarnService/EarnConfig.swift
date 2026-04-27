// Copyright (c). Gem Wallet. All rights reserved.

import GemstonePrimitives
import Primitives

public enum EarnConfig {
    public static func underlyingAssetIdsByBackedAssetId() -> [String: [String]] {
        YieldProvider.allCases.reduce(into: [:]) { result, provider in
            for (backedAssetId, underlyingAssetIds) in GemstoneConfig.shared.getUnderlyingAssetsByProvider(providerId: provider.rawValue) {
                result[backedAssetId, default: []].append(contentsOf: underlyingAssetIds)
            }
        }
    }
}
