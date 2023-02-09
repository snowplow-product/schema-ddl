package com.snowplowanalytics.iglu.schemaddl.redshift.internal

import cats.syntax.show._
import com.snowplowanalytics.iglu.core.SchemaKey
import com.snowplowanalytics.iglu.schemaddl.redshift.internal.Migrations._
import com.snowplowanalytics.iglu.schemaddl.redshift.internal.ShredModelEntry.ColumnType._

import scala.collection.immutable.TreeMap
import scala.math.Ordered.orderingToOrdered

case class Migrations(migrations: TreeMap[SchemaKey, List[Migrations.NonBreaking]]) {

  def values: Iterable[NonBreaking] = migrations.values.flatten

  def inTransaction(maybeBase: Option[SchemaKey]): List[Migrations.ColumnAddition] =
    migrations
      .dropWhile { case (k, _) => maybeBase.exists(_ >= k) }
      .values
      .flatten
      .collect { case a: Migrations.ColumnAddition => a }
      .toList

  def outTransaction(maybeBase: Option[SchemaKey]): List[Migrations.VarcharExtension] =
    migrations
      .dropWhile { case (k, _) => maybeBase.exists(_ >= k) }
      .values
      .flatten
      .collect { case a: Migrations.VarcharExtension => a }
      .toList


  def toSql(tableName: String, dbSchema: String, maybeBase: Option[SchemaKey]): String =
    s"""|-- WARNING: only apply this file to your database if the following SQL returns the expected:
        |--
        |-- SELECT pg_catalog.obj_description(c.oid) FROM pg_catalog.pg_class c WHERE c.relname = '$tableName';
        |--  obj_description
        |-- -----------------
        |--  ${migrations.lastKey.toSchemaUri}
        |--  (1 row)
        |
        |""".stripMargin +
      outTransaction(maybeBase).map { case Migrations.VarcharExtension(old, newEntry) =>
        s"""  ALTER TABLE $tableName
           |     ALTER COLUMN "${old.columnName}" TYPE ${newEntry.columnType.show};
           |
           |""".stripMargin +
          (inTransaction(maybeBase).map { case Migrations.ColumnAddition(column) =>
            s"""  ALTER TABLE $tableName
               |     ADD COLUMN "${column.columnName}" ${column.columnType.show} ${column.compressionEncoding.show};
               |
               |""".stripMargin
          } match {
            case Nil => s"""|-- NO ADDED COLUMNS CAN BE EXPRESSED IN SQL MIGRATION
                          |
                          |COMMENT ON TABLE  $dbSchema.$tableName IS '${migrations.lastKey.toSchemaUri}';
                          |""".stripMargin
            case h :: t => s"""BEGIN TRANSACTION;
                              |
                              |${(h :: t).mkString}
                              |
                              |  COMMENT ON TABLE  $dbSchema.$tableName IS '${migrations.lastKey.toSchemaUri}';
                              |  
                              |END TRANSACTION;""".stripMargin
          })
      }.mkString

  def ++(that: Migrations): Migrations = Migrations(migrations ++ that.migrations)
}

object Migrations {
  def apply(schemaKey: SchemaKey, migrations: List[Migrations.NonBreaking]): Migrations = Migrations(TreeMap((schemaKey, migrations))
  )

  implicit val ord: Ordering[SchemaKey] = SchemaKey.ordering

  sealed trait NonBreaking extends Product with Serializable

  case class VarcharExtension(old: ShredModelEntry, newEntry: ShredModelEntry) extends NonBreaking

  case class ColumnAddition(column: ShredModelEntry) extends NonBreaking

  case object NoChanges extends NonBreaking

  sealed trait Breaking extends Product with Serializable

  case class IncompatibleTypes(old: ShredModelEntry, changed: ShredModelEntry) extends Breaking

  case class IncompatibleEncoding(old: ShredModelEntry, changed: ShredModelEntry) extends Breaking

  case class NullableRequired(old: ShredModelEntry) extends Breaking
}