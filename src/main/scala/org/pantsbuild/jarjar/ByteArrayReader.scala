package org.pantsbuild.jarjar

// Utility class to read the content of a single table entry
class ByteArrayReader(bytes: Array[Byte]) extends Nat.Reader {
  private var readIndex = 0

  /** Read a byte */
  override def readByte(): Int = {
    val x = bytes(readIndex).toInt
    readIndex += 1
    x
  }

  /** Reads a number of bytes into an array */
  def readBytes(len: Int): Array[Byte] = {
    val result = bytes.slice(readIndex, readIndex + len)
    readIndex += len
    result
  }

  def atEnd: Boolean = readIndex == bytes.length
}