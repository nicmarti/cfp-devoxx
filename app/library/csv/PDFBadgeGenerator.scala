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
object PDFBadgeGenerator {

  final val NORMAL: Font = new Font(Font.FontFamily.HELVETICA, 10)
  final val FONT_COMPANY: Font = new Font(Font.FontFamily.HELVETICA, 8)

  final val lightGreen: BaseColor = new BaseColor(91, 255, 18)
  final val lightOrange: BaseColor = new BaseColor(238, 134, 31)

  final val lightBlue: BaseColor = new BaseColor(102, 200, 255)
  final val darkBlue: BaseColor = new BaseColor(1, 39, 123)
  final val lightRed: BaseColor = new BaseColor(242, 129, 129)
  final val lightGray: BaseColor = new BaseColor(90, 90, 90)

  def handle(fileName: String) = {
    val inputFile = new File(fileName)
    val lines = scala.io.Source.fromFile(inputFile)(Codec.UTF8).getLines().toList
    val file = new File(inputFile.getParentFile, "output.pdf")
    file.createNewFile()

    // step 1
    val pageSize: Rectangle = new Rectangle(Utilities.millimetersToPoints(210f), Utilities.millimetersToPoints(297f))

    val document: Document = new Document(pageSize,
      Utilities.millimetersToPoints(7.21f), //left margin
      Utilities.millimetersToPoints(7.21f), //right margin
      Utilities.millimetersToPoints(15.15f),
      Utilities.millimetersToPoints(15.15f))

    // step 2
    val writer = PdfWriter.getInstance(document, new FileOutputStream(file))

    document.open()

    // step 3 Create font
    val embeddeFont: BaseFont = BaseFont.createFont("public/font/museo_slab_500-webfont.ttf", BaseFont.WINANSI, BaseFont.EMBEDDED)
    val gothamFont: BaseFont = BaseFont.createFont("public/font/gotham-black-webfont-webfont.ttf", "UTF-8", BaseFont.EMBEDDED)

    // Create table
    val largeTable: PdfPTable = new PdfPTable(5)
    largeTable.setTotalWidth(Array[Float](Utilities.millimetersToPoints(63.5f),Utilities.millimetersToPoints(2.54f),Utilities.millimetersToPoints(63.5f),Utilities.millimetersToPoints(2.54f),Utilities.millimetersToPoints(63.5f)))
    largeTable.setLockedWidth(true)

    // step 4
    var cell: PdfPCell = null
    var sticker: PdfPTable = null


    var cpt=1
    val gouttiere = new PdfPCell


    lines.drop(1).foreach {
      line: String =>
        sticker = generateSticker(Attendee.parse(line), embeddeFont, gothamFont)
        cell = new PdfPCell
        cell.setMinimumHeight(Utilities.millimetersToPoints(38.1f))
        cell.setFixedHeight(Utilities.millimetersToPoints(38.1f))
        cell.setVerticalAlignment(Element.ALIGN_MIDDLE)
        cell.setHorizontalAlignment(Element.ALIGN_CENTER)
        cell.enableBorderSide(Rectangle.BOX)
        cell.setBorderColor(BaseColor.RED)
        cell.addElement(sticker)
        largeTable.addCell(cell)

        if(cpt%3!=0){
          largeTable.addCell(gouttiere)
        }
      cpt=cpt+1
    }



    if ((lines.size-1) % 4 == 0) {
      val bouchon = new PdfPCell
      bouchon.addElement(new Phrase("  "))
      bouchon.setMinimumHeight(Utilities.millimetersToPoints(38.1f))
      bouchon.disableBorderSide(Rectangle.BOX)
      largeTable.addCell(bouchon)
      largeTable.addCell(gouttiere)
      largeTable.addCell(bouchon) // 2 times
    }

    if ((lines.size-1) % 5 == 0) {
      val bouchon = new PdfPCell
      bouchon.addElement(new Phrase("  "))
      bouchon.setMinimumHeight(Utilities.millimetersToPoints(38.1f))
      bouchon.disableBorderSide(Rectangle.BOX)
      largeTable.addCell(bouchon)
    }


    document.add(largeTable)
    document.addCreationDate()
    document.addAuthor("Nicolas MARTIGNOLE")
    document.addCreator("Play! Framework")
    document.addTitle("Badges Devoxx France 2014")

    document.close()

  }


