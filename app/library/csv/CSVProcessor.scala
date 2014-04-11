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

import java.io.{FileOutputStream, File, FileReader}
import au.com.bytecode.opencsv.CSVReader
import library.VCard
import scala.collection.JavaConverters._
import org.apache.commons.lang3.{StringEscapeUtils, StringUtils}
import scala.io.{Codec, BufferedSource}
import com.itextpdf.text.Font
import com.itextpdf.text.BaseColor
import com.itextpdf.text._
import com.itextpdf.text.pdf._
import com.itextpdf.text.pdf.qrcode.EncodeHintType
import com.itextpdf.text.pdf.qrcode.ErrorCorrectionLevel

/**
 * Utility class for Devoxx France, that help us to generate QRCode for the list of attendees
 * Created by nicolas on 10/04/2014.
 */
object CSVProcessor {

  final val NORMAL: Font = new Font(Font.FontFamily.HELVETICA, 10)
  final val FONT_COMPANY: Font = new Font(Font.FontFamily.HELVETICA, 10)
  final val lightGreen: BaseColor = new BaseColor(171, 255, 171)
  final val lightOrange: BaseColor = new BaseColor(255, 178, 102)
  final val lightBlue: BaseColor = new BaseColor(102, 200, 255)
  final val darkBlue: BaseColor = new BaseColor(0, 49, 113)
  final val lightRed: BaseColor = new BaseColor(242, 129, 129)
  final val lightGray: BaseColor = new BaseColor(90, 90, 90)

  def handle(fileName: String) = {
    val inputFile = new File(fileName)
    val lines = scala.io.Source.fromFile(inputFile)(Codec.UTF8).getLines()
    val file = new File(inputFile.getParentFile,"output.pdf")
    file.createNewFile()

    // step 1
    val pageSize: Rectangle = new Rectangle(Utilities.millimetersToPoints(210f), Utilities.millimetersToPoints(297f))

    val document: Document = new Document(pageSize,
      Utilities.millimetersToPoints(7f), //left margin
      Utilities.millimetersToPoints(7f), //right margin
      Utilities.millimetersToPoints(15.7f),
      Utilities.millimetersToPoints(14.6f))

    // step 2
    PdfWriter.getInstance(document, new FileOutputStream(file))
    document.open()

    // step 3 Create font
    val embeddeFont: BaseFont = BaseFont.createFont("public/font/museo_slab_500-webfont.ttf", BaseFont.WINANSI, true)

    // Create table
    val largeTable: PdfPTable = new PdfPTable(2)
    largeTable.setTotalWidth(Utilities.millimetersToPoints(195f))
    largeTable.setLockedWidth(true)

    // step 4
    var cell: PdfPCell = null
    var sticker: PdfPTable = null

    import scala.collection.JavaConversions._

    lines.foreach {
      line: String =>
        sticker = generateSticker(Attendee.parse(line), embeddeFont, day=0)
        cell = new PdfPCell
        cell.addElement(sticker)
        cell.setMinimumHeight(Utilities.millimetersToPoints(38.1f))
        cell.setVerticalAlignment(Element.ALIGN_MIDDLE)
        largeTable.addCell(cell)
    }

    //
    //    if (attendees.size % 2 eq 1) {
    //      cell = new PdfPCell
    //      cell.addElement(new Phrase("Sample"))
    //      cell.setMinimumHeight(Utilities.millimetersToPoints(38.1f))
    //      cell.setVerticalAlignment(PdfPCell.ALIGN_MIDDLE)
    //      largeTable.addCell(cell)
    //    }


    document.add(largeTable)


    document.addCreationDate()
    document.addAuthor("Nicolas MARTIGNOLE")
    document.addCreator("Play! Framework")
    document.addTitle("Badges Devoxx France 2014")

    document.close()

  }

