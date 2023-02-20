package edu.rit.cs.mmior.jsonoid.discovery
package schemas

import scala.reflect._

import org.json4s._

object BooleanSchema {
  def apply(value: Boolean)(implicit p: JsonoidParams): BooleanSchema = {
    BooleanSchema(
      BooleanSchema.AllProperties.mergeValue(value)(p)
    )
  }

  val MinProperties: SchemaProperties[Boolean] =
    SchemaProperties.empty[Boolean]

  val AllProperties: SchemaProperties[Boolean] =
    SchemaProperties.empty[Boolean]
}

/** Represents Boolean values in JSON Schema.
  */
final case class BooleanSchema(
    override val properties: SchemaProperties[Boolean] =
      BooleanSchema.AllProperties
) extends JsonSchema[Boolean] {
  override val schemaType = "boolean"

  override val validTypes: Set[Class[_]] = Set(classOf[JBool])

  override def mergeSameType(mergeType: MergeType)(implicit
      p: JsonoidParams
  ): PartialFunction[JsonSchema[_], JsonSchema[_]] = {
    case other @ BooleanSchema(otherProperties) =>
      BooleanSchema(properties.merge(otherProperties, mergeType))
  }

  @SuppressWarnings(Array("org.wartremover.warts.NonUnitStatements"))
  override def copy(properties: SchemaProperties[Boolean]): BooleanSchema = {
    val newSchema = BooleanSchema(properties)
    newSchema.definitions ++= this.definitions

    newSchema
  }

  override def collectAnomalies[S <: JValue](
      value: S,
      path: String
  )(implicit tag: ClassTag[S]) = {
    value match {
      case JBool(_) => Seq.empty
      case _        => Seq(Anomaly(path, "expected boolean type", Fatal))
    }
  }
}
