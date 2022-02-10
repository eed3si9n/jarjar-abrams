package testpkg

import verify._
import com.eed3si9n.jarjarabrams.scalasig.ScalaSigAnnotationVisitor

object ScalaSigAnnotationVisitorSpec extends BasicTestSuite {
  test("nextChunk") {
    import ScalaSigAnnotationVisitor.nextChunk

    val chars1 = Array('a', 'b', 'c', 'd', 'e', 'f', 'g', 'h')
    assert(nextChunk(chars1, chars1.length, 3).toSeq.isEmpty)
    assert(nextChunk(chars1, 0, 3).toSeq == Seq('a', 'b', 'c'))
    assert(nextChunk(chars1, 3, 3).toSeq == Seq('d', 'e', 'f'))
    assert(nextChunk(chars1, 6, 3).toSeq == Seq('g', 'h'))

    val chars2 = Array('\u0000', 'a', 'b', 'c', 'd', 'e', '\u0000', 'g', '\u0000')
    assert(nextChunk(chars2, 0, 3).toSeq == Seq('\u0000', 'a'))
    assert(nextChunk(chars2, 2, 3).toSeq == Seq('b', 'c', 'd'))
    assert(nextChunk(chars2, 5, 3).toSeq == Seq('e', '\u0000'))
    assert(nextChunk(chars2, 5, 2).toSeq == Seq('e'))
    assert(nextChunk(chars2, 7, 3).toSeq == Seq('g', '\u0000'))
    assert(nextChunk(chars2, 7, 2).toSeq == Seq('g'))
    assert(nextChunk(chars2, 8, 2).toSeq == Seq('\u0000'))
  }
}
