package io.udash.rest.internal

import scala.concurrent.Future

trait RESTConnector {
  def send(url: String, method: RESTConnector.HTTPMethod, queryArguments: Map[String, String], headers: Map[String, String], body: String): Future[String]
}

object RESTConnector {
  sealed trait HTTPMethod
  case object GET extends HTTPMethod
  case object POST extends HTTPMethod
  case object PATCH extends HTTPMethod
  case object PUT extends HTTPMethod
  case object DELETE extends HTTPMethod
}