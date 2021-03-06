//
// ModelResponse.swift
//
// Generated by openapi-generator
// https://openapi-generator.tech
//

import Foundation
#if canImport(AnyCodable)
import AnyCodable
#endif

/** Просто ответ Просто запрос TODO: добавить все обрабатываемые типы  open api и коллекции с required&#x3D;false  */
public struct ModelResponse: Codable, Hashable {

    /** Самое обычное описание для поля */
    public var firstField: String
    /** Самое обычное описание для поля */
    public var secondField: String?
    @available(*, deprecated, message: "This property is deprecated.")
    public var thirdField: Int?

    public init(firstField: String, secondField: String? = nil, thirdField: Int? = nil) {
        self.firstField = firstField
        self.secondField = secondField
        self.thirdField = thirdField
    }

    public enum CodingKeys: String, CodingKey, CaseIterable {
        case firstField
        case secondField = "second_field"
        case thirdField
    }

    // Encodable protocol methods

    public func encode(to encoder: Encoder) throws {
        var container = encoder.container(keyedBy: CodingKeys.self)
        try container.encode(firstField, forKey: .firstField)
        try container.encodeIfPresent(secondField, forKey: .secondField)
        try container.encodeIfPresent(thirdField, forKey: .thirdField)
    }
}

