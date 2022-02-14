package library

object Scores {
  /**
    * Let's say :
    * - we have 490 conference ratings
    * - we are planning to keep only 96 conference
    *
    * We are going to consider 2 different linear scales :
    * - 1 from ranks 0->96
    * - 1 from ranks 97->490
    *
    * Now, let's consider we have :
    * - rated 7.5 a conference talk
    * - our 7.5 rating comes at rank 90 out of these 490 conference ratings
    * => Then the "score" of this talk will be 10 - roundFloor((90 / 96)x5) = 6
    *
    * Now with :
    * - rate of 9.2
    * - this rating coming at rank 13 out of these 490 conference ratings
    * => Then the "score" of this talk will be 10 - roundFloor((13 / 96)x5) = 10
    *
    * And finally with :
    * - rate of 6.1
    * - this rating coming at rank 330 out of these 490 conference ratings
    * => Then the "score" of this talk will be 5 - roundFloor( ((330-96) / (490-96))x4 ) = 3
    */
  def calculateVisualScoreOf(value: Double, availableSlots: Long, availableSortedScores: List[Double]): Long = {
    availableSortedScores.zipWithIndex.find(_._1 == value).map { case (_, valueIndex) =>
      (if(availableSlots <= 0) {
        // Once we've filled every available slots, using only a single (linear) scale
        10 - Math.floor(valueIndex * 9 / availableSortedScores.size)
      } else if (valueIndex < availableSlots) {
        // in the [0-availableSlots[ range, retrieving linear score from this range between 6-10
        10 - Math.floor(valueIndex * 5 / availableSlots)
        // in the [availableSlots, availableSortedScores.size[, retrieving linear score from this range between 1-5
      } else {
        5 - Math.floor((valueIndex - availableSlots) * 4 / (availableSortedScores.size - availableSlots))
      }).toLong
    }.getOrElse(0)
  }
}
