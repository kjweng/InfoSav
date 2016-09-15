package models

import play.api.libs.json._

/**
  * Created by kaijunweng on 9/4/16.
  */
case class InfoSessionTime(var time: String, var period: String)

case class InfoSessionDetails(var date: String,
                              var startTime: InfoSessionTime,
                              var endTime: InfoSessionTime,
                              var location: String,
                              var website: String,
                              var tentative: Boolean)

object InfoSessionTime {

  implicit object InfoSessionTimeFormat extends Format[InfoSessionTime] {
    def writes(infoSessionTime: InfoSessionTime): JsValue = {
      JsObject(Seq(
        "time" -> JsString(infoSessionTime.time),
        "period" -> JsString(infoSessionTime.period)
      ))
    }

    def reads(json: JsValue): JsResult[InfoSessionTime] = {
      JsSuccess(InfoSessionTime(
        (json \ "time").as[String],
        (json \ "period").as[String]
      ))
    }
  }

}

object InfoSessionDetails {

  implicit object InfoSessionDetailsFormat extends Format[InfoSessionDetails] {
    def writes(infoSessionDetails: InfoSessionDetails): JsValue = {
      JsObject(Seq(
        "date" -> JsString(infoSessionDetails.date),
        "startTime" -> Json.toJson(infoSessionDetails.startTime),
        "endTime" -> Json.toJson(infoSessionDetails.endTime),
        "location" -> JsString(infoSessionDetails.location),
        "website" -> JsString(infoSessionDetails.website),
        "tentative" -> JsBoolean(infoSessionDetails.tentative)
      ))
    }

    def reads(json: JsValue): JsResult[InfoSessionDetails] = {
      JsSuccess(InfoSessionDetails(
        (json \ "date").as[String],
        (json \ "startTime").as[InfoSessionTime],
        (json \ "endTime").as[InfoSessionTime],
        (json \ "location").as[String],
        (json \ "website").as[String],
        (json \ "tentative").as[Boolean]
      ))
    }

  }

}

case class InfoSession(var employer: String,
                       var infoSessionDetails: List[InfoSessionDetails])