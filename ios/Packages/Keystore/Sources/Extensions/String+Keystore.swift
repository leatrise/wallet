// Copyright (c). Gem Wallet. All rights reserved.

import Foundation
import Primitives

extension String {
    func v4KeystorePasswordBytes() throws -> Data {
        (try? Data.from(hex: self)) ?? Data(utf8)
    }

    func v3PasswordBytes() -> Data {
        Data(utf8)
    }
}
