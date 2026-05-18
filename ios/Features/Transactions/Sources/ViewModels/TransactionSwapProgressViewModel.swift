// Copyright (c). Gem Wallet. All rights reserved.

import BigInt
import Components
import Formatters
import Gemstone
import GemstonePrimitives
import Localization
import Primitives

struct TransactionSwapProgressViewModel {
    private let transaction: TransactionExtended

    init(transaction: TransactionExtended) {
        self.transaction = transaction
    }
}

// MARK: - ItemModelProvidable

extension TransactionSwapProgressViewModel: ItemModelProvidable {
    var itemModel: TransactionItemModel {
        guard let progress = progressModel else {
            return .empty
        }
        return .swapProgress(progress)
    }
}

// MARK: - Private

extension TransactionSwapProgressViewModel {
    private var progressModel: TransactionSwapProgressItemModel? {
        guard
            transaction.transaction.type == .swap,
            let metadata = transaction.transaction.metadata?.decode(TransactionSwapMetadata.self),
            let providerId = metadata.provider,
            let swapProvider = SwapProvider(rawValue: providerId),
            let fromAsset = transaction.assets.first(where: { $0.id == metadata.fromAsset })
        else {
            return nil
        }

        let provider = SwapProviderConfig.fromString(id: swapProvider.rawValue).inner()
        guard provider.mode.isCrossChain else {
            return nil
        }

        let chainName = Asset(fromAsset.id.chain).name
        let amount = ValueFormatter.auto.string(BigInt.fromString(metadata.fromValue), asset: fromAsset)

        return TransactionSwapProgressItemModel(
            transfer: TransactionSwapProgressItemModel.Step(
                title: Localized.Transfer.title,
                subtitle: "\(amount) (\(chainName))",
                status: .completed,
            ),
            swap: TransactionSwapProgressItemModel.Step(
                title: Localized.Wallet.swap,
                subtitle: provider.name,
                status: transaction.transaction.state.swapProgressStatus,
            ),
        )
    }
}

private extension Primitives.TransactionState {
    var swapProgressStatus: TransactionSwapProgressItemModel.Step.Status {
        switch self {
        case .pending, .inTransit: .pending
        case .confirmed: .completed
        case .failed, .reverted: .failed
        }
    }
}

private extension SwapperProviderMode {
    var isCrossChain: Bool {
        switch self {
        case .crossChain, .bridge, .omniChain: true
        case .onChain: false
        }
    }
}
