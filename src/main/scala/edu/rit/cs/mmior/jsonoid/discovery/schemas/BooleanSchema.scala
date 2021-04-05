package edu.rit.cs.mmior.jsonoid.discovery
package schemas

object BooleanSchema {
  def apply(value: Boolean): BooleanSchema = {
    BooleanSchema(SchemaProperties().merge(value))
  }
}

final case class BooleanSchema(
    override val properties: SchemaProperties[Boolean] = SchemaProperties.empty
) extends JsonSchema[Boolean] {
  override val schemaType = "boolean"

  def mergeSameType: PartialFunction[JsonSchema[_], JsonSchema[_]] = {
    case other @ BooleanSchema(otherProperties) =>
      BooleanSchema(properties.merge(otherProperties))
  }
}
