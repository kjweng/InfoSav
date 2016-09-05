package models

/**
  * Created by kaijunweng on 9/4/16.
  */
case class InfoSessionTime(var time: String, var period: String)

case class InfoSession(var employer: String,
                       var date: String,
                       var startTime: InfoSessionTime,
                       var endTime: InfoSessionTime,
                       var location: String,
                       var website: String)