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

package library

import scala.math.BigDecimal.RoundingMode

/**
 * Compute standard deviation of a List of Integer.
 * Created by nicolas on 28/01/2014.
 */
object Stats {

  def average(elements: List[Double]): Double = {
    elements match {
      case Nil => 0
      case _ => {
        val total = elements.sum
        BigDecimal(total / elements.size).setScale(2, RoundingMode.HALF_EVEN).toDouble
      }
    }
  }

  def standardDeviation(elements: List[Double]): Double = {
    elements match {
      case Nil => 0
      case element :: Nil => 0
      case _ => {
        val total = elements.sum
        val mean = BigDecimal(total / elements.size).toDouble
        val factor: Double = 1.0 / (elements.length.toDouble - 1)
        val sumOfDeviationToSquare = factor * elements.foldLeft(0.toDouble) {
          (acc, x) =>
            acc + math.pow(x - mean, 2)
        }
        BigDecimal(math.sqrt(sumOfDeviationToSquare)).setScale(3,RoundingMode.HALF_EVEN).toDouble

      }
    }
  }
}
