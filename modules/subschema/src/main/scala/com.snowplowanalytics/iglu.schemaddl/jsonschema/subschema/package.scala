package com.snowplowanalytics.iglu.schemaddl.jsonschema.subschema

import com.snowplowanalytics.iglu.schemaddl.jsonschema.Schema
import com.snowplowanalytics.iglu.schemaddl.jsonschema.properties.ArrayProperty.AdditionalItems._
import com.snowplowanalytics.iglu.schemaddl.jsonschema.properties.ArrayProperty.Items._
import com.snowplowanalytics.iglu.schemaddl.jsonschema.properties.ArrayProperty.{MaxItems, MinItems}
import com.snowplowanalytics.iglu.schemaddl.jsonschema.properties.CommonProperties._
import com.snowplowanalytics.iglu.schemaddl.jsonschema.properties.CommonProperties.Type._
import com.snowplowanalytics.iglu.schemaddl.jsonschema.properties.NumberProperty.Maximum.{IntegerMaximum, NumberMaximum}
import com.snowplowanalytics.iglu.schemaddl.jsonschema.properties.NumberProperty.Minimum.{IntegerMinimum, NumberMinimum}
import com.snowplowanalytics.iglu.schemaddl.jsonschema.properties.ObjectProperty.AdditionalProperties._
import com.snowplowanalytics.iglu.schemaddl.jsonschema.properties.ObjectProperty.{Properties, Required}
import com.snowplowanalytics.iglu.schemaddl.jsonschema.properties.StringProperty.Pattern
import dregex.{Regex, Universe}
import io.circe.Json

import scala.annotation.tailrec

