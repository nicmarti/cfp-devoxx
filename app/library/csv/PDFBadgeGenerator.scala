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

import java.io.{FileOutputStream, File}
import scala.collection.JavaConverters._
import scala.io.Codec
import com.itextpdf.text._
import scala.List

import com.itextpdf.text.pdf._
import models.ConferenceDescriptor
import play.api.i18n.Messages

/**
 * Utility class for Devoxx France, that help us to generate QRCode for the list of attendees
 * Created by nicolas on 10/04/2014.
 */
object PDFBadgeGenerator {

  final val NORMAL: Font = new Font(Font.FontFamily.HELVETICA, 8)
  final val FONT_COMPANY: Font = new Font(Font.FontFamily.HELVETICA, 8)

  final val lightGreen: BaseColor = new BaseColor(91, 255, 18)
  final val lightOrange: BaseColor = new BaseColor(238, 134, 31)
  final val otherColor: BaseColor = new BaseColor(15, 104, 231)

  final val lightBlue: BaseColor = new BaseColor(102, 200, 255)
  final val darkBlue: BaseColor = new BaseColor(1, 39, 123)
  final val lightRed: BaseColor = new BaseColor(242, 129, 129)
  final val lightGray: BaseColor = new BaseColor(90, 90, 90)

  def handle(fileName: String) = {
    val inputFile = new File(fileName)
    val lines = scala.io.Source.fromFile(inputFile)(Codec.UTF8).getLines().toList

    val badgeLines: List[BadgeLine] = lines.flatMap {
      line: String => BadgeLine.parse(line)
    }

    val badgesByID = badgeLines.drop(1).groupBy(_.id)

    println("Total "+badgeLines.size)

    badgesByID.map {
      case (groupName: String, groupOfBadges: List[BadgeLine]) =>
        createPDFFile(inputFile, groupName, groupOfBadges)
    }


  }

