// Copyright (c). Gem Wallet. All rights reserved.

import Primitives
@testable import Swap
import Testing

@MainActor
struct SwapSlippageViewModelTests {
    @Test
    func initAuto() {
        let model = SwapSlippageViewModel(slippage: .auto) { _ in }

        #expect(model.isAuto)
        #expect(model.selectedBps == 100)
    }

    @Test
    func initManual() {
        let model = SwapSlippageViewModel(slippage: .manual(bps: 50)) { _ in }

        #expect(model.isAuto == false)
        #expect(model.selectedBps == 50)
    }

    @Test
    func initManualLimitsToRange() {
        let belowMin = SwapSlippageViewModel(slippage: .manual(bps: 1)) { _ in }
        #expect(belowMin.selectedBps == SwapSlippageViewModel.minBps)

        let aboveMax = SwapSlippageViewModel(slippage: .manual(bps: 10_000)) { _ in }
        #expect(aboveMax.selectedBps == SwapSlippageViewModel.maxBps)
    }

    @Test
    func applyAuto() {
        var applied: SwapSlippage?
        let model = SwapSlippageViewModel(slippage: .manual(bps: 50)) { applied = $0 }
        model.isAuto = true
        model.apply()

        #expect(applied == .auto)
    }

    @Test(arguments: [SwapSlippageViewModel.minBps, 50, 100, SwapSlippageViewModel.maxBps])
    func applyManual(bps: UInt32) {
        var applied: SwapSlippage?
        let model = SwapSlippageViewModel(slippage: .auto) { applied = $0 }
        model.isAuto = false
        model.selectedBps = bps
        model.apply()

        #expect(applied == .manual(bps: bps))
    }

    @Test(arguments: [
        (UInt32(10), false),
        (UInt32(100), false),
        (UInt32(290), false),
        (UInt32(300), true),
        (UInt32(500), true),
    ] as [(UInt32, Bool)])
    func warning(bps: UInt32, expected: Bool) {
        let model = SwapSlippageViewModel(slippage: .manual(bps: bps)) { _ in }

        #expect((model.warningText != nil) == expected)
    }
}
