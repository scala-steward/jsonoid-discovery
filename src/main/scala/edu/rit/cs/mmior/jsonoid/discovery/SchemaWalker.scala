package edu.rit.cs.mmior.jsonoid.discovery

import schemas._

trait SchemaWalker[T] {
  private def extractSingle(
      schema: JsonSchema[_],
      extractor: PartialFunction[(String, JsonSchema[_]), T],
      path: String
  ): Seq[(String, T)] = {
    if (extractor.isDefinedAt((path, schema))) {
      Seq((path, extractor(path, schema)))
    } else {
      Seq.empty[(String, T)]
    }
  }

  @SuppressWarnings(Array("org.wartremover.warts.Recursion"))
  private def extractValues(
      schema: JsonSchema[_],
      extractor: PartialFunction[(String, JsonSchema[_]), T],
      prefix: String = "$"
  ): Seq[(String, T)] = {
    schema match {
      case o: ObjectSchema =>
        val props = o.properties.get[ObjectTypesProperty].objectTypes
        val extractedProps =
          extractSingle(o, extractor, prefix) ++ props.keySet.toSeq.flatMap(
            key => extractValues(props(key), extractor, prefix + "." + key)
          )

        // XXX patternProperties are represented using the regex string
        val patternProps = o.properties
          .getOrNone[PatternTypesProperty]
          .map(_.patternTypes)
          .getOrElse(Map.empty)
        val extractedPatternProps =
          extractSingle(o, extractor, prefix) ++ patternProps.keySet.toSeq
            .flatMap(key =>
              extractValues(
                patternProps(key),
                extractor,
                prefix + "." + key.toString
              )
            )

        extractedProps.toSeq ++ extractedPatternProps.toSeq
      case a: ArraySchema =>
        extractSingle(a, extractor, prefix) ++ (a.properties
          .get[ItemTypeProperty]
          .itemType match {
          case Left(singleSchema) =>
            extractValues(singleSchema, extractor, prefix + "[*]")
          case Right(multipleSchemas) =>
            multipleSchemas.zipWithIndex.flatMap { case (schema, index) =>
              extractValues(schema, extractor, s"$prefix[$index]")
            }
        })
      case p: ProductSchema =>
        val types = p.properties.get[ProductSchemaTypesProperty].schemaTypes
        extractSingle(p, extractor, prefix) ++ types.zipWithIndex.flatMap {
          case (schema, index) =>
            extractValues(schema, extractor, s"$prefix[$index]")
        }
      case x =>
        extractSingle(x, extractor, prefix)
    }
  }

  def walk(
      schema: JsonSchema[_],
      extractor: PartialFunction[(String, JsonSchema[_]), T]
  ): Map[String, T] = {
    extractValues(schema, extractor, "$").toMap
  }
}
