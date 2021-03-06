/**
 * Copyright (C) 2012-2013 Kaj Magnus Lindberg (born 1979)
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

package com.debiki.core

import com.debiki.core.{PostActionPayload => PAP}
import java.{util => ju}
import Prelude._


/**
 * Analyzes page actions, e.g. replies and their approvals, and
 * generates and returns the appropriate notifications.
 */
case class NotfGenerator(pageExclNewActions: PageParts, newActions: Seq[PostActionDtoOld]) {

  def page = pageExclNewActions


  def generateNotfs: Seq[NotfOfPageAction] = newActions flatMap (_ match {
    case action: PostActionDto[_] => action.payload match {
      case p: PostActionPayload.CreatePost =>
        makePersonalReplyNotf(
          new Post(page, action.asInstanceOf[PostActionDto[PAP.CreatePost]]))
      case e: PAP.EditPost =>
        Nil  // fix later, see "Note:" below
      case _: PAP.ReviewPost =>
        makeReviewNotfs(new Review(page, action.asInstanceOf[PostActionDto[PAP.ReviewPost]]))
      case _ =>
        Nil // skip for now
    }
    // Note:
    // If you add notfs (below) for other things than replies,
    // then, in debiki-server, update NotfHtmlRenderer.
    case app: EditApp =>
      Nil  // fix later, see note above
    case flag: Flag =>
      Nil  // fix later, see note above
    case _ =>
      Nil  // skip for now
  })


  private def makePersonalReplyNotf(post: Post,
          review: Option[Review] = None): List[NotfOfPageAction] = {

    val (triggerAction, approvalOpt) =
      review.map(r => (r, r.directApproval)) getOrElse (
        post, post.directApproval)

    // Don't notify about unapproved comments.
    if (approvalOpt.isEmpty)
      return Nil

    // Don't notify about preliminarily or temporarily approved comments
    // (wait until moderator upholds it).
    if (approvalOpt.map(_.isPermanent) == Some(false))
      return Nil

    // This might be a page config/template Post; if so, it has no parent.
    val postRepliedTo = post.parentPost getOrElse (return Nil)

    val userRepliedTo = postRepliedTo.user_!
    val replier = post.user_!

    // Don't notify the user about his/her own replies.
    if (replier.id == userRepliedTo.id)
      return Nil

    List(NotfOfPageAction(
        ctime = triggerAction.creationDati,
        recipientUserId = userRepliedTo.id,
        pageTitle = page.approvedTitleText.getOrElse("Unnamed page"),
        pageId = page.id,
        eventType = NotfOfPageAction.Type.PersonalReply,
        eventActionId = post.id,
        triggerActionId = triggerAction.id,
        recipientActionId = postRepliedTo.id,
        recipientUserDispName = userRepliedTo.displayName,
        eventUserDispName = replier.displayName,
        triggerUserDispName = None, // (skip, uninteresting?)
        emailPending =
           userRepliedTo.emailNotfPrefs == EmailNotfPrefs.Receive))
  }


  private def makeReviewNotfs(review: Review): List[NotfOfPageAction] = {
    // For now, only consider approvals of posts.
    if (!review.target.isInstanceOf[Post])
      return Nil

    lazy val postReviewed: Post = review.target.asInstanceOf[Post]
    lazy val userReviewed = postReviewed.user_!
    lazy val reviewer = review.user_!

    // If the postReviewed was rejected, don't notify anyone.
    if (review.directApproval.isEmpty)
      return Nil

    // If the postReviewed has already been permanently approved, a notification
    // has already been generated. Don't send another notification, *even* if
    // the postReviewed has been *edited* and it's the edits that we're approving.
    // (This could happen if the post is WellBehavedUser-approved on creation,
    // or if it is approved manually, then edited, and a new approval that concerns
    // the edits is saved.)
    val alreadySavedNotf =
      postReviewed.lastPermanentApprovalDati.map(
        _.getTime < review.creationDati.getTime) == Some(true)
    if (alreadySavedNotf)
      return Nil

    // If the postReviewed is a reply to the reviewer, don't notify her.
    // (She has obviously read the reply already.)
    if (Some(reviewer.id) == postReviewed.parentPost.map(_.user_!.id))
      return Nil

    /*
    // Notify the action doer about the approval.
    // -- Or don't? Do people want notfs that their comments were approved?
    // I'd want that, personally, but what if other people consider it spam
    // and unregister?  COULD generate notfs of approvals, *if* I change
    // Notifier.scala to send such notifications at most once a week? (unless
    // there are also other notfs, then they'd be sent at the same time)
    //
    // (Could skip this if reviewer == userReviewed, but needn't care about
    // that case, because moderators' actions are auto approved and we
    // won't get to here.)
    val authorNotf = NotfOfPageAction(
       ctime = review.creationDati,
       recipientUserId = userReviewed.id,
       pageTitle = page.approvedTitleText.getOrElse("Unnamed page"),
       pageId = page.id,
       eventType = NotfOfPageAction.Type.MyPostApproved,
       eventActionId = review.id,
       triggerActionId = review.id,
       recipientActionId = postReviewed.id,
       recipientUserDispName = userReviewed.displayName,
       eventUserDispName = reviewer.displayName,
       triggerUserDispName = None, // (skip, same as event user disp name)
       emailPending = userReviewed.emailNotfPrefs == EmailNotfPrefs.Receive)
       */

    // If the postReviewed is a reply to some other comment,
    // notify the the author of that other comment, that s/he has a reply
    // (that has been approved).
    val notfToAuthorOfParentPost =
      if (postReviewed.parentPost.isEmpty) Nil
      else makePersonalReplyNotf(postReviewed, Some(review))

    //authorNotf ::
    notfToAuthorOfParentPost
  }
}


// vim: fdm=marker et ts=2 sw=2 tw=80 fo=tcqwn list

