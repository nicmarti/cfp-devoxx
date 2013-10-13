package models

import reactivemongo.bson.BSONObjectID

/**
 * Proposal
 *
 * Author: nicolas
 * Created: 12/10/2013 15:19
 */
case class Proposal(id: Option[BSONObjectID], event:String, code: String, lang: String)
