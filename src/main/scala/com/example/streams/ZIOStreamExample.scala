package com.example.streams

import zio._
import zio.stream._
import zio.json._

import java.nio.charset.CharacterCodingException
import scala.util.matching.Regex

object ZIOStreamExample extends ZIOAppDefault {
  val post1: String = "hello-word.md"
  val post1_content: Array[Byte] =
    """---
      |title: "Hello World"
      |tags: []
      |---
      |======
      |
      |## Generic Heading
      |
      |Even pretend blog posts need a #generic intro.
      |""".stripMargin.getBytes

  val post2: String = "scala-3-extensions.md"
  val post2_content: Array[Byte] =
    """---
      |title: "Scala 3 for You and Me"
      |tags: []
      |---
      |======
      |
      |## Cool Heading
      |
      |This is a post about #Scala and their re-work of #implicits via thing like #extensions.
      |""".stripMargin.getBytes

  val post3: String = "zio-streams.md"
  val post3_content: Array[Byte] =
    """---
      |title: "ZIO Streams: An Introduction"
      |tags: []
      |---
      |======
      |
      |## Some Heading
      |
      |This is a post about #Scala and #ZIO #ZStreams!
    """.stripMargin.getBytes


  val fileMap: Map[String, Array[Byte]] = Map(
    post1 -> post1_content,
    post2 -> post2_content,
    post3 -> post3_content
  )

  // Obj1: Automatically add tags to the tag array from the message body.

  // Find tags.

  val hashFilter: String => Boolean = str =>
    str.startsWith("#") && str.count(_ == '#') == 1 && str.length > 2
  val punctuationRegex: Regex = """\p{Punct}""".r

  val parseHash: ZPipeline[Any, Nothing, String, String] =
    ZPipeline.filter(hashFilter)
  val removePunctuation: ZPipeline[Any, Nothing, String, String] = ZPipeline.map(str => punctuationRegex.replaceAllIn(str, ""))
  val lowercase: ZPipeline[Any, Nothing, String, String] = ZPipeline.map(_.toLowerCase)

  val collectTagsPipeline: ZPipeline[Any, CharacterCodingException, Byte, String] =
    ZPipeline.utf8Decode >>>
      ZPipeline.splitLines >>>
      ZPipeline.splitOn(" ") >>>
      parseHash >>>
      removePunctuation >>>
      lowercase

  val printSink: ZSink[Any, Nothing, String, Nothing, Unit] = ZSink.foreach(str => ZIO.succeed(println(str)))

  // Obj2: Create tags.
  val addTags: Set[String] => ZPipeline[Any, Nothing, String, String] = tags =>
    ZPipeline.map(content => content.replace(s"tags: []", s"tags: [${tags.mkString(", ")}]"))

  val addLinks: ZPipeline[Any, Nothing, String, String] =
    ZPipeline.map { line =>
      line.split(" ").map { word =>
        if (hashFilter(word)) {
          s"[$word](/tags/${punctuationRegex.replaceAllIn(word.toLowerCase, "")})"
        } else {
          word
        }
      }.mkString(" ")
    }

  val addNewLine: ZPipeline[Any, Nothing, String, String] = ZPipeline.map(line => line + "\n")

  val regeneratePost: Set[String] => ZPipeline[Any, CharacterCodingException, Byte, Byte] = tags =>
    ZPipeline.utf8Decode >>>
      ZPipeline.splitLines >>>
      addTags(tags) >>>
      addLinks >>>
      addNewLine >>>
      ZPipeline.utf8Encode

  def writeFile(dirPath: String, filename: String): ZSink[Any, Throwable, Byte, Byte, Long] =
    ZSink.fromFileName(dirPath + "/" + filename)

  def collectTags: ZSink[Any, Nothing, String, Nothing, Set[String]] =
    ZSink.collectAllToSet

  def autoTag(filename: String, content: Array[Byte]): ZIO[Any, Throwable, (String, Set[String])] = for {
    tags <- ZStream.fromIterable(content)
      .via(collectTagsPipeline)
      .run(collectTags)
    _ <- Console.printLine(s"Generating file: $filename")
    _ <- ZStream.fromIterable(content)
      .via(regeneratePost(tags))
      .run(writeFile(s"src/main/resources/data/zio-streams", filename))
  } yield (filename, tags)

  val autoTagAll: ZIO[Any, Throwable, Map[String, Set[String]]] = ZIO.foreach(fileMap) {
    case (filename, contents) => autoTag(filename, contents)
  }

  // Obj. 3 Add the search index file

  // Map[filename, all tags in that file]
  // Map[tag, all file with the tag]
  def createTagIndex(tagMap: Map[String, Set[String]]): ZIO[Any, Throwable, Long] = {
    val searchMap = tagMap.values
      .toSet // Set[Set[String]]
      .flatten // Set[String]
      .map(tag => tag -> tagMap.filter(_._2.contains(tag)).keys.toSet) // Set((String, Set[String]))
      .toMap

    ZStream.fromIterable(searchMap.toJsonPretty.getBytes)
      .run(ZSink.fromFileName("src/main/resources/data/zio-streams/search.json"))
  }

  val parseProgram = for {
    tagMap <- autoTagAll
    _ <- Console.printLine("Generating index file search.json")
    _ <- createTagIndex(tagMap)
  } yield ()

  override def run: URIO[Any, ExitCode] = {
    // Run the first stream
    // ZStream.fromIterable(post1_content).via(collectTagsPipeline).run(printSink).exitCode
    parseProgram.exitCode
  }
}
