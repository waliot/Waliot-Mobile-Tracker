//
//  LanguageManager.swift
//  GpsTracker
//
//  Created by Ivan Muratov on 17.10.2025.
//

import SwiftUI

final class LanguageManager: ObservableObject {
    private static let processInfo = ProcessInfo.processInfo

    @Published var code: String {
        didSet {
            UserDefaults.standard.set(code, forKey: "app.lang")
            objectWillChange.send()
        }
    }

    var locale: Locale { .init(identifier: code) }

    init() {
        if let forcedCode = Self.processInfo.environment["UITEST_APP_LANG"], !forcedCode.isEmpty {
            UserDefaults.standard.set(forcedCode, forKey: "app.lang")
            self.code = forcedCode
            return
        }

        UserDefaults.standard.register(defaults: ["app.lang": "ru"])
        self.code = UserDefaults.standard.string(forKey: "app.lang") ?? "ru"
    }
}
