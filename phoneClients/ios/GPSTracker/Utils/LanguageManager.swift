//
//  LanguageManager.swift
//  GpsTracker
//
//  Created by Ivan Muratov on 17.10.2025.
//

import SwiftUI

final class LanguageManager: ObservableObject {
    @Published var code: String {
        didSet {
            UserDefaults.standard.set(code, forKey: "app.lang")
            objectWillChange.send()
        }
    }

    var locale: Locale { .init(identifier: code) }

    init() {
        UserDefaults.standard.register(defaults: ["app.lang": "ru"])
        self.code = UserDefaults.standard.string(forKey: "app.lang") ?? "ru"
    }
}
