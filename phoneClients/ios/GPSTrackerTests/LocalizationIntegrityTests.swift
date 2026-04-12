import Foundation
import Testing

struct LocalizationIntegrityTests {
    @Test
    func localizableFilesHaveMatchingKeySets() throws {
        let resourcesDirectory = try projectRoot()
            .appending(path: "GPSTracker")
            .appending(path: "Resources")
        let englishKeys = try localizationKeys(
            at: resourcesDirectory
                .appending(path: "en.lproj")
                .appending(path: "Localizable.strings")
        )
        let russianKeys = try localizationKeys(
            at: resourcesDirectory
                .appending(path: "ru.lproj")
                .appending(path: "Localizable.strings")
        )

        #expect(englishKeys == russianKeys)
        #expect(englishKeys.isEmpty == false)
    }

    @Test
    func sourceReferencedLocalizationKeysExistInBothLanguages() throws {
        let root = try projectRoot()
        let resourcesDirectory = root.appending(path: "GPSTracker").appending(path: "Resources")
        let englishKeys = try localizationKeys(
            at: resourcesDirectory
                .appending(path: "en.lproj")
                .appending(path: "Localizable.strings")
        )
        let russianKeys = try localizationKeys(
            at: resourcesDirectory
                .appending(path: "ru.lproj")
                .appending(path: "Localizable.strings")
        )
        let referencedKeys = try referencedLocalizationKeys(
            in: root.appending(path: "GPSTracker")
        )

        let missingEnglish = referencedKeys.subtracting(englishKeys)
        let missingRussian = referencedKeys.subtracting(russianKeys)

        #expect(missingEnglish.isEmpty, "Missing English keys: \(missingEnglish.sorted())")
        #expect(missingRussian.isEmpty, "Missing Russian keys: \(missingRussian.sorted())")
    }

    @Test
    func infoPlistLocalizationFilesContainLocalizedDisplayNamesAndLocationUsageText() throws {
        let appDirectory = try projectRoot().appending(path: "GPSTracker")
        let englishInfoPlistStrings = try infoPlistStrings(
            at: appDirectory.appending(path: "en.lproj").appending(path: "InfoPlist.strings")
        )
        let russianInfoPlistStrings = try infoPlistStrings(
            at: appDirectory.appending(path: "ru.lproj").appending(path: "InfoPlist.strings")
        )

        let requiredKeys = Set([
            "CFBundleDisplayName",
            "NSLocationAlwaysAndWhenInUseUsageDescription",
            "NSLocationWhenInUseUsageDescription"
        ])

        #expect(requiredKeys.isSubset(of: Set(englishInfoPlistStrings.keys)))
        #expect(requiredKeys.isSubset(of: Set(russianInfoPlistStrings.keys)))
        #expect(englishInfoPlistStrings["CFBundleDisplayName"] == "WALIOT.Tracker")
        #expect(russianInfoPlistStrings["CFBundleDisplayName"] == "WALIOT.Трекер")
    }
}

private func projectRoot(filePath: String = #filePath) throws -> URL {
    URL(fileURLWithPath: filePath)
        .deletingLastPathComponent()
        .deletingLastPathComponent()
}

private func localizationKeys(at fileURL: URL) throws -> Set<String> {
    Set(try parseStringsFile(at: fileURL).keys)
}

private func infoPlistStrings(at fileURL: URL) throws -> [String: String] {
    try parseStringsFile(at: fileURL)
}

private func parseStringsFile(at fileURL: URL) throws -> [String: String] {
    let content = try String(contentsOf: fileURL, encoding: .utf8)
    let pattern = #"^\s*"([^"]+)"\s*=\s*"((?:\\.|[^"])*)";\s*$"#
    let regex = try NSRegularExpression(pattern: pattern, options: [.anchorsMatchLines])
    let nsContent = content as NSString

    var result: [String: String] = [:]
    for match in regex.matches(in: content, range: NSRange(location: 0, length: nsContent.length)) {
        guard match.numberOfRanges == 3 else { continue }
        let key = nsContent.substring(with: match.range(at: 1))
        let rawValue = nsContent.substring(with: match.range(at: 2))
        let value = rawValue.replacingOccurrences(of: #"\""#, with: #"""#)
        result[key] = value
    }
    return result
}

private func referencedLocalizationKeys(in sourceDirectory: URL) throws -> Set<String> {
    let fileManager = FileManager.default
    let enumerator = fileManager.enumerator(
        at: sourceDirectory,
        includingPropertiesForKeys: [.isRegularFileKey],
        options: [.skipsHiddenFiles]
    )

    let callsiteFragments = [
        "String(localized:",
        "Text(",
        "Button(",
        "Toggle(",
        "Picker(",
        "sectionHeader(",
        "LabeledContent("
    ]
    let keyPattern = try NSRegularExpression(pattern: #""([a-z][A-Za-z0-9_.-]+)""#)
    var keys = Set<String>()

    while let fileURL = enumerator?.nextObject() as? URL {
        guard fileURL.pathExtension == "swift" else { continue }
        let source = try String(contentsOf: fileURL, encoding: .utf8)
        for line in source.split(separator: "\n", omittingEmptySubsequences: false) {
            let lineString = String(line)
            guard callsiteFragments.contains(where: { lineString.contains($0) }) else {
                continue
            }

            let nsLine = lineString as NSString
            let matches = keyPattern.matches(
                in: lineString,
                range: NSRange(location: 0, length: nsLine.length)
            )
            for match in matches where match.numberOfRanges > 1 {
                let key = nsLine.substring(with: match.range(at: 1))
                if key.contains(".") {
                    keys.insert(key)
                }
            }
        }
    }

    return keys
}
