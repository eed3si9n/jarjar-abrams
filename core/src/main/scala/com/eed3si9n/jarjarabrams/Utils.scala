package com.eed3si9n.jarjarabrams

import java.io.InputStream

object Utils {
  // Use InputStream.readAllBytes() once on JDK >=9
  def readAllBytes(inputStream: InputStream): Array[Byte] =
    Stream.continually(inputStream.read).takeWhile(_ != -1).map(_.toByte).toArray
}