package object subschema {

  val any: Schema  = Schema.empty
  val none: Schema = Schema.empty.copy(not = Some(Not(any)))

  def isSubSchema(s1: Schema, s2: Schema): Compatibility =
    isSubType(simplify(canonicalize(s1)), simplify(canonicalize(s2)))

  @tailrec
  def canonicalize(s: Schema): Schema = {
    s.`type` match {
      case None           => if (s.`enum`.isDefined) canonicalize(canonicalizeEnum(s)) else s
      case Some(Null)     => s
      case Some(Boolean)  => canonicalizeBoolean(s)
      case Some(Integer)  => s
      case Some(Number)   => s
      case Some(String)   => s
      case Some(Object)   => canonicalizeObject(s)
      case Some(Array)    => canonicalizeArray(s)
      case Some(Union(_)) => canonicalize(canonicalizeUnion(s))
      case _              => s
    }
  }

  def canonicalizeBoolean(s: Schema): Schema =
    if (s.`enum`.isDefined) s else s.copy(`enum` = Some(Enum(List(Json.True, Json.False))))

  def canonicalizeEnum(s: Schema): Schema = {
    val splitByType: List[Json] => List[Schema] =
      _.map(inferType(_))
        .groupBy(_._1)
        .mapValues(_.flatMap(_._2))
        .mapValues(values => if (values.isEmpty) None else Some(Enum(values)))
        .toList
        .map({ case (t, e) => Schema.empty.copy(`type` = Some(t), `enum` = e)})

    s.`enum`
      .map(_.value)
      .map(splitByType(_))
      .map(anyOf => s.copy(`enum` = None, anyOf = Some(AnyOf(anyOf))))
      .getOrElse(s)
  }

  def canonicalizeObject(s: Schema): Schema =
    s.additionalProperties match {
      case Some(AdditionalPropertiesAllowed(allowed)) =>
        s.copy(additionalProperties = Some(AdditionalPropertiesSchema(if (allowed) any else none)))
      case _ => s
    }

  def canonicalizeArray(s: Schema): Schema =
    (s.items, s.additionalItems) match {
      // make sure items is only used for tuples and additionalItems is always a schema
      case (Some(ListItems(schema)), _) =>
        s.copy(items = Some(TupleItems(List.empty)), additionalItems = Some(AdditionalItemsSchema(schema)))
      case (Some(TupleItems(_)), Some(AdditionalItemsAllowed(allowed))) =>
        s.copy(additionalItems = Some(AdditionalItemsSchema(if (allowed) any else none)))
      case _ => s
    }

  def canonicalizeUnion(s: Schema): Schema = {
    s.`type` match {
      case Some(Union(types)) =>
        val anyOf = types.map(t => Schema.empty.copy(`type` = Some(t))).toList
        s.copy(`type` = None, anyOf = Some(AnyOf(anyOf)))
      case _ => s
    }
  }

  def inferType(j: Json): (Type, Option[Json]) =
    // enum values are plain JSON values, not schemas
    j.fold(
      jsonNull = Null -> None,
      jsonBoolean = v => Boolean -> Some(Json.fromBoolean(v)),
      jsonNumber = v => Number -> Some(Json.fromJsonNumber(v)),
      jsonString = v => String -> Some(Json.fromString(v)),
      jsonArray = v => Array -> Some(Json.fromValues(v)),
      jsonObject = v => Object -> Some(Json.fromJsonObject(v))
    )

  def simplify(s: Schema): Schema =
    (simplifyMultiValuedEnum _)
      .andThen(simplifyEnum)(s)

  def simplifyMultiValuedEnum(s: Schema): Schema =
    (s.`type`, s.`enum`) match {
      case (t@Some(typ), Some(Enum(values))) if typ != Boolean && values.length > 1 =>
        val anyOf = values.map(v => Schema.empty.copy(`type` = t, `enum` = Some(Enum(List(v))))).map(simplifyEnum(_))
        Schema.empty.copy(anyOf = Some(AnyOf(anyOf)))
      case _ => s
    }

  def simplifyEnum(s: Schema): Schema =
    (s.`type`, s.`enum`) match {
      case (t@Some(Null), Some(Enum(List(Json.Null)))) =>
        Schema.empty.copy(`type` = t, `enum` = None)
      case (t@Some(String), Some(Enum(v :: Nil))) =>
        Schema.empty.copy(`type` = t, pattern = v.asString.map(s => Pattern(s"^$s$$")))
      case (t@Some(Integer), Some(Enum(v :: Nil))) =>
        val intV = v.asNumber.flatMap(_.toBigInt)
        Schema.empty.copy(`type` = t, minimum = intV.map(IntegerMinimum(_)), maximum = intV.map(IntegerMaximum(_)))
      case (t@Some(Number), Some(Enum(v :: Nil))) =>
        val decV = v.asNumber.flatMap(_.toBigDecimal)
        Schema.empty.copy(`type` = t, minimum = decV.map(NumberMinimum(_)), maximum = decV.map(NumberMaximum(_)))
      case (t@Some(Array), Some(Enum(vArr :: Nil))) =>
        val maybeArr = vArr.asArray
        val arrSize = maybeArr.map(_.size)
        val tupleItems = maybeArr.map(xv => xv.map(schemaForEnumValue).map(simplifyEnum).toList)
        Schema.empty.copy(
          `type` = t,
          minItems = arrSize.map(MinItems(_)),
          maxItems = arrSize.map(MaxItems(_)),
          items = tupleItems.map(TupleItems(_))
        )
      case (t@Some(Object), Some(Enum(vObj :: Nil))) =>
        val maybeObj = vObj.asObject
        val props = maybeObj.map(_.toMap.mapValues(schemaForEnumValue).mapValues(simplifyEnum))
        Schema.empty.copy(
          `type` = t,
          required = maybeObj.map(_.keys.toList).map(Required(_)),
          additionalProperties = Some(AdditionalPropertiesSchema(none)),
          properties = props.map(x => Properties(x.toMap))
        )
      case _ => s
    }

  def schemaForEnumValue(v: Json): Schema = {
    val (t, value) = inferType(v)
    Schema.empty.copy(`type` = Some(t), `enum` = value.map(v => Enum(List(v))))
  }

  def isSubType(s1: Schema, s2: Schema): Compatibility = (s1, s2) match {
    case (_, `any`)                                                        => Compatible
    case (`none`, _)                                                       => Compatible
    case (s1, s2) if s1.`type`.contains(Null) && s1.`type` == s2.`type`    => Compatible
    case (s1, s2) if s1.`type`.contains(Boolean) && s1.`type` == s2.`type` => isBooleanSubType(s1, s2)
    case (s1, s2) if isNumber(s1) && isNumber(s2)                          => isNumberSubType(s1, s2)
    case (s1, s2) if s1.`type`.contains(String) && s1.`type` == s2.`type`  => isStringSubType(s1, s2)
    case (s1, s2) if s1.`type`.contains(Object) && s1.`type` == s2.`type`  => isObjectSubType(s1, s2)
    case (s1, s2) if s1.`type`.contains(Array) && s1.`type` == s2.`type`   => isArraySubType(s1, s2)
    case (s1, s2) if s1.`type` != s2.`type`                                => Incompatible
    case (s1, s2) if s1.anyOf.isDefined && s2.anyOf.isDefined              => anyOfSubType(s1, s2)
    case _ => Undecidable
  }

  def isBooleanSubType(s1: Schema, s2: Schema): Compatibility =
    (s1.`enum`.map(_.value.toSet), s2.`enum`.map(_.value.toSet)) match {
      case (maybeXe1, Some(xe2)) if !maybeXe1.getOrElse(Set(Json.True, Json.False)).subsetOf(xe2) => Incompatible
      case _ => Compatible
    }

  def isStringSubType(s1: Schema, s2: Schema): Compatibility = {
    val lengthRangeToPattern: Schema => String =
      s => (s.minLength, s.maxLength) match {
        case (Some(m1), Some(m2)) => s".{${m1.value},${m2.value}}"
        case (None, Some(m2))     => s".{0,${m2.value}}"
        case (Some(m1), None)     => s".{${m1.value},}"
        case (None, None)         => s".{0}"
      }

    val List(p1, pl1, p2, pl2) = Regex.compile(
      List(
        s1.pattern.map(_.value).map(stripAnchors).getOrElse(".*"),
        lengthRangeToPattern(s1),
        s2.pattern.map(_.value).map(stripAnchors).getOrElse(".*"),
        lengthRangeToPattern(s2)
      )
    )

    val compatibleFormat: Boolean =
      (s1.format, s2.format) match {
        case (Some(f1), Some(f2)) if f1 == f2 => true
        case (_, None) => true
        case _ => false
      }

    if (p1.isSubsetOf(p2) && pl1.isSubsetOf(pl2) && compatibleFormat)
      Compatible
    else
      Incompatible
  }

  def isNumberSubType(s1: Schema, s2: Schema): Compatibility = {
    val s1min = s1.minimum.map(_.getAsDecimal)
    val s1max = s1.maximum.map(_.getAsDecimal)
    val s2min = s2.minimum.map(_.getAsDecimal)
    val s2max = s2.maximum.map(_.getAsDecimal)

    (s1.`type`, s2.`type`, isSubRange((s1min, s1max), (s2min, s2max))) match {
      case (Some(t1), Some(t2), true) if t1 == t2 => Compatible
      case (Some(Integer), Some(Number), true)    => Compatible
      case _ => Incompatible
    }
  }

  def isObjectSubType(s1: Schema, s2: Schema): Compatibility = {
    val required: Schema => Set[String] =
      _.required.map(_.value).getOrElse(List.empty).toSet

    val properties: Schema => List[(String, Schema)] =
      _.properties.map(_.value).getOrElse(Map.empty).toList

    val patternProperties: Schema => List[(String, Schema)] =
      _.patternProperties.map(_.value).getOrElse(Map.empty).toList

    val additionalProperties: Schema => Schema =
      _.additionalProperties.collect({ case AdditionalPropertiesSchema(value) => value }).getOrElse(any)

    def stripAnchorsFromSchemaList(list: List[(String, Schema)]) =
      list.map{case (r, s) => (stripAnchors(r), s)}

    val p1: List[(String, Schema)] = stripAnchorsFromSchemaList(properties(s1))
    val p2: List[(String, Schema)] = stripAnchorsFromSchemaList(properties(s2))

    val pp1: List[(String, Schema)] = stripAnchorsFromSchemaList(patternProperties(s1))
    val pp2: List[(String, Schema)] = stripAnchorsFromSchemaList(patternProperties(s2))

    val ap1: Schema = additionalProperties(s1)
    val ap2: Schema = additionalProperties(s2)

    val matchAnythingString = ".*"
    val matchNothingString  = "(?!x)x"

    val universe = {
      val strRegexps = matchAnythingString :: matchNothingString :: (p1 ++ pp1 ++ p2 ++ pp2).map(_._1).map(stripAnchors)
      val parsed     = strRegexps.map(Regex.parse)
      val trees      = parsed.map(_.tree)
      new Universe(trees, parsed.head.norm)
    }

    def compileInUniverse(regexp: String): Regex =
      Regex.compileParsed(Regex.parse(regexp), universe)

    def canonicalizeSchema(
        properties: List[(String, Schema)],
        patternProperties: List[(String, Schema)],
        additionalProperties: Schema
    ): List[(Regex, Schema)] = {
      val matchAnything = compileInUniverse(matchAnythingString)
      val matchNothing  = compileInUniverse(matchNothingString)

      val union: List[Regex] => Regex = _.fold[Regex](matchNothing)(_ union _)

      val p1Regexes     = properties.map { case (raw, _) => compileInUniverse(raw) }
      val rawPp1Regexes = patternProperties.map { case (raw, _) => compileInUniverse(raw) }

      val pp1Regexes = rawPp1Regexes.map(raw => raw.diff(union(p1Regexes)))
      val ap1Regex   = matchAnything.diff(union(p1Regexes ++ rawPp1Regexes))

      (ap1Regex +: (p1Regexes ++ pp1Regexes))
        .zip(additionalProperties +: (properties ++ patternProperties).map(_._2))
    }

    val pp1WithRegexes: List[(Regex, Schema)] = canonicalizeSchema(p1, pp1, ap1)
    val pp2WithRegexes: List[(Regex, Schema)] = canonicalizeSchema(p2, pp2, ap2)

    def arePatternPropertiesOverlapping(patternProperties: List[(String, Schema)]): Boolean = {
      val compiledRegexes = patternProperties.map { case (raw, _) => compileInUniverse(raw) }
      compiledRegexes match {
        case Nil => false
        case _   =>
          compiledRegexes
            .combinations(2)
            .exists {
              case r1 :: r2 :: Nil => r1.doIntersect(r2)
              case _               => false
            }
      }
    }

    val patternPropertiesOverlaps =
      arePatternPropertiesOverlapping(pp1) || arePatternPropertiesOverlapping(pp2)

    val subSchemaCheckOverlappingOnly: List[Compatibility] =
      for { (r1, s1) <- pp1WithRegexes; (r2, s2) <- pp2WithRegexes; if r1.doIntersect(r2) } yield isSubSchema(s1, s2)

    (required(s2).subsetOf(required(s1)), subSchemaCheckOverlappingOnly, patternPropertiesOverlaps) match {
      case (_, _, true)  => Undecidable // Until we implement XP-1365
      case (false, _, _) => Incompatible
      case (true, xs, _) => combineAll(combineAnd)(xs.head, xs.tail: _*)
    }
  }

  def isArraySubType(s1: Schema, s2: Schema): Compatibility = {
    val items: Schema => List[Schema] =
      _.items.collect({ case TupleItems(schemas) => schemas }).getOrElse(List.empty)

    val additionalItems: Schema => Schema =
      _.additionalItems.collect({ case AdditionalItemsSchema(schema) => schema }).getOrElse(any)

    val i1 = items(s1)
    val i2 = items(s2)

    val ai1 = additionalItems(s1)
    val ai2 = additionalItems(s2)

    val max = Math.max(i1.length, i2.length)
    val zippedItems = i1.padTo(max + 1, ai1).zip(i2.padTo(max + 1, ai2))
    val subSchemaCheckZipped = zippedItems.map((isSubSchema _).tupled)

    val s1min = s1.minItems.map(v => BigDecimal(v.value))
    val s1max = s1.maxItems.map(v => BigDecimal(v.value))
    val s2min = s2.minItems.map(v => BigDecimal(v.value))
    val s2max = s2.maxItems.map(v => BigDecimal(v.value))

    (isSubRange((s1min, s1max), (s2min, s2max)), subSchemaCheckZipped) match {
      case (false, _) => Incompatible
      case (true, xs) => combineAll(combineAnd)(xs.head, xs.tail:_*)
    }
  }

  def anyOfSubType(s1: Schema, s2: Schema): Compatibility =
    (s1.anyOf, s2.anyOf) match {
      case (Some(AnyOf(ao1)), Some(AnyOf(ao2))) =>
        val h :: t = ao1.map(i => {
          val h :: t = ao2.map(j => isSubType(i, j))
          combineAll(combineOr)(h, t:_*)
        })
        combineAll(combineAnd)(h, t:_*)
      case _ =>
        Undecidable
    }

  def isNumber(s: Schema): Boolean =
    s.`type`.contains(Integer) || s.`type`.contains(Number)

  def stripAnchors(r: String): String = r.stripPrefix("^").stripSuffix("$")

  def isSubRange(r1: (Option[BigDecimal], Option[BigDecimal]), r2: (Option[BigDecimal], Option[BigDecimal])): Boolean = {
    val minCheck = (r1._1, r2._1) match {
      case (None, Some(_))    => false
      case (Some(_), None)    => true
      case (Some(l), Some(r)) => l >= r
      case (None, None)       => true
    }

    val maxCheck = (r1._2, r2._2) match {
      case (None, Some(_))    => false
      case (Some(_), None)    => true
      case (Some(l), Some(r)) => l <= r
      case (None, None)       => true
    }

    minCheck && maxCheck
  }

  // Semigroup for "AND" on Compatibility
  def combineAnd(c1: Compatibility, c2: Compatibility): Compatibility =
    (c1, c2) match {
      case (Compatible, Compatible) => Compatible
      case (Incompatible, _) | (_, Incompatible) => Incompatible
      case _ => Undecidable
    }

  // Semigroup for "OR" on Compatibility
  def combineOr(c1: Compatibility, c2: Compatibility): Compatibility =
    (c1, c2) match {
      case (Compatible, _) | (_, Compatible) => Compatible
      case (Incompatible, Incompatible) => Incompatible
      case _ => Undecidable
    }

  // Emulates a NonEmptyList without relying on cats. Required because Compatibility cannot form a Monoid since
  // an "empty" element doesn't exist
  def combineAll(op: (Compatibility, Compatibility) => Compatibility)(c1: Compatibility, c2: Compatibility*): Compatibility =
    (c1 +: c2.toList).reduce[Compatibility](op)

}
