package example

import shaded.org.typelevel.jawn._
import scala.collection.mutable.ArrayBuffer

object ApplicationMain extends App {
  val json = """{ "x": 5 }"""
  val tree = Parser.parseUnsafe(json)
  println(tree)
}

object Parser extends SupportParser[JValue] {
  implicit val facade: Facade[JValue] =
    new Facade[JValue] {
      def jnull(index: Int) = JNull
      def jfalse(index: Int) = JFalse
      def jtrue(index: Int) = JTrue
      def jnum(s: CharSequence, decIndex: Int, expIndex: Int, index: Int) = JNumber(s.toString)
      def jint(s: String) = JNumber(s)
      def jstring(s: CharSequence, index: Int) = JString(s.toString)
      def singleContext(index: Int) = new FContext[JValue] {
        var value: JValue = _
        def add(s: CharSequence, index: Int) = value = jstring(s, index)
        def add(v: JValue, index: Int) = value = v
        def finish(index: Int): JValue = value
        def isObj: Boolean = false
      }
      def arrayContext(index: Int) = new FContext[JValue] {
        private val vs = ArrayBuffer.empty[JValue]
        def add(s: CharSequence, index: Int) = vs += jstring(s, index)
        def add(v: JValue, index: Int) = vs += v
        def finish(index: Int): JValue = JArray(vs.toArray)
        def isObj: Boolean = false
      }
      def objectContext(index: Int) = new FContext[JValue] {
        private var key: String = _
        private var vs = ArrayBuffer.empty[JField]
        private def andNullKey[A](t: => Unit): Unit = { t; key = null }
        def add(s: CharSequence, index: Int) = {
          if (key == null) key = s.toString
          else andNullKey(vs += JField(key, jstring(s, index)))
        }
        def add(v: JValue, index: Int) = andNullKey(vs += JField(key, v))
        def finish(index: Int): JValue = JObject(vs.toArray)
        def isObj: Boolean = true
      }
    }
}

/** Represents a JSON Value which may be invalid. Internally uses mutable
  * collections when its desirable to do so, for performance and other reasons
  * (such as ordering and duplicate keys)
  *
  * @author Matthew de Detrich
  * @see https://www.ietf.org/rfc/rfc4627.txt
  */
sealed abstract class JValue extends Serializable with Product {

}

/** Represents a JSON null value
  *
  * @author Matthew de Detrich
  */
final case object JNull extends JValue {

}

/** Represents a JSON string value
  *
  * @author Matthew de Detrich
  */
final case class JString(value: String) extends JValue {

}

object JNumber {
  def apply(value: Int): JNumber = JNumber(value.toInt.toString)

  def apply(value: Long): JNumber = JNumber(value.toString)

  def apply(value: BigInt): JNumber = JNumber(value.toString)

  def apply(value: BigDecimal): JNumber = JNumber(value.toString)

  def apply(value: Float): JNumber = JNumber(value.toString)

  def apply(value: Double): JNumber = JNumber(value.toString)

  def apply(value: Integer): JNumber = JNumber(value.toString)

  def apply(value: Array[Char]): JNumber = JNumber(new String(value))
}

/** Represents a JSON number value.
  *
  * If you are passing in a NaN or Infinity as a [[scala.Double]], [[unsafe.JNumber]]
  * will contain "NaN" or "Infinity" as a String which means it will cause
  * issues for users when they use the value at runtime. It may be
  * preferable to check values yourself when constructing [[unsafe.JValue]]
  * to prevent this. This isn't checked by default for performance reasons.
  *
  * @author Matthew de Detrich
  */
// JNumber is internally represented as a string, to improve performance
final case class JNumber(value: String) extends JValue {
}

/** Represents a JSON Boolean value, which can either be a
  * [[JTrue]] or a [[JFalse]]
  *
  * @author Matthew de Detrich
  */
// Implements named extractors so we can avoid boxing
sealed abstract class JBoolean extends JValue {
  def isEmpty: Boolean = false
  def get: Boolean
}

object JBoolean {
  def apply(x: Boolean): JBoolean = if (x) JTrue else JFalse

  def unapply(x: JBoolean): Some[Boolean] = Some(x.get)
}

/** Represents a JSON Boolean true value
  *
  * @author Matthew de Detrich
  */
final case object JTrue extends JBoolean {
  override def get = true
}

/** Represents a JSON Boolean false value
  *
  * @author Matthew de Detrich
  */
final case object JFalse extends JBoolean {
  override def get = false
}

final case class JField(field: String, value: JValue)

object JObject {
  def apply(value: JField, values: JField*): JObject =
    JObject(Array(value) ++ values)
}

/** Represents a JSON Object value. Duplicate keys
  * are allowed and ordering is respected
  * @author Matthew de Detrich
  */
// JObject is internally represented as a mutable Array, to improve sequential performance
final case class JObject(value: Array[JField] = Array.empty) extends JValue {
  override def equals(obj: scala.Any): Boolean = {
    obj match {
      case jObject: JObject =>
        val length = value.length
        if (length != jObject.value.length)
          return false
        var index = 0
        while (index < length) {
          if (value(index) != jObject.value(index))
            return false
          index += 1
        }
        true
      case _ => false
    }
  }

  override def hashCode: Int =
    java.util.Arrays.deepHashCode(value.asInstanceOf[Array[AnyRef]])

  override def toString =
    "JObject(" + java.util.Arrays
      .toString(value.asInstanceOf[Array[AnyRef]]) + ")"
}

object JArray {
  def apply(value: JValue, values: JValue*): JArray =
    JArray(Array(value) ++ values.toArray[JValue])
}

/** Represents a JSON Array value
  * @author Matthew de Detrich
  */
// JArray is internally represented as a mutable Array, to improve sequential performance
final case class JArray(value: Array[JValue] = Array.empty) extends JValue {
  override def equals(obj: scala.Any): Boolean = {
    obj match {
      case jArray: JArray =>
        val length = value.length
        if (length != jArray.value.length)
          return false
        var index = 0
        while (index < length) {
          if (value(index) != jArray.value(index))
            return false
          index += 1
        }
        true
      case _ => false
    }
  }

  override def hashCode: Int =
    java.util.Arrays.deepHashCode(value.asInstanceOf[Array[AnyRef]])

  override def toString =
    "JArray(" + java.util.Arrays
      .toString(value.asInstanceOf[Array[AnyRef]]) + ")"
}
