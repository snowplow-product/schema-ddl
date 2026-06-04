/*
 * Copyright (c) 2012-2023 Snowplow Analytics Ltd. All rights reserved.
 *
 * This program is licensed to you under the Apache License Version 2.0,
 * and you may not use this file except in compliance with the Apache License Version 2.0.
 * You may obtain a copy of the Apache License Version 2.0 at http://www.apache.org/licenses/LICENSE-2.0.
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the Apache License Version 2.0 is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the Apache License Version 2.0 for the specific language governing permissions and limitations there under.
 */
package com.snowplowanalytics.iglu.schemaddl
package jsonschema

import org.specs2.Specification

class AllLintersMapSpec extends Specification { def is = s2"""
  allLintersMap contains every linter keyed by its name $e1
  allLintersMap is keyed by getName $e2
  """

  private val expectedNames: Set[String] = Set(
    "rootObject",
    "numericMinimumMaximum",
    "stringMinMaxLength",
    "stringMaxLengthRange",
    "arrayMinMaxItems",
    "numericProperties",
    "stringProperties",
    "arrayProperties",
    "objectProperties",
    "requiredPropertiesExist",
    "unknownFormats",
    "numericMinMax",
    "stringLength",
    "optionalNull",
    "description",
    "schemaUri",
    "bqDisallowedCharacters",
    "bqIllegalStart"
  )

  def e1 = Linter.allLintersMap.keySet must_== expectedNames

  def e2 = Linter.allLintersMap.toList must contain { (kv: (String, Linter)) =>
    kv._1 must_== kv._2.getName
  }.forall
}
