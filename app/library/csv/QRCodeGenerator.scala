/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2013 Association du Paris Java User Group.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of
 * this software and associated documentation files (the "Software"), to deal in
 * the Software without restriction, including without limitation the rights to
 * use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of
 * the Software, and to permit persons to whom the Software is furnished to do so,
 * subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
 * FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 * COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER
 * IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN
 * CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package library.csv

import java.io.File
import scala.io.Codec
   import net.glxn.qrgen._
import org.apache.commons.lang3.StringUtils

/**
 * Created by nicolas on 12/04/2014.
 */
object QRCodeGenerator {

  def handle(fileName: String) = {
    val inputFile = new File(fileName)
    val lines = scala.io.Source.fromFile(inputFile)(Codec.UTF8).getLines().toList


    lines.drop(1).foreach {
      line: String =>
        generateQRCode(Attendee.parse(line), inputFile.getParentFile)
    }

  }

  private def generateQRCode(attendee: Attendee, outputDir:File) = {

    val finalFile = new File(outputDir, attendee.id+".png")

    val tmpFile = QRCode.from(attendee.getQRCodeString).file()
    tmpFile.renameTo(finalFile)

  }
}


