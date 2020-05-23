package sbtassembly.scalasig

/**
 * Utility methods for reading and writing nat encoded numbers.
 */
object Nat {

  trait Reader {
    /** Read a byte */
    def readByte(): Int

    /** Read a natural number in big endian format, base 128.
     * All but the last digits have bit 0x80 set. */
    def readNat(): Int = readLongNat().toInt

    def readLongNat(): Long = {
      var b = 0L
      var x = 0L
      do {
        b = readByte().toLong
        x = (x << 7) + (b & 0x7f)
      } while ((b & 0x80) != 0L)
      x
    }

    /** Read a long number in signed big endian format, base 256. */
    def readLong(len: Int): Long = {
      var x = 0L
      var i = 0
      while (i < len) {
        x = (x << 8) + (readByte() & 0xff)
        i += 1
      }
      val leading = 64 - (len << 3)
      x << leading >> leading
    }
  }

  trait Writer {
    /** Write a byte */
    def writeByte(b: Int): Unit

    /** Write a natural number in big endian format, base 128.
     *  All but the last digits have bit 0x80 set.
     */
    def writeNat(x: Int): Unit =
      writeLongNat(x.toLong & 0x00000000FFFFFFFFL)

    /**
     * Like writeNat, but for longs. This is not the same as
     * writeLong, which writes in base 256. Note that the
     * binary representation of LongNat is identical to Nat
     * if the long value is in the range Int.MIN_VALUE to
     * Int.MAX_VALUE.
     */
    def writeLongNat(x: Long): Unit = {
      def writeNatPrefix(x: Long): Unit = {
        val y = x >>> 7
        if (y != 0L) writeNatPrefix(y)
        writeByte(((x & 0x7f) | 0x80).toInt)
      }
      val y = x >>> 7
      if (y != 0L) writeNatPrefix(y)
      writeByte((x & 0x7f).toInt)
    }
  }
}
