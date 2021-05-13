package tracker.mocked

import tracker._

trait DAOMock[A] {
  var maxId: Long
  var store: Store
}

case class Store(
    var vehicles: List[LightVehicle] = List.empty,
    var users: List[User] = List.empty,
    var fleets: List[LightFleet] = List.empty,
    var vehicleFleets: List[VehicleFleet] = List.empty,
    var tracks: List[LightTrack] = List.empty,
    var trackers: List[LightTracker] = List.empty,
    var positions: List[Position] = List.empty
) {}
