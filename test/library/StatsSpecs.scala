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

import org.specs2.mutable._

/**
 * Specs test for Standard Deviation.
 * Created by nicolas on 28/01/2014.
 */
class StatsSpecs extends Specification {
  "The Stats average function" should {
    "returns 0 for empty list" in {
      Stats.average(Nil) must beEqualTo(0.toDouble)
    }
    "returns same value if List contains only one element" in {
      Stats.average(List(10)) must beEqualTo(10.toDouble)
    }
    "returns correct average value" in {
      Stats.average(List(10,10)) must beEqualTo(10.toDouble)
    }
    "returns a rounded value" in {
      Stats.average(List(10,34,23)) must beEqualTo(22.33)
    }
  }

  "The standard deviation function" should{
    "returns 0 if element is empty" in{
      Stats.standardDeviation(Nil) must beEqualTo(0.toDouble)
    }
    "returns 0 if element contains one value" in{
      Stats.standardDeviation(List(12334)) must beEqualTo(0.toDouble)
    }
    "returns 0 if elements are identical" in{
      Stats.standardDeviation(List(10,10,10)) must beEqualTo(0.toDouble)
    }
    "returns the correct rounded value for standard deviation" in{
      Stats.standardDeviation(List(1,2,3,4,5,6)) must beEqualTo(1.871)
    }
  }
}