  private def generateSticker(attendee: Attendee, embeddedFont: BaseFont, day: Int): PdfPTable = {
    val sticker1: PdfPTable = new PdfPTable(Array[Float](1, 2, 2))
    sticker1.setTotalWidth(Utilities.millimetersToPoints(63.5f))
    sticker1.setLockedWidth(true)
    var img: Image = null
    val qrcode: BarcodeQRCode = new BarcodeQRCode(attendee.lastName + "," + attendee.firstName + "," + attendee.email + "," + attendee.company, 9, 9, null)
    val cell: PdfPCell = new PdfPCell
    try {
      img = qrcode.getImage
      cell.addElement(img)
    }
    catch {
      case e: BadElementException => {
        play.Logger.error("QRCode error", e)
        cell.addElement(new Phrase("QRCode ERR"))
      }
    }
    cell.setHorizontalAlignment(Element.ALIGN_CENTER)
    cell.setVerticalAlignment(Element.ALIGN_MIDDLE)
    cell.setBorder(1)
    cell.setRowspan(5)
    sticker1.addCell(cell)
    val pLastName: Phrase = new Phrase(attendee.lastName.toUpperCase, NORMAL)
    val cell1: PdfPCell = new PdfPCell
    cell1.addElement(pLastName)
    cell1.enableBorderSide(4)
    sticker1.addCell(cell1)
    val p3: Phrase = new Phrase
    p3.setFont(FONT_COMPANY)
    if (attendee.company == null) {
      p3.add(" ")
    }
    else {
      p3.add(StringUtils.abbreviate(attendee.company, 18))
    }
    val c3: PdfPCell = new PdfPCell
    c3.setNoWrap(true)
    c3.addElement(p3)
    c3.setBorder(0)
    sticker1.addCell(c3)
    val fontFN: Font = new Font(embeddedFont, 16)
    fontFN.setColor(darkBlue)
    val p2: Phrase = new Phrase(attendee.firstName.toUpperCase(), fontFN)
    val c2: PdfPCell = new PdfPCell
    c2.addElement(p2)
    c2.setBorder(0)
    sticker1.addCell(c2)
    val p4: Phrase = new Phrase(attendee.registration_type)
    p4.setFont(NORMAL)
    val c4: PdfPCell = new PdfPCell
    c4.addElement(p4)
    c4.setBorder(0)
    c4.setBackgroundColor(lightRed)
    if (attendee.registration_type == "COMBI") {
      c4.setBackgroundColor(lightGreen)
    }
    if (attendee.registration_type == "UNI") {
      c4.setBackgroundColor(lightOrange)
    }
    if (attendee.registration_type == "CONF") {
      c4.setBackgroundColor(lightBlue)
    }
    if (attendee.registration_type == "DCAMP") {
      c4.setBackgroundColor(lightOrange)
    }
    if (attendee.registration_type == "STUDENT") {
      c4.setBackgroundColor(lightOrange)
    }
    if (day != 0) {
      var p5: Phrase = null
      val c5: PdfPCell = new PdfPCell
      day match {
        case 1 =>
          p5 = new Phrase("MERCREDI")
          c5.setBackgroundColor(lightOrange)
        case 2 =>
          p5 = new Phrase("JEUDI")
          c5.setBackgroundColor(lightBlue)
        case 3 =>
          p5 = new Phrase("VENDREDI")
          c5.setBackgroundColor(lightGreen)
        case _ =>
          p5 = new Phrase("TRAINING")
      }
      p5.setFont(NORMAL)
      c5.addElement(p5)
      sticker1.addCell(c5)
    }
    else {
      sticker1.addCell(c4)
    }
    val font: Font = new Font(embeddedFont, 5)
    font.setColor(lightGray)
    val p6: Phrase = new Phrase("Devoxx France", font)
    val c6: PdfPCell = new PdfPCell
    c6.addElement(p6)
    c6.setBorder(0)
    c6.setColspan(2)
    sticker1.addCell(c6)
    sticker1
  }
}

case class Attendee(id: String, firstName: String, lastName: String, email: String,
                    position: String, opt_in_phone: String, phone: String,
                    company: String, organization: String, attendee_type: String,
                    registration_type: String)

object Attendee {
  def parse(line: String): Attendee = {
    val tokens = line.split(";")
    val id = tokens(0)
    val firstName = tokens(1)
    val lastName = tokens(2)
    val email = tokens(3)
    val position = tokens(4)
    val opt_in_phone = tokens(5)
    val phone = tokens(6)
    val company_name = tokens(7)
    val organization = tokens(8)
    val attendee_type = tokens(9)
    val registration_type = tokens(10)
    Attendee(id,firstName,lastName,email, position, opt_in_phone, phone,company_name, organization, attendee_type, registration_type)
  }
}
