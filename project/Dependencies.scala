/**
 * Copyright (c) 2014-2023 Snowplow Analytics Ltd. All rights reserved.
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

import sbt._
import Keys._

object Dependencies {
  object V {
    // Scala
    val igluCore         = "1.1.2"
    val circe            = "0.14.3"
    val circeJackson     = "0.14.0"
    val jsonValidator    = "1.0.76"
    val catsParse        = "0.3.9"
    val libCompat        = "2.9.0"
    val jacksonDatabind  = "2.14.1"
    val dregex           = "0.7.0"

    // Scala (test only)
    val specs2           = "4.15.0"
    val scalaCheck       = "1.16.0"
  }

  object Libraries {
    // Scala
    val igluCoreJson4s   = "com.snowplowanalytics"      %% "iglu-core-json4s"       % V.igluCore
    val igluCoreCirce    = "com.snowplowanalytics"      %% "iglu-core-circe"        % V.igluCore
    val circeGeneric     = "io.circe"                   %% "circe-generic"          % V.circe
    val circeJackson     = "io.circe"                   %% "circe-jackson210"       % V.circeJackson
    val libCompat        = "org.scala-lang.modules"     %% "scala-collection-compat" % V.libCompat
    val catsParse        = "org.typelevel"              %% "cats-parse"             % V.catsParse
    val dregex           = "com.github.marianobarrios"  %% "dregex"                 % V.dregex
    // Java
    val jacksonDatabind  = "com.fasterxml.jackson.core" % "jackson-databind"        % V.jacksonDatabind
    val jsonValidator    = "com.networknt"              %  "json-schema-validator"  % V.jsonValidator
    // Scala (test only)
    val specs2           = "org.specs2"                 %% "specs2-core"            % V.specs2     % Test
    val specs2Cats       = "org.specs2"                 %% "specs2-cats"            % V.specs2     % Test
    val specs2Scalacheck = "org.specs2"                 %% "specs2-scalacheck"      % V.specs2     % Test
    val scalaCheck       = "org.scalacheck"             %% "scalacheck"             % V.scalaCheck % Test
    val circeLiteral     = "io.circe"                   %% "circe-literal"          % V.circe      % Test
    val circeParser      = "io.circe"                   %% "circe-parser"           % V.circe      % Test
  }
}