  def createPDFFile(sourceFile: File, groupName: String, groupOfBadges: List[BadgeLine]) = {
    play.Logger.info(s"Processing $groupName")

    val file = new File(sourceFile.getParentFile, sourceFile.getName + ".pdf")
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
    largeTable.setTotalWidth(Array[Float](Utilities.millimetersToPoints(63.5f), Utilities.millimetersToPoints(2.54f), Utilities.millimetersToPoints(63.5f), Utilities.millimetersToPoints(2.54f), Utilities.millimetersToPoints(63.5f)))
    largeTable.setLockedWidth(true)

    // step 4
    var cell: PdfPCell = null
    var sticker: PdfPTable = null

    var cpt = 1
    val gouttiere = new PdfPCell
    gouttiere.disableBorderSide(Rectangle.BOX)

    groupOfBadges.foreach {
      badge: BadgeLine =>
        sticker = generateSticker(badge, embeddeFont, gothamFont)
        cell = new PdfPCell
        cell.setMinimumHeight(Utilities.millimetersToPoints(38.1f))
        cell.setFixedHeight(Utilities.millimetersToPoints(38.1f))
        cell.setVerticalAlignment(Element.ALIGN_MIDDLE)
        cell.setHorizontalAlignment(Element.ALIGN_CENTER)
        cell.disableBorderSide(Rectangle.BOX)
        cell.addElement(sticker)
        largeTable.addCell(cell)

        if (cpt % 3 != 0) {
          largeTable.addCell(gouttiere)
        }
        cpt = cpt + 1
    }

    if ((groupOfBadges.size - 1) % 4 == 0) {
      val bouchon = new PdfPCell
      bouchon.addElement(new Phrase("  "))
      bouchon.setMinimumHeight(Utilities.millimetersToPoints(38.1f))
      bouchon.disableBorderSide(Rectangle.BOX)
      largeTable.addCell(bouchon)
      largeTable.addCell(gouttiere)
      largeTable.addCell(bouchon) // 2 times
    }

    if ((groupOfBadges.size - 1) % 5 == 0) {
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
    document.addTitle("Badges "+Messages("longYearlyName"))

    document.close()
  }

  private def generateSticker(badge: BadgeLine, embeddedFont: BaseFont, gothamFont: BaseFont): PdfPTable = {
    val sticker1: PdfPTable = new PdfPTable(Array[Float](1, 1, 1))

    sticker1.setTotalWidth(Utilities.millimetersToPoints(63.5f))
    sticker1.setLockedWidth(true)

    // Type de badge
    val p4: Phrase = new Phrase(badge.badgeType)
    p4.setFont(NORMAL)

    val cellTypeBadge: PdfPCell = new PdfPCell
    cellTypeBadge.setFixedHeight(Utilities.millimetersToPoints(7.5f))
    cellTypeBadge.setPaddingLeft(4f)

    badge.badgeType.toUpperCase() match {
      case "COMBI" =>
        cellTypeBadge.setBackgroundColor(lightGreen)
      case "UNIV" =>
        cellTypeBadge.setBackgroundColor(lightOrange)
      case "CONF" =>
        cellTypeBadge.setBackgroundColor(lightBlue)
      case "DCAMP" =>
        cellTypeBadge.setBackgroundColor(lightOrange)
      case "STUDENT" =>
        cellTypeBadge.setBackgroundColor(lightOrange)
      case other =>
        println("Unknown badgeType [" + badge.badgeType + "]")
        cellTypeBadge.setBackgroundColor(lightRed)
    }

    cellTypeBadge.disableBorderSide(Rectangle.BOX)
    cellTypeBadge.addElement(p4)

    sticker1.addCell(cellTypeBadge)

    //********************
    // Lettrine
    val pLettrine: Phrase = new Phrase(badge.lastName.toUpperCase.take(3).toString)
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
    val qrcode: BarcodeQRCode = new BarcodeQRCode(badge.getQRCodeString, 9, 9, null)
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

    val fontFN: Font = new Font(embeddedFont, 10)
    val p2: Phrase = new Phrase(badge.firstName, fontFN)
    val cellFirstName: PdfPCell = new PdfPCell
    cellFirstName.addElement(p2)
    cellFirstName.disableBorderSide(Rectangle.BOX)
    cellFirstName.setColspan(2)
    cellFirstName.setFixedHeight(Utilities.millimetersToPoints(9.61f))
    cellFirstName.setPaddingLeft(Utilities.millimetersToPoints(3f))

    sticker1.addCell(cellFirstName)

    val pLastName: Phrase = new Phrase(badge.lastName.toUpperCase, fontFN)
    val cellLastName: PdfPCell = new PdfPCell
    cellLastName.addElement(pLastName)
    cellLastName.disableBorderSide(Rectangle.BOX)
    cellLastName.setColspan(2)
    cellLastName.setFixedHeight(Utilities.millimetersToPoints(9.65f))
    cellLastName.setPaddingLeft(Utilities.millimetersToPoints(3f))
    sticker1.addCell(cellLastName)

    // **********
    var phraseCompany: Phrase = new Phrase(badge.company, new Font(embeddedFont, 12))

    val cellCompany: PdfPCell = new PdfPCell
    cellCompany.addElement(phraseCompany)
    cellCompany.disableBorderSide(Rectangle.BOX)
    cellCompany.setColspan(2)
    cellCompany.setPaddingLeft(Utilities.millimetersToPoints(3f))

    sticker1.addCell(cellCompany)

    // VIP, PRESSe, etc
    // Type de badge
    val zoneID: Phrase = new Phrase(badge.id)
    zoneID.setFont(NORMAL)

    val cellZoneID: PdfPCell = new PdfPCell
    cellZoneID.setFixedHeight(Utilities.millimetersToPoints(7.5f))
    cellZoneID.setPaddingLeft(4f)

    badge.id.toUpperCase match {
      case "VIP" =>
        cellZoneID.setBackgroundColor(lightRed)
      case "PRESSE" =>
        cellZoneID.setBackgroundColor(lightRed)
      case "SPEAKER" =>
        cellZoneID.setBackgroundColor(lightRed)
      case "GUEST" =>
        cellZoneID.setBackgroundColor(BaseColor.CYAN)
      case "ATTENDEE" =>
        cellZoneID.setBackgroundColor(BaseColor.LIGHT_GRAY)
      case "COMBI" =>
        cellZoneID.setBackgroundColor(BaseColor.LIGHT_GRAY)
      case "DVX4K" =>
        cellZoneID.setBackgroundColor(BaseColor.YELLOW)
      case "ORGA" =>
        cellZoneID.setBackgroundColor(BaseColor.RED)
      case "VIDEO" =>
        cellZoneID.setBackgroundColor(BaseColor.RED)
      case "FORMATION" =>
        cellZoneID.setBackgroundColor(BaseColor.YELLOW)
      case "AMD" =>
        cellZoneID.setBackgroundColor(BaseColor.ORANGE)
      case other =>
        println("Unknown ID " + badge.id)
        cellZoneID.setBackgroundColor(BaseColor.WHITE)
    }

    cellZoneID.disableBorderSide(Rectangle.BOX)
    cellZoneID.addElement(zoneID)

    sticker1.addCell(cellZoneID)

    sticker1
  }
}

