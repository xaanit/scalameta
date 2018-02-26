package scala.meta.tests.cli

import java.io._
import java.nio.charset.StandardCharsets._
import java.nio.file._
import scala.util.Properties.versionNumberString
import org.scalatest.FunSuite
import scala.meta.cli._
import scala.meta.testkit.DiffAssertions

class CliSuite extends BaseCliSuite {
  val sourceroot = Files.createTempDirectory("sourceroot_")
  val helloWorldScala = sourceroot.resolve("HelloWorld.scala")
  Files.write(
    helloWorldScala,
    """
    object HelloWorld {
      def main(args: Array[String]): Unit = {
        println("hello world")
      }
    }
  """.getBytes(UTF_8))
  val target = Files.createTempDirectory("target_")
  val helloWorldSemanticdb = target.resolve("META-INF/semanticdb/HelloWorld.scala.semanticdb")

  test("metac " + helloWorldScala) {
    val (exitcode, out, err) = CliSuite.communicate { (out, err) =>
      val args = Array(
        "-cp",
        scalaLibraryJar,
        "-P:semanticdb:sourceroot:" + sourceroot.toString,
        "-d",
        target.toString,
        helloWorldScala.toString)
      Metac.process(args, out, err)
    }
    assert(exitcode == 0)
    assert(out.isEmpty)
    assert(Files.exists(helloWorldSemanticdb))
  }

  test("metap " + helloWorldSemanticdb) {
    val language = {
      if (versionNumberString.startsWith("2.11")) "Scala211"
      else if (versionNumberString.startsWith("2.12")) "Scala212"
      else sys.error(s"unsupported Scala version: $versionNumberString")
    }
    val (exitcode, out, err) = CliSuite.communicate { (out, err) =>
      Metap.process(Array(helloWorldSemanticdb.toString), out, err)
    }
    assert(exitcode == 0)
    assertNoDiff(
      out,
      s"""
      |HelloWorld.scala
      |----------------
      |
      |Summary:
      |Schema => SemanticDB v3
      |Uri => HelloWorld.scala
      |Text => non-empty
      |Language => $language
      |Symbols => 7 entries
      |Occurrences => 7 entries
      |
      |Symbols:
      |_empty_.HelloWorld. => final object HelloWorld
      |_empty_.HelloWorld.main(Array). => def main: (args: Array[String]): Unit
      |  args => _empty_.HelloWorld.main(Array).(args)
      |  Unit => _root_.scala.Unit#
      |_empty_.HelloWorld.main(Array).(args) => param args: Array[String]
      |  Array => _root_.scala.Array#
      |  String => _root_.scala.Predef.String#
      |_root_.scala.Array# => final class Array[Array#[T: <?>].{+6 decls}
      |  extends AnyRef
      |  extends Serializable
      |  extends Cloneable
      |_root_.scala.Predef.String# => type String: >: String <: String
      |  String => _root_.java.lang.String#
      |_root_.scala.Predef.println(Any). => def println: (x: <?>): Unit
      |  x => _root_.scala.Predef.println(Any).(x)
      |  Unit => _root_.scala.Unit#
      |_root_.scala.Unit# => abstract final class Unit.{+2 decls}
      |  extends AnyVal
      |
      |Occurrences:
      |[1:11..1:21): HelloWorld <= _empty_.HelloWorld.
      |[2:10..2:14): main <= _empty_.HelloWorld.main(Array).
      |[2:15..2:19): args <= _empty_.HelloWorld.main(Array).(args)
      |[2:21..2:26): Array => _root_.scala.Array#
      |[2:27..2:33): String => _root_.scala.Predef.String#
      |[2:37..2:41): Unit => _root_.scala.Unit#
      |[3:8..3:15): println => _root_.scala.Predef.println(Any).
    """.trim.stripMargin)
    assert(err.isEmpty)
  }
}

object CliSuite {
  def communicate[T](op: (PrintStream, PrintStream) => T): (T, String, String) = {
    val outbaos = new ByteArrayOutputStream
    val outps = new PrintStream(outbaos, true, UTF_8.name)
    val errbaos = new ByteArrayOutputStream
    val errps = new PrintStream(errbaos, true, UTF_8.name)
    val result = op(outps, errps)
    val outs = new String(outbaos.toByteArray, UTF_8)
    val errs = new String(errbaos.toByteArray, UTF_8)
    (result, outs, errs)
  }
}
