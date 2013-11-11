package controllers

import play.api.mvc._
import models.Webuser

/**
 * The backoffice controller for the CFP technical commitee.
 *
 * Author: @nmartignole
 * Created: 11/11/2013 09:09 in Thalys, heading to Devoxx2013
 */
object CFPAdmin extends Controller with Secured {

  def index()=IsMemberOf("cfp"){
    email => implicit request =>
      Ok(views.html.CFPAdmin.index())
  }
}


