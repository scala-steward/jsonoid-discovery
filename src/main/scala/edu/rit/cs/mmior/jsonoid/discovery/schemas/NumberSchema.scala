package edu.rit.cs.mmior.jsonoid.discovery
package schemas

import scalaz._
import org.json4s.JsonDSL._
import org.json4s._
import Scalaz._

import Helpers._


object NumberSchema {
  def apply(value: BigDecimal): NumberSchema = {
    NumberSchema(SchemaProperties(
      MinNumValueProperty(),
      MaxNumValueProperty()
    ).merge(value))
  }
}

case class NumberSchema(override val properties: SchemaProperties[BigDecimal] = SchemaProperties.empty) extends JsonSchema[BigDecimal] {
  override val schemaType = "string"

  def mergeSameType: PartialFunction[JsonSchema[_], JsonSchema[_]] = {
    case other @ NumberSchema(otherProperties) =>
      NumberSchema(properties.merge(otherProperties))

    case other @ IntegerSchema(otherProperties) => {
      val newProperties = otherProperties.collect {
        case MinIntValueProperty(minValue) => MinNumValueProperty(minValue.map(_.toDouble))
        case MaxIntValueProperty(maxValue) => MaxNumValueProperty(maxValue.map(_.toDouble))
      }.toSeq

      NumberSchema(properties.merge(SchemaProperties(newProperties)))
    }
  }
}

case class MinNumValueProperty(minNumValue: Option[BigDecimal] = None) extends SchemaProperty[BigDecimal] {
  override val toJson = ("minimum" -> minNumValue)

  override def merge(otherProp: SchemaProperty[BigDecimal]) = {
    MinNumValueProperty(minOrNone(minNumValue, otherProp.asInstanceOf[MinNumValueProperty].minNumValue))
  }

  override def merge(value: BigDecimal) = {
    MinNumValueProperty(minOrNone(Some(value), minNumValue))
  }
}

case class MaxNumValueProperty(maxNumValue: Option[BigDecimal] = None) extends SchemaProperty[BigDecimal] {
  override val toJson = ("maximum" -> maxNumValue)

  override def merge(otherProp: SchemaProperty[BigDecimal]) = {
    MaxNumValueProperty(maxOrNone(maxNumValue, otherProp.asInstanceOf[MaxNumValueProperty].maxNumValue))
  }

  override def merge(value: BigDecimal) = {
    MaxNumValueProperty(maxOrNone(Some(value), maxNumValue))
  }
}
