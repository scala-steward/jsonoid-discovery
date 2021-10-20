package edu.rit.cs.mmior.jsonoid.discovery
package schemas

import scala.reflect._

import com.sangupta.bloomfilter.impl.RoaringBloomFilter
import scalaz._
import org.json4s.JsonDSL._
import org.json4s._
import Scalaz._

import Helpers._
import utils.{Histogram, HyperLogLog}

object IntegerSchema {
  def apply(value: BigInt)(implicit propSet: PropertySet): IntegerSchema = {
    IntegerSchema(
      propSet.integerProperties.mergeValue(value)(
        EquivalenceRelations.KindEquivalenceRelation
      )
    )
  }

  val AllProperties: SchemaProperties[BigInt] = {
    val props = SchemaProperties.empty[BigInt]
    props.add(MinIntValueProperty())
    props.add(MaxIntValueProperty())
    props.add(IntHyperLogLogProperty())
    props.add(IntBloomFilterProperty())
    props.add(IntStatsProperty())
    props.add(IntExamplesProperty())
    props.add(MultipleOfProperty())
    props.add(IntHistogramProperty())

    props
  }

  val MinProperties: SchemaProperties[BigInt] = {
    SchemaProperties.empty[BigInt]
  }

  val SimpleProperties: SchemaProperties[BigInt] = {
    val props = SchemaProperties.empty[BigInt]
    props.add(MinIntValueProperty())
    props.add(MaxIntValueProperty())
    props.add(MultipleOfProperty())

    props
  }
}

final case class IntegerSchema(
    override val properties: SchemaProperties[BigInt] =
      IntegerSchema.AllProperties
) extends JsonSchema[BigInt] {
  override val schemaType = "integer"

  override val validTypes: Set[ClassTag[_ <: JValue]] = Set(classTag[JInt])

  def mergeSameType()(implicit
      er: EquivalenceRelation
  ): PartialFunction[JsonSchema[_], JsonSchema[_]] = {
    case other @ IntegerSchema(otherProperties) =>
      IntegerSchema(properties.merge(otherProperties))
    case other: NumberSchema => other.mergeSameType()(er)(this)
  }

  override def copy(properties: SchemaProperties[BigInt]): IntegerSchema =
    IntegerSchema(properties)
}

final case class MinIntValueProperty(minIntValue: Option[BigInt] = None)
    extends SchemaProperty[BigInt, MinIntValueProperty] {
  override def toJson: JObject = ("minimum" -> minIntValue)

  override def merge(
      otherProp: MinIntValueProperty
  )(implicit er: EquivalenceRelation): MinIntValueProperty = {
    MinIntValueProperty(minOrNone(minIntValue, otherProp.minIntValue))
  }

  override def mergeValue(
      value: BigInt
  )(implicit er: EquivalenceRelation): MinIntValueProperty = {
    MinIntValueProperty(minOrNone(Some(value), minIntValue))
  }

  override def collectAnomalies(value: JValue, path: String) = {
    value match {
      case JInt(num) =>
        minIntValue match {
          case Some(min) =>
            if (num < min) {
              Seq(Anomaly(path, "value is below minimum", Warning))
            } else {
              Seq.empty
            }
          case None => Seq.empty
        }
      case _ => Seq.empty
    }
  }
}

final case class MaxIntValueProperty(maxIntValue: Option[BigInt] = None)
    extends SchemaProperty[BigInt, MaxIntValueProperty] {
  override def toJson: JObject = ("maximum" -> maxIntValue)

  override def merge(
      otherProp: MaxIntValueProperty
  )(implicit er: EquivalenceRelation): MaxIntValueProperty = {
    MaxIntValueProperty(maxOrNone(maxIntValue, otherProp.maxIntValue))
  }

  override def mergeValue(
      value: BigInt
  )(implicit er: EquivalenceRelation): MaxIntValueProperty = {
    MaxIntValueProperty(maxOrNone(Some(value), maxIntValue))
  }

  override def collectAnomalies(value: JValue, path: String) = {
    value match {
      case JInt(num) =>
        maxIntValue match {
          case Some(max) =>
            if (num > max) {
              Seq(Anomaly(path, "value is above maximum", Warning))
            } else {
              Seq.empty
            }
          case None => Seq.empty
        }
      case _ => Seq.empty
    }
  }
}

final case class IntHyperLogLogProperty(
    hll: HyperLogLog = new HyperLogLog()
) extends SchemaProperty[BigInt, IntHyperLogLogProperty] {
  override def toJson: JObject = ("distinctValues" -> hll.count())

  override def merge(
      otherProp: IntHyperLogLogProperty
  )(implicit er: EquivalenceRelation): IntHyperLogLogProperty = {
    val prop = IntHyperLogLogProperty()
    prop.hll.merge(this.hll)
    prop.hll.merge(otherProp.hll)

    prop
  }

  override def mergeValue(
      value: BigInt
  )(implicit er: EquivalenceRelation): IntHyperLogLogProperty = {
    val prop = IntHyperLogLogProperty()
    prop.hll.merge(this.hll)
    prop.hll.add(value.toLong)

    prop
  }
}

