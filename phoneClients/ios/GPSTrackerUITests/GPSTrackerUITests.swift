import XCTest

final class GPSTrackerUITests: XCTestCase {
    override func setUpWithError() throws {
        continueAfterFailure = false
    }

    @MainActor
    func testHomeTrackingAndStatsFlowSmoke() throws {
        let app = launchApp(resetState: true)

        XCTAssertTrue(app.otherElements["home.map"].waitForExistence(timeout: 5))
        XCTAssertTrue(app.buttons["home.open.settings"].exists)
        XCTAssertTrue(app.buttons["home.open.stats"].exists)
        let toggle = app.buttons["home.toggle.tracking"]
        XCTAssertTrue(toggle.waitForExistence(timeout: 5))
        XCTAssertTrue(app.staticTexts["home.upload.status"].exists)

        let initialLabel = toggle.label
        toggle.tap()
        XCTAssertNotEqual(toggle.label, initialLabel)

        let uploadStatus = app.staticTexts["home.upload.status"]
        XCTAssertTrue(uploadStatus.waitForExistence(timeout: 5))
        XCTAssertTrue(
            waitForLabelPrefix(
                of: uploadStatus,
                prefix: "Точек в буфере:",
                timeout: 8
            )
        )

        let statsButton = app.buttons["home.open.stats"]
        XCTAssertTrue(statsButton.waitForExistence(timeout: 5))
        statsButton.tap()

        XCTAssertTrue(app.staticTexts["stats.value.totalDistance"].waitForExistence(timeout: 5))
        XCTAssertTrue(app.staticTexts["stats.value.collected"].exists)
        XCTAssertTrue(app.staticTexts["stats.value.uploaded"].exists)
        XCTAssertTrue(app.staticTexts["stats.value.lastUpload"].exists)

        let done = app.buttons["stats.done"]
        XCTAssertTrue(done.waitForExistence(timeout: 5))
        done.tap()

        XCTAssertTrue(app.buttons["home.toggle.tracking"].waitForExistence(timeout: 5))
        toggle.tap()
        XCTAssertEqual(toggle.label, initialLabel)
    }

    @MainActor
    func testPendingPermissionStateShowsExplicitHomeStatus() throws {
        let app = launchApp(
            resetState: true,
            locationAuth: "notDetermined"
        )

        let state = app.staticTexts["home.location.state"]
        let provider = app.staticTexts["home.location.provider"]
        let age = app.staticTexts["home.location.age"]
        let accuracy = app.staticTexts["home.location.accuracy"]

        XCTAssertTrue(state.waitForExistence(timeout: 5))
        XCTAssertEqual(state.label, "Нет доступа к геолокации")
        XCTAssertEqual(provider.label, "Ожидается разрешение")
        XCTAssertEqual(age.label, "Нет данных")
        XCTAssertEqual(accuracy.label, "Нет данных")
    }

    @MainActor
    func testDeniedPermissionStateShowsExplicitHomeStatus() throws {
        let app = launchApp(
            resetState: true,
            locationAuth: "denied"
        )

        let state = app.staticTexts["home.location.state"]
        let provider = app.staticTexts["home.location.provider"]
        let age = app.staticTexts["home.location.age"]
        let accuracy = app.staticTexts["home.location.accuracy"]

        XCTAssertTrue(state.waitForExistence(timeout: 5))
        XCTAssertEqual(state.label, "Доступ к геолокации отключён")
        XCTAssertEqual(provider.label, "Откройте системные настройки")
        XCTAssertEqual(age.label, "Нет данных")
        XCTAssertEqual(accuracy.label, "Нет данных")
    }

    @MainActor
    func testSettingsValidationAndPersistenceFlow() throws {
        let app = launchApp(resetState: true)
        openSettings(in: app)

        let save = app.buttons["settings.save"]
        XCTAssertFalse(save.isEnabled)

        let trackerField = app.textFields["settings.field.trackerIdentifier"]
        XCTAssertTrue(trackerField.waitForExistence(timeout: 5))
        clearAndType(trackerField, text: "", in: app)
        XCTAssertFalse(save.isEnabled)

        clearAndType(trackerField, text: "1234567890", in: app)
        let uploadIntervalField = app.textFields["settings.field.uploadInterval"]
        clearAndType(uploadIntervalField, text: "7", in: app)

        XCTAssertTrue(save.isEnabled)
        save.tap()

        app.terminate()

        let relaunchedApp = launchApp(resetState: false)
        openSettings(in: relaunchedApp)
        XCTAssertEqual(textFieldValue(relaunchedApp.textFields["settings.field.uploadInterval"]), "7")
    }

