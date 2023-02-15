package edu.rit.cs.mmior.jsonoid.discovery

import java.io.File
import java.io.FileOutputStream
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import scala.io.Source

import scopt.OptionParser
import org.json4s._
import org.json4s.jackson.JsonMethods._

import Helpers._
import schemas._

final case class Config(
    input: Option[File] = None,
    writeOutput: Option[File] = None,
    writeValues: Option[File] = None,
    propertySet: PropertySet = PropertySets.AllProperties,
    onlyProperties: Option[Seq[String]] = None,
    equivalenceRelation: EquivalenceRelation =
      EquivalenceRelations.KindEquivalenceRelation,
    addDefinitions: Boolean = false,
    maxExamples: Option[Int] = None,
    additionalProperties: Boolean = false
)

object DiscoverSchema {
  def discover(
      jsons: Iterator[JValue],
      propSet: PropertySet = PropertySets.AllProperties
  )(implicit p: JsonoidParams): JsonSchema[_] = {
    jsons.map(discoverFromValue(_, propSet)).fold(ZeroSchema())(_.merge(_))
  }

  @SuppressWarnings(Array("org.wartremover.warts.Recursion"))
  def discoverFromValue(
      value: JValue,
      propSet: PropertySet = PropertySets.AllProperties
  )(implicit p: JsonoidParams): JsonSchema[_] = {
    value match {
      case JArray(items) =>
        ArraySchema(items.map(discoverFromValue(_, propSet)(p)))(propSet, p)
      case JBool(bool)     => BooleanSchema(bool)(p)
      case JDecimal(dec)   => NumberSchema(dec)(propSet, p)
      case JDouble(dbl)    => NumberSchema(dbl)(propSet, p)
      case JInt(int)       => IntegerSchema(int)(propSet, p)
      case JLong(long)     => IntegerSchema(long)(propSet, p)
      case JNothing        => NullSchema()
      case JNull           => NullSchema()
      case JObject(fields) => discoverObjectFields(fields, propSet)(p)
      case JSet(items) =>
        ArraySchema(items.map(discoverFromValue(_, propSet)(p)).toList)(
          propSet,
          p
        )
      case JString(str) => StringSchema(str)(propSet, p)
    }
  }

  def discoverObjectFields(
      fields: Seq[JField],
      propSet: PropertySet
  )(implicit p: JsonoidParams): JsonSchema[_] = {
    ObjectSchema(
      fields
        .map { case (k, v) =>
          (k, discoverFromValue(v, propSet))
        }
        .asInstanceOf[Seq[(String, JsonSchema[_])]]
        .toMap
    )(propSet, p)
  }

  def jsonFromSource(source: Source): Iterator[JValue] = {
    source.getLines().map(parse(_))
  }

  @SuppressWarnings(Array("org.wartremover.warts.Var"))
  def transformSchema(
      schema: JsonSchema[_],
      addDefinitions: Boolean = false
  )(implicit p: JsonoidParams): JsonSchema[_] = {
    var transformedSchema = schema
    if (addDefinitions) {
      transformedSchema = DefinitionTransformer
        .transformSchema(transformedSchema)(p)
    }
    transformedSchema = EnumTransformer
      .transformSchema(transformedSchema)(p)

    transformedSchema
  }

  // $COVERAGE-OFF$ No automated testing of CLI
  implicit val propertySetRead: scopt.Read[PropertySet] =
    scopt.Read.reads(typeName =>
      typeName match {
        case "All"    => PropertySets.AllProperties
        case "Min"    => PropertySets.MinProperties
        case "Simple" => PropertySets.SimpleProperties
      }
    )

  implicit val equivalenceRelationRead: scopt.Read[EquivalenceRelation] =
    scopt.Read.reads(erName =>
      erName match {
        case "Kind"  => EquivalenceRelations.KindEquivalenceRelation
        case "Label" => EquivalenceRelations.LabelEquivalenceRelation
        case "IntersectingLabel" =>
          EquivalenceRelations.IntersectingLabelEquivalenceRelation
        case "TypeMatch" =>
          EquivalenceRelations.TypeMatchEquivalenceRelation
      }
    )

  @SuppressWarnings(
    Array(
      "org.wartremover.warts.NonUnitStatements",
      "org.wartremover.warts.OptionPartial",
      "org.wartremover.warts.Var"
    )
  )
  def main(args: Array[String]): Unit = {
    val parser = new OptionParser[Config]("jsonoid-discover") {
      head("jsonoid-discover", BuildInfo.version)

      help("help")

      arg[File]("<input>")
        .optional()
        .action((x, c) => c.copy(input = Some(x)))
        .text("a JSON file to perform discovery on, one object per line")

      opt[File]('w', "write-output")
        .action((x, c) => c.copy(writeOutput = Some(x)))
        .valueName("<file>")
        .text("file to write the generated schema to, defaults to stdout")

      opt[File]('v', "values")
        .action((x, c) => c.copy(writeValues = Some(x)))
        .valueName("<file>")
        .text("a file where a table of collected values should be written")

      opt[PropertySet]('p', "prop")
        .action((x, c) => c.copy(propertySet = x))
        .text("the set of properties to calculate [All, Min, Simple]")

      opt[Seq[String]]('o', "only-properties")
        .optional()
        .action((x, c) => c.copy(onlyProperties = Some(x)))
        .text("limit discovered properties")

      opt[EquivalenceRelation]('e', "equivalence-relation")
        .action((x, c) => c.copy(equivalenceRelation = x))
        .text(
          "the equivalence relation to use when merging" +
            " [Kind, Label, IntersectingLabel, TypeMatch]"
        )

      opt[Unit]('d', "add-definitions")
        .action((x, c) => c.copy(addDefinitions = true))
        .text("extract similar objects to create definitions")

      opt[Int]("max-examples")
        .action((x, c) => c.copy(maxExamples = Some(x)))
        .text("maximum number of examples to extract")

      opt[Unit]('a', "additional-properties")
        .action((x, c) => c.copy(additionalProperties = true))
        .text("set additionalProperties to true in the generated schema")
    }

    parser.parse(args, Config()) match {
      case Some(config) =>
        val source = config.input match {
          case Some(file) => Source.fromFile(file)
          case None       => Source.stdin
        }

        val propSet = config.onlyProperties match {
          case Some(propNames) => config.propertySet.onlyNamed(propNames)
          case None            => config.propertySet
        }

        val jsons = jsonFromSource(source)
        var p = JsonoidParams()
          .withER(config.equivalenceRelation)
          .withAdditionalProperties(config.additionalProperties)
        if (config.maxExamples.isDefined) {
          p = p.withMaxExamples(config.maxExamples.get)
        }

        val schema =
          discover(jsons, propSet)(p)

        // Check if transformations are valid
        if (
          config.addDefinitions && config.propertySet =/= PropertySets.AllProperties
        ) {
          throw new IllegalArgumentException(
            "All properties required to compute definitions"
          )
        }

        var transformedSchema: JsonSchema[_] =
          transformSchema(schema, config.addDefinitions)(p)

        if (config.writeValues.isDefined) {
          val outputStream = new FileOutputStream(config.writeValues.get)
          ValueTableGenerator.writeValueTable(transformedSchema, outputStream)
        }

        val schemaStr = pretty(render(transformedSchema.toJsonSchema))
        config.writeOutput match {
          case Some(file) =>
            Files.write(
              file.toPath(),
              schemaStr.getBytes(StandardCharsets.UTF_8)
            )
          case None => println(schemaStr)
        }
      case None =>
    }
  }
  // $COVERAGE-ON$
}