  private def generateSticker(attendee: Attendee, embeddedFont: BaseFont, gothamFont: BaseFont): PdfPTable = {
    val sticker1: PdfPTable = new PdfPTable(Array[Float](1, 1, 1))

    sticker1.setTotalWidth(Utilities.millimetersToPoints(63.5f))
    sticker1.setLockedWidth(true)

    // Type de badge
    val p4: Phrase = new Phrase(attendee.registration_type)
    p4.setFont(NORMAL)

    val cellLettr: PdfPCell = new PdfPCell
    cellLettr.setFixedHeight(Utilities.millimetersToPoints(7.5f))
    cellLettr.setPaddingLeft(4f)

    cellLettr.setBackgroundColor(lightRed)
    if (attendee.registration_type == "COMBI") {
      cellLettr.setBackgroundColor(lightGreen)
    }
    if (attendee.registration_type == "UNI") {
      cellLettr.setBackgroundColor(lightOrange)
    }
    if (attendee.registration_type == "CONF") {
      cellLettr.setBackgroundColor(lightBlue)
    }
    if (attendee.registration_type == "DCAMP") {
      cellLettr.setBackgroundColor(lightOrange)
    }
    if (attendee.registration_type == "STUDENT") {
      cellLettr.setBackgroundColor(lightOrange)
    }
    cellLettr.disableBorderSide(Rectangle.BOX)
    cellLettr.addElement(p4)

    sticker1.addCell(cellLettr)

    //********************
    // Lettrine
    val pLettrine: Phrase = new Phrase(attendee.lastName.toUpperCase.take(3).toString)
    pLettrine.setFont(new Font(gothamFont, 11, 0, BaseColor.WHITE))

    val cellLett: PdfPCell = new PdfPCell
    cellLett.setFixedHeight(Utilities.millimetersToPoints(7.5f))
    cellLett.disableBorderSide(Rectangle.BOX)
    cellLett.setHorizontalAlignment(Element.ALIGN_CENTER)
    cellLett.setVerticalAlignment(Element.ALIGN_TOP)
    cellLett.setBackgroundColor(BaseColor.DARK_GRAY)

    cellLett.addElement(pLettrine)

    sticker1.addCell(cellLett)

    //********************

    var img: Image = null
    val qrcode: BarcodeQRCode = new BarcodeQRCode(attendee.lastName + "," + attendee.firstName + "," + attendee.email + "," + attendee.company + ", " + attendee.organization + ", " + attendee.position + ", " + attendee.registration_type, 9, 9, null)
    val cellQRCode: PdfPCell = new PdfPCell
    try {
      img = qrcode.getImage
      cellQRCode.addElement(img)
    }
    catch {
      case e: BadElementException => {
        play.Logger.error("QRCode error", e)
        cellQRCode.addElement(new Phrase("QRCode ERR"))
      }
    }
    cellQRCode.setHorizontalAlignment(Element.ALIGN_RIGHT)
    cellQRCode.setVerticalAlignment(Element.ALIGN_TOP)
    cellQRCode.disableBorderSide(Rectangle.BOX)
    cellQRCode.setRowspan(3)
    cellQRCode.setFixedHeight(Utilities.millimetersToPoints(26.3f))

    sticker1.addCell(cellQRCode)

    //********************

    val fontFN: Font = new Font(embeddedFont,10)
    val p2: Phrase = new Phrase(attendee.firstName, fontFN)
    val cellFirstName: PdfPCell = new PdfPCell
    cellFirstName.addElement(p2)
    cellFirstName.disableBorderSide(Rectangle.BOX)
    cellFirstName.setColspan(2)
    cellFirstName.setFixedHeight(Utilities.millimetersToPoints(9.61f))
    cellFirstName.setPaddingLeft(Utilities.millimetersToPoints(3f))

    sticker1.addCell(cellFirstName)

    val pLastName: Phrase = new Phrase(attendee.lastName.toUpperCase, fontFN)
    val cellLastName: PdfPCell = new PdfPCell
    cellLastName.addElement(pLastName)
    cellLastName.disableBorderSide(Rectangle.BOX)
    cellLastName.setColspan(2)
    cellLastName.setFixedHeight(Utilities.millimetersToPoints(9.65f))
    cellLastName.setPaddingLeft(Utilities.millimetersToPoints(3f))
    sticker1.addCell(cellLastName)

    // **********
    var phraseCompany: Phrase = new Phrase(attendee.company, new Font(embeddedFont, 12))

    val cellCompany: PdfPCell = new PdfPCell
    cellCompany.addElement(phraseCompany)
    cellCompany.disableBorderSide(Rectangle.BOX)
    cellCompany.setColspan(3)
    cellCompany.setPaddingLeft(Utilities.millimetersToPoints(3f))

    sticker1.addCell(cellCompany)

    sticker1
  }
}