    @MainActor
    func testClosingSettingsWithoutSavingKeepsPersistedValues() throws {
        let app = launchApp(resetState: true)
        openSettings(in: app)

        clearAndType(app.textFields["settings.field.bufferDistanceInterval"], text: "250", in: app)
        app.buttons["settings.cancel"].tap()

        openSettings(in: app)
        XCTAssertEqual(textFieldValue(app.textFields["settings.field.bufferDistanceInterval"]), "100")
    }

    @MainActor
    func testOfflineUploadShowsFriendlyStatus() throws {
        let app = launchApp(resetState: true, uploadMode: "offline")
        let toggle = app.buttons["home.toggle.tracking"]
        XCTAssertTrue(toggle.waitForExistence(timeout: 5))

        toggle.tap()
        toggle.tap()

        let uploadStatus = app.staticTexts["home.upload.status"]
        XCTAssertTrue(uploadStatus.waitForExistence(timeout: 5))
        XCTAssertFalse(uploadStatus.label.contains("NSURLError"))
        XCTAssertFalse(uploadStatus.label.contains("kCF"))
    }

    // MARK: - Helpers

    @MainActor
    private func launchApp(
        resetState: Bool,
        uploadMode: String = "success",
        locationAuth: String? = nil
    ) -> XCUIApplication {
        let app = XCUIApplication()
        app.launchArguments += ["UITEST_MODE"]
        if resetState {
            app.launchArguments += ["UITEST_RESET_STATE"]
        }
        app.launchEnvironment["UITEST_MODE"] = "1"
        if resetState {
            app.launchEnvironment["UITEST_RESET_STATE"] = "1"
        }
        app.launchEnvironment["UITEST_UPLOAD_MODE"] = uploadMode
        if let locationAuth {
            app.launchEnvironment["UITEST_LOCATION_AUTH"] = locationAuth
        }
        app.launch()
        return app
    }

    @MainActor
    private func openSettings(in app: XCUIApplication) {
        let button = app.buttons["home.open.settings"]
        XCTAssertTrue(button.waitForExistence(timeout: 5))
        button.tap()

        let saveButton = app.buttons["settings.save"]
        if !saveButton.waitForExistence(timeout: 5) {
            button.tap()
        }
        XCTAssertTrue(saveButton.waitForExistence(timeout: 5))
    }

    @MainActor
    private func clearAndType(_ element: XCUIElement, text: String, in app: XCUIApplication) {
        XCTAssertTrue(element.waitForExistence(timeout: 5))
        focus(element, in: app)

        let currentValue = textFieldValue(element)
        if !currentValue.isEmpty {
            element.typeText(String(repeating: XCUIKeyboardKey.delete.rawValue, count: currentValue.count))
        }
        if !text.isEmpty {
            element.typeText(text)
        }
    }

    @MainActor
    private func focus(_ element: XCUIElement, in app: XCUIApplication) {
        for _ in 0..<3 {
            element.tap()
            if app.keyboards.firstMatch.waitForExistence(timeout: 1) {
                return
            }

            element.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.5)).tap()
            if app.keyboards.firstMatch.waitForExistence(timeout: 1) {
                return
            }
        }

        XCTFail("Failed to focus text field \(element.identifier)")
    }

    private func textFieldValue(_ element: XCUIElement) -> String {
        element.value as? String ?? ""
    }

    private func waitForLabelPrefix(
        of element: XCUIElement,
        prefix: String,
        timeout: TimeInterval
    ) -> Bool {
        let predicate = NSPredicate { _, _ in
            guard element.exists else { return false }
            return element.label.hasPrefix(prefix)
        }
        let expectation = XCTNSPredicateExpectation(predicate: predicate, object: element)
        return XCTWaiter().wait(for: [expectation], timeout: timeout) == .completed
    }
}
