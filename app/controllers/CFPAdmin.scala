package controllers

import play.api.mvc._
import models.{Event, Review}

/**
 * The backoffice controller for the CFP technical commitee.
 *
 * Author: @nmartignole
 * Created: 11/11/2013 09:09 in Thalys, heading to Devoxx2013
 */
object CFPAdmin extends Controller with Secured {

  def index() = IsMemberOf("cfp") {
    email => implicit request =>
      val twentyEvents = Event.loadEvents(20)
      val allProposalsForReview = Review.allProposalsNotReviewed(email)
      Ok(views.html.CFPAdmin.cfpAdminIndex(twentyEvents, allProposalsForReview))
  }
}


