/**
 * Copyright (C) 2013 Kaj Magnus Lindberg (born 1979)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package test.e2e.code

import com.debiki.core.PostId
import com.debiki.core.Prelude._
import org.scalatest.time.{Seconds, Span}


/** Adds replies to the article or other comments.
  */
trait TestRater {
  self: DebikiBrowserSpec with StuffTestClicker =>

  val GoodRating = "interesting"
  val BadRating = "faulty"


  def rateComment(postId: PostId, rating: String) {
    // 1. There was an ElementNotVisibleException somewhere in this method,
    // but the stack trace was cropped, so don't no where.
    // Could it have been the action links in clickRateAction() { showActionLinks() ... } ?
    // 2. There was an error: """WebElement '.dw-page .dw-fs-r .dw-fi-submit' not found"""
    // — how is that possible?? when the rating tags are present!
    clickRateAction(postId)
    eventually { clickRatingTag(rating) }
    clickSubmit()
  }


  private def clickRateAction(postId: PostId) {
    showActionLinks(postId)
    val rateLink = findActionLink_!(postId, "dw-a-rate")
    scrollIntoView(rateLink)
    click on rateLink
  }


  private def clickRatingTag(rating: String) {
    assert(!rating.contains("\""))
    val query = i"""
      |//div[contains(concat(" ", @class, " "), " dw-fs-r ")]
      |//div[contains(concat(" ", @class, " "), " dw-r-tag-set ")]
      |//span[contains(concat(" ", @class, " "), " ui-button-text ") and text()="$rating"]
      |"""
    val anyRatingTag = find(xpath(query))
    val ratingTag = anyRatingTag getOrElse fail(s"No rating tag with text: `$rating'")
    click on ratingTag
  }


  private def clickSubmit() {
    click on cssSelector(".dw-page .dw-fs-r .dw-fi-submit")
  }

}

