package tracker.mocked

trait DAOMock[A] {
  var maxId: Long
  var store: List[A]
}
