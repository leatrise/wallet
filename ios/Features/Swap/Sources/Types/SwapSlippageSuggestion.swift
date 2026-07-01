// Copyright (c). Gem Wallet. All rights reserved.

import Foundation
import PrimitivesComponents

public struct SwapSlippageSuggestion: SuggestionViewable {
    public let bps: UInt32
    public var id: UInt32 { bps }

    public var inputValue: String {
        (Double(bps) / 100).formatted(.number.precision(.fractionLength(0 ... 2)))
    }

    public var title: String { "\(inputValue)%" }
}
