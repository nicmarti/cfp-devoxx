package library

/**
 * Timer utility that evaluates the time spent in a function.
 * Author: nmartignole
 * Created: 04/02/2013 10:17
 */
object Benchmark {

  /**
   * Measures execution time of a code block and returns measurements.
   * @param code Code block to be benchmarked.
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