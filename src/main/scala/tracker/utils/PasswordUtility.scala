package tracker.utils

import java.security.SecureRandom
import java.util.Base64

import javax.crypto._
import javax.crypto.spec.PBEKeySpec
import org.log4s.{getLogger, Logger}

/**
  * Providing operations with passwords.
  */
class PasswordUtility {
  val DefaultIterations = 10000
  val random = new SecureRandom()
  val logger: Logger = getLogger("PasswordUtility")

  private def pbkdf2(password: String, salt: Array[Byte], iterations: Int): Array[Byte] = {
    val keySpec = new PBEKeySpec(password.toCharArray, salt, iterations, 256)
    val keyFactory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
    keyFactory.generateSecret(keySpec).getEncoded
  }

  /**
    * Hash password with random salt and defined iterations.
    * @param password - password to be hashed
    * @return hashed password
    */
  def hashPassword(password: String): String = {
    val salt = new Array[Byte](16)
    random.nextBytes(salt)
    val hash = pbkdf2(password, salt, DefaultIterations)
    val salt64 = Base64.getEncoder.encodeToString(salt)
    val hash64 = Base64.getEncoder.encodeToString(hash)

    val res = s"$DefaultIterations:$hash64:$salt64"
    res
  }

  /**
    * Check if plain password matching their hash in colon format (iterations:hash:salt)
    * @param password - plain password in colon format
    * @param passwordHash - password hash in colon format (iterations:hash:salt)
    * @return True if hash of plain password matches `passwordHash`, otherwise false
    */
  def checkPassword(password: String, passwordHash: String): Boolean = {
    passwordHash.split(":") match {
      case Array(it, hash64, salt64) if it.forall(_.isDigit) =>
        val hash = Base64.getDecoder.decode(hash64)
        val salt = Base64.getDecoder.decode(salt64)

        val calculatedHash = pbkdf2(password, salt, it.toInt)

        calculatedHash.sameElements(hash)

      case _ => false
    }
  }
}

object PasswordUtility extends PasswordUtility
