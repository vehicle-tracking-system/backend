package tracker.utils

object Haversine {
  val R = 6372.8 //radius of Earth in km

  def compute(startLat: Double, startLon: Double, endLat: Double, endLon: Double): Double = {
    val dLat = math.toRadians(endLat - startLat)
    val dLon = math.toRadians(endLon - startLon)
    val lat1 = math.toRadians(startLat)
    val lat2 = math.toRadians(endLat)

    val a =
      math.sin(dLat / 2) * math.sin(dLat / 2) +
        math.sin(dLon / 2) * math.sin(dLon / 2) * math.cos(lat1) * math.cos(lat2)
    val c = 2 * math.atan2(math.sqrt(a), math.sqrt(1 - a))

    R * c
  }
}
