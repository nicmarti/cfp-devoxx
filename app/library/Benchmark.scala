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

/**
 * Timer utility that evaluates the time spent in a function.
 * Author: Nicolas MARTIGNOLE @nmartignole
 * Created: 04/02/2013 10:17
 */
object Benchmark {

  /**
   * Measures execution time of a code block and returns measurements.
   * @param f Code block to be benchmarked.
   * @return Execution time of the code block.
   */
  def measure[T](f: () => T, msg: String) = {
    val startTime = System.nanoTime
    val res = f()
    val endTime = System.nanoTime
    val total = (endTime - startTime) / 1E6
    if (total > 999) {
      play.Logger.of("application.Benchmark").debug(msg + " Ellapsed: " + total / 1E3 + " s")
    } else {
      play.Logger.of("application.Benchmark").debug(msg + " Ellapsed: " + total + " ms")
    }
    res
  }
}