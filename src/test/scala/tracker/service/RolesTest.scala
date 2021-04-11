package tracker.service

import org.scalatest.flatspec.AnyFlatSpec
import tracker.{Role, Roles}
import org.scalatest.matchers.should.Matchers._

class RolesTest extends AnyFlatSpec {
  "Roles" should "be parsable from string" in {
    Role.fromString("READER").getOrElse(fail("Reader role is not parsable")) shouldEqual Roles.Reader
    Role.fromString("USER").getOrElse(fail("User role is not parsable")) shouldEqual Roles.User
    Role.fromString("ADMIN").getOrElse(fail("Admin role is not parsable")) shouldEqual Roles.Admin
    Role.fromString("EDITOR").getOrElse(fail("Editor role is not parsable")) shouldEqual Roles.Editor
  }
  it should "be convertible to strings" in {
    Role.toString(Roles.Reader) shouldEqual "READER"
    Role.toString(Roles.User) shouldEqual "USER"
    Role.toString(Roles.Admin) shouldEqual "ADMIN"
    Role.toString(Roles.Editor) shouldEqual "EDITOR"
  }
}
