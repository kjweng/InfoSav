package controllers

import javax.inject.{Inject, Singleton}

import models.{InfoSession, InfoSessionTime}
import org.jsoup.Jsoup
import org.jsoup.nodes.{Document, Element}
import org.jsoup.select.Elements
import play.api.mvc._
import play.api.libs.ws._

import scala.collection.JavaConversions._
import scala.concurrent.{ExecutionContext, Future}


/**
  * Created by kaijunweng on 9/4/16.
  */
@Singleton
class ScrapeController @Inject() (implicit ws: WSClient, exec: ExecutionContext) extends Controller {
  val InvalidSessions = List(
    "NO INFO SESSIONS",
    "STUDY DAY",
    "LABOUR DAY",
    "CLOSED INFO SESSION",
    "LECTURES BEGIN",
    "THANKSGIVING HOLIDAY",
    "MAIN POSTING 1 OPENS",
    "MAIN POSTING 2 OPENS",
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
    InfoSession(
      employer = infoSessionMap.getOrDefault("Employer", "").substring(2).trim,
      date = infoSessionMap.getOrDefault("Date", "").substring(2).trim,
      startTime = startTime,
      endTime = endTime,
      location = infoSessionMap.getOrDefault("Location", "").substring(2).trim,
      website = infoSessionMap.getOrDefault("Web Site:", "").trim)
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

  def scrape = Action.async {
    println("Starting InfoSavage Scraping")
    val resultData = for {
      resultData1 <- getResultData(9, 2016)
      resultData2 <- getResultData(10, 2016)
      resultData3 <- getResultData(11, 2016)
    } yield List(resultData1, resultData2, resultData3)

    val infoSessionLinksFuture = resultData.map(_.flatMap(result => {
      val table = Jsoup.parse(result.body).select("table")
      getInfoSessionLinks(table)
    }))

    infoSessionLinksFuture.map(infoSessionLinks => Ok(infoSessionLinks.map(getInfoSession(_)).toString))
  }

}