object IntBloomFilterProperty {
  val ExpectedElements: Int = 100000
  val FalsePositive: Double = 0.01
}

final case class IntBloomFilterProperty(
    bloomFilter: RoaringBloomFilter[Integer] = new RoaringBloomFilter[Integer](
      IntBloomFilterProperty.ExpectedElements,
      IntBloomFilterProperty.FalsePositive
    )
) extends SchemaProperty[BigInt, IntBloomFilterProperty] {
  override def toJson: JObject = JObject(Nil)

  override def merge(
      otherProp: IntBloomFilterProperty
  )(implicit er: EquivalenceRelation): IntBloomFilterProperty = {
    val prop = IntBloomFilterProperty()
    prop.bloomFilter.merge(this.bloomFilter)
    prop.bloomFilter.merge(otherProp.bloomFilter)

    prop
  }

  @SuppressWarnings(Array("org.wartremover.warts.NonUnitStatements"))
  override def mergeValue(
      value: BigInt
  )(implicit er: EquivalenceRelation): IntBloomFilterProperty = {
    val prop = IntBloomFilterProperty()
    prop.bloomFilter.merge(this.bloomFilter)
    prop.bloomFilter.add(value.toByteArray)

    prop
  }

  override def collectAnomalies(value: JValue, path: String) = {
    val inFilter = value match {
      case JInt(num) => Some(bloomFilter.contains(num.toByteArray))
      case _         => None
    }

    inFilter match {
      case Some(false) =>
        Seq(Anomaly(path, "value not found in Bloom filter", Info))
      case _ => Seq.empty
    }
  }
}

final case class IntStatsProperty(stats: StatsProperty = StatsProperty())
    extends SchemaProperty[BigInt, IntStatsProperty] {
  override def toJson: JObject = ("statistics" -> stats.toJson)

  override def merge(
      otherProp: IntStatsProperty
  )(implicit er: EquivalenceRelation): IntStatsProperty = {
    IntStatsProperty(stats.merge(otherProp.stats))
  }

  override def mergeValue(
      value: BigInt
  )(implicit er: EquivalenceRelation): IntStatsProperty = {
    IntStatsProperty(stats.merge(StatsProperty(BigDecimal(value))))
  }
}

final case class IntExamplesProperty(
    examples: ExamplesProperty[BigInt] = ExamplesProperty()
) extends SchemaProperty[BigInt, IntExamplesProperty] {
  override def toJson: JObject = ("examples" ->
    examples.examples.distinct.sorted)

  override def merge(
      otherProp: IntExamplesProperty
  )(implicit er: EquivalenceRelation): IntExamplesProperty = {
    IntExamplesProperty(examples.merge(otherProp.examples))
  }

  override def mergeValue(
      value: BigInt
  )(implicit er: EquivalenceRelation): IntExamplesProperty = {
    IntExamplesProperty(examples.merge(ExamplesProperty(value)))
  }
}

final case class MultipleOfProperty(multiple: Option[BigInt] = None)
    extends SchemaProperty[BigInt, MultipleOfProperty] {
  @SuppressWarnings(Array("org.wartremover.warts.Equals"))
  override def toJson: JObject = multiple match {
    case Some(intVal) if intVal > 1 => ("multipleOf" -> intVal)
    case _                          => Nil
  }

  override def merge(
      otherProp: MultipleOfProperty
  )(implicit er: EquivalenceRelation): MultipleOfProperty = {
    val newMultiple = (multiple, otherProp.multiple) match {
      case (Some(m), None)    => Some(m)
      case (None, Some(n))    => Some(n)
      case (Some(m), Some(n)) => Some(gcd(m, n))
      case (None, None)       => None
    }
    MultipleOfProperty(newMultiple)
  }

  override def mergeValue(
      value: BigInt
  )(implicit er: EquivalenceRelation): MultipleOfProperty = {
    merge(MultipleOfProperty(Some(value)))
  }
}

final case class IntHistogramProperty(
    histogram: Histogram = Histogram()
) extends SchemaProperty[BigInt, IntHistogramProperty] {
  override def toJson: JObject = {
    ("histogram" -> histogram.bins.map { case (value, count) =>
      List(value.doubleValue, count.longValue)
    })
  }

  override def merge(
      otherProp: IntHistogramProperty
  )(implicit er: EquivalenceRelation): IntHistogramProperty = {
    IntHistogramProperty(histogram.merge(otherProp.histogram))
  }

  override def mergeValue(
      value: BigInt
  )(implicit er: EquivalenceRelation): IntHistogramProperty = {
    IntHistogramProperty(
      histogram.merge(Histogram(List((BigDecimal(value), 1))))
    )
  }

  override def collectAnomalies(value: JValue, path: String) = {
    value match {
      case JInt(num) =>
        if (histogram.isAnomalous(BigDecimal(num))) {
          Seq(Anomaly(path, "value outside histogram bounds", Warning))
        } else {
          Seq.empty
        }
      case _ => Seq.empty
    }
  }
}
