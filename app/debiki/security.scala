/**
 * Copyright (c) 2011 Kaj Magnus Lindberg (born 1979)
 */

package com.debiki.v0

import com.debiki.v0.Prelude._
import debiki.DebikiHttp._
import java.{util => ju, io => jio}
import play.api.mvc.Cookie
import scala.xml.{Text, Node, NodeSeq}


sealed abstract class XsrfStatus { def isOk = false }
case object XsrfAbsent extends XsrfStatus
case object XsrfNoSid extends XsrfStatus
case object XsrfBad extends XsrfStatus
case class XsrfOk(token: String) extends XsrfStatus {
  override def isOk = true
}


object Xsrf {
  private val _secretSalt = "w4k2i2ErK84o938"  // hardcoded, for now
  private val _hashLength = 14

  def check(xsrfToken: String, sidValue: Option[String]): XsrfStatus = {
    if (sidValue.isEmpty) return XsrfNoSid
    val xsrfFromSid: XsrfOk = create(sidValue.get)
    if (xsrfFromSid.token != xsrfToken) return XsrfBad
    xsrfFromSid
  }

  /**
   * Generates a new xsrf token, based on the SID,
   *
   * The sid itself isn't used directly, because if it's included
   * in the HTML (in the form xsrf hidden input), it'd be saved to
   * disk should you download the web page, and someone might find
   * it (perhaps you email the web page to someone).
   *
   * Alternatively, the browser could calculate the token since it has
   * the SID. But then I'd need to duplicate the implementation and
   * cannot add any salt.
   */
  def create(sid: String): XsrfOk =
    XsrfOk(hashSha1Base64UrlSafe(_secretSalt + sid) take _hashLength)

  def newSidAndXsrf(
        loginId: Option[String], userId: Option[String],
        displayName: Option[String]): (SidOk, XsrfOk, List[Cookie]) = {
    // Note that the xsrf token is created using the unencoded cookie value.
    val sidOk = Sid.create(loginId, userId, displayName)
    val xsrfOk = create(sidOk.value)
    val sidCookie = urlEncodeCookie("dwCoSid", sidOk.value)
    val xsrfCookie = urlEncodeCookie("dwCoXsrf", xsrfOk.token)
    (sidOk, xsrfOk, sidCookie::xsrfCookie::Nil)
  }
}


sealed abstract class SidStatus {
  def isOk = false
  def loginId: Option[String] = None
  def userId: Option[String] = None
  def displayName: Option[String] = None
}

case object SidAbsent extends SidStatus
case object SidBadFormat extends SidStatus
case object SidBadHash extends SidStatus
//case class SidExpired(millisAgo: Long) extends SidStatus


case class SidOk(
  value: String,
  ageInMillis: Long,
  override val loginId: Option[String],
  override val userId: Option[String],

  /**
   * Remembering the display name saves 1 database rondtrip every time
   * a page is rendered, because `Logged in as <name>' info is shown.
   *
   * (Were the display name not included in the SID cookie, one would have
   * to look it up in the database.)
   */
  override val displayName: Option[String])
  extends SidStatus {
  override def isOk = true
}


/**
 * Session ID stuff.
 *
 * A session id cookie is created on the first page view.
 * It is cleared on logout, and a new one generated on login.
 * Lift-Web's cookies and session state won't last across server
 * restarts and I want to be able to restart the app servers at
 * any time so I don't use Lift's stateful session stuff so very much.
 */
object Sid {
  val sidHashLength = 14
  private val _secretSidSalt = "w4k2i2rK8409kk3xk"  // hardcoded, for now
  private val _sidMaxMillis = 2 * 31 * 24 * 3600 * 1000  // two months
  //private val _sidExpireAgeSecs = 5 * 365 * 24 * 3600  // five years

  def check(value: String): SidStatus = {
    // Example value: 88-F7sAzB0yaaX.1312629782081.1c3n0fgykm  - no, obsolete
    if (value.length <= sidHashLength) return SidBadFormat
    val (hash, dotLoginUidNameDateRandom) = value splitAt sidHashLength
    val realHash = hashSha1Base64UrlSafe(
      _secretSidSalt + dotLoginUidNameDateRandom) take sidHashLength
    if (hash != realHash) return SidBadHash
    dotLoginUidNameDateRandom.drop(1).split('.') match {
      case Array(loginId, userId, nameNoDots, dateStr, randVal) =>
        val ageMillis = (new ju.Date).getTime - dateStr.toLong
        val displayName = nameNoDots.replaceAll("_", ".")
        //if (ageMillis > _sidMaxMillis)
        //  return SidExpired(ageMillis - _sidMaxMillis)
        SidOk(
          value = value,
          ageInMillis = ageMillis,
          loginId = if (loginId isEmpty) None else Some(loginId),
          userId = if (userId isEmpty) None else Some(userId),
          displayName = if (displayName isEmpty) None else Some(displayName))
      case _ => SidBadFormat
    }
  }

  /**
   * Creates and returns a new SID.
   */
  def create(
        loginId: Option[String], userId: Option[String],
        displayName: Option[String]): SidOk = {
    // For now, create a SID value and *parse* it to get a SidOk.
    // This is stupid and inefficient.
    val nameNoDots = displayName.map(
      _.replaceAll("\\.", "_")) // Could use replaceAllLiterally, Scala 2.9.1?
    val uid = "" // for now
    val loginUidNameDateRandom =
      loginId.getOrElse("") +"."+
         userId.getOrElse("") +"."+
         nameNoDots.getOrElse("") +"."+
         (new ju.Date).getTime +"."+
         (nextRandomString() take 10)
    val saltedHash = hashSha1Base64UrlSafe(
      _secretSidSalt +"."+ loginUidNameDateRandom) take sidHashLength
    val value = saltedHash +"."+ loginUidNameDateRandom

    check(value).asInstanceOf[SidOk]
  }

  def newCookie(
        loginId: Option[String], userId: Option[String],
        displayName: Option[String]): Cookie = {
    val sidOk = create(loginId, userId, displayName)
    urlEncodeCookie("dwCoSid", sidOk.value)
  }
}
