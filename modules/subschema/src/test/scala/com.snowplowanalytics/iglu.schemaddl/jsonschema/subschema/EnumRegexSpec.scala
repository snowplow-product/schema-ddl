package com.snowplowanalytics.iglu.schemaddl.jsonschema.subschema

import com.snowplowanalytics.iglu.schemaddl.jsonschema.Schema
import com.snowplowanalytics.iglu.schemaddl.jsonschema.properties.CommonProperties.Type._
import com.snowplowanalytics.iglu.schemaddl.jsonschema.properties.CommonProperties._
import com.snowplowanalytics.iglu.schemaddl.jsonschema.subschema.subschema._
import io.circe.Json
import org.specs2.Specification

class EnumRegexSpec extends Specification with org.specs2.specification.Tables { def is = s2"""
  EnumRegex
  ${
    "placeholderExpr"                 | "result"    |>
    "@abc"                            ! Compatible  |
    "@@abc"                           ! Compatible  |
    "a@bc"                            ! Compatible  |
    "a@@bc"                           ! Compatible  |
    "abc@"                            ! Compatible  |
    "abc@@"                           ! Compatible  |
    { (placeholderExpr, result) =>

      val exprs = "\\^$.|?*+()[]{}😊".toList.map { c: Char =>
        Json.fromString(placeholderExpr.replace("@", c.toString))
      }.toList

      val s = Schema.empty.copy(
        `type` = Some(String),
        `enum` = Some(Enum(exprs))
      )

      isSubSchema(s, s) mustEqual result
    }
  }"""
}


