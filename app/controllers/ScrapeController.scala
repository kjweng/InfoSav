package controllers

import javax.inject.{Inject, Singleton}

import models._
import org.jsoup.Jsoup
import org.jsoup.nodes.{Document, Element}
import org.jsoup.select.Elements
import play.api.mvc._
import play.api.libs.ws._
import org.sedis.Pool
import play.api.libs.json._

import scala.collection.JavaConversions._
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Success, Failure}


/**
  * Created by kaijunweng on 9/4/16.
  */
@Singleton
class ScrapeController @Inject() (implicit ws: WSClient, exec: ExecutionContext, sedisPool: Pool) extends Controller {
  val InvalidSessions = List(
    "NO INFO SESSIONS",
    "STUDY DAY",
    "LABOUR DAY",
    "CLOSED INFO SESSION",
    "LECTURES BEGIN",
    "THANKSGIVING HOLIDAY",
    "MAIN POSTING 1 OPENS",
    "MAIN POSTING 2 OPENS",
    "MAIN POSTING 1 CLOSES",
    "MAIN POSTING 2 CLOSES",
    "MAIN INTERVIEWS START",
    "MAIN INTERVIEWS END",
    "MAIN CONTINUOUS INTERVIEWS START",
    "TBD"
  )

  def getResultData(month: Int, year: Int): Future[WSResponse] = {
    val request = ws.url("http://www.ceca.uwaterloo.ca/students/sessions.php")
      .withQueryString(("month_num", month.toString), ("year_num", year.toString))
    request.get()
  }

  def sanitizeInfoSession(infoSession: Element): String = {
    val attr = infoSession.attr("onmouseover")
    val idx_start = attr.indexOf('(')
    val idx_end = attr.lastIndexOf(",OFFSETY")
    attr.substring(idx_start + 1, idx_end)
  }

  def parseInfoSession(doc: Document) = {
    val infoSessionMap = doc.select("b").collect {
      case element if element.nextSibling != null =>
        element.text() -> element.nextSibling.toString
    }.toMap
    val time = infoSessionMap.getOrDefault("Time", "").split(" ")
    val startTime = InfoSessionTime(time = time(1), period = time(2))
    val endTime = InfoSessionTime(time = time(4), period = time(5))
    val employer = infoSessionMap.getOrDefault("Employer", "")
    List(InfoSession)
    InfoSession(
      employer = employer.substring(2).trim.replace("*", ""),
      List(InfoSessionDetails(tentative = employer.charAt(0) == '*',
        date = infoSessionMap.getOrDefault("Date", "").substring(2).trim,
        startTime = startTime,
        endTime = endTime,
        location = infoSessionMap.getOrDefault("Location", "").substring(2).trim,
        website = infoSessionMap.getOrDefault("Web Site:", "").trim)
      )
    )
  }

  def getInfoSessionLinks(tableContent: Elements): List[String] = {
    val links = tableContent.select("a[href]")

    links.subList(3, links.size())   // Start at fourth element because first three are not part of the calendar
      .filter(element => !InvalidSessions.contains(element.text().toUpperCase)).toList
      .map(sanitizeInfoSession(_))
  }

  def getInfoSession(infoSessionLink: String): InfoSession = {
    parseInfoSession(Jsoup.parseBodyFragment(infoSessionLink))
  }

  def pushInfoSessionToRedis(infoSession: InfoSession) = {
    Json.toJson(infoSession.infoSessionDetails)
    val listSize = sedisPool.withJedisClient(client => {
      client.rpush(infoSession.employer, Json.toJson(infoSession.infoSessionDetails).toString)
    })
   // println(s"Employer ${infoSession.employer} has a list size of $listSize")
  }

  def getInfoSessionDetailsFromRedis(employer: String): Option[InfoSession] = {
    sedisPool.withJedisClient(client => {
      val jsonData = Json.toJson(client.lrange(employer, 0, -1).map(Json.toJson(_)))
      jsonData.validate[List[InfoSessionDetails]] match {
        case s: JsSuccess[List[InfoSessionDetails]] => Option { InfoSession(employer, s.get) }
        case _ => None;
      }
    })
  }

  def clearRedis() = {
    sedisPool.withJedisClient(client => {
      client.flushDB
    })
  }


  def scrape = Action.async {
    println("Starting InfoSavage Scraping")
    clearRedis
    val resultData = for {
      resultData1 <- getResultData(9, 2016)
      resultData2 <- getResultData(10, 2016)
      resultData3 <- getResultData(11, 2016)
    } yield List(resultData1, resultData2, resultData3)

    val infoSessionLinksFuture = resultData.map(_.flatMap(result => {
      val table = Jsoup.parse(result.body).select("table")
      getInfoSessionLinks(table)
    }))

    val infoSessionDataFuture = infoSessionLinksFuture.map(_.map(getInfoSession(_)))
    infoSessionDataFuture.map(_.map(pushInfoSessionToRedis(_))).onComplete {
      case Success(value) => println(getInfoSessionDetailsFromRedis("Google"))
      case Failure(e) => println(e)
    }
    infoSessionDataFuture.map(infoSessionData =>
      Ok(infoSessionData.map(infoSession => {
        (infoSession.employer, Json.toJson(infoSession.infoSessionDetails(0)))
      }).toString)
    )
  }

}
