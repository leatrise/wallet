// Copyright (c). Gem Wallet. All rights reserved.

import Localization
import Style
import SwiftUI

public struct TransactionSwapProgressItemModel: Equatable {
    public struct Step: Equatable {
        public enum Status: Equatable {
            case completed
            case pending
            case failed
        }

        public let title: String
        public let subtitle: String
        public let status: Status

        public init(
            title: String,
            subtitle: String,
            status: Status,
        ) {
            self.title = title
            self.subtitle = subtitle
            self.status = status
        }
    }

    public let transfer: Step
    public let swap: Step

    public init(
        transfer: Step,
        swap: Step,
    ) {
        self.transfer = transfer
        self.swap = swap
    }
}

extension TransactionSwapProgressItemModel.Step.Status {
    var label: String {
        switch self {
        case .completed: Localized.Transaction.Status.completed
        case .pending: Localized.Transaction.Status.pending
        case .failed: Localized.Transaction.Status.failed
        }
    }

    var color: Color {
        switch self {
        case .completed: Colors.green
        case .pending: Colors.orange
        case .failed: Colors.red
        }
    }

    var background: Color {
        color.opacity(.light)
    }

    var lineColor: Color {
        switch self {
        case .completed: Colors.green
        case .pending, .failed: Colors.gray.opacity(.medium)
        }
    }

    var markerBackground: Color {
        switch self {
        case .completed, .failed: background
        case .pending: .clear
        }
    }
}
