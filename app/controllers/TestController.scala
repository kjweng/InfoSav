package controllers

import javax.inject.{Inject, Singleton}

import play.api.mvc._

import scala.concurrent.ExecutionContext

/**
  * Created by kaijunweng on 8/25/16.
  */
@Singleton
class TestController @Inject() (implicit exec: ExecutionContext) extends Controller {
  def foo = Action { Ok("All good") }
}
