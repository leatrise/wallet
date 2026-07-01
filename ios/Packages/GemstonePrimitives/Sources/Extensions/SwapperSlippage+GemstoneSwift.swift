// Copyright (c). Gem Wallet. All rights reserved.

import Foundation
import struct Gemstone.SwapperSlippage
import enum Gemstone.SwapperSlippageMode
import Primitives

public extension SwapperSlippage {
    init(slippage: SwapSlippage, defaultSlippage: SwapperSlippage) {
        switch slippage {
        case .auto:
            // TODO: send mode .auto once the swapper implements auto slippage (mode is currently ignored)
            self = defaultSlippage
        case let .manual(bps):
            self.init(bps: bps, mode: .exact)
        }
    }
}
