package io.udash.rest

import scala.concurrent.Future


trait RESTConnector {
  def sendAndForget(url: String, method: RESTConnector.HTTPMethod, queryArguments: Map[String, String], headers: Map[String, String], body: String): Unit
  def send(url: String, method: RESTConnector.HTTPMethod, queryArguments: Map[String, String], headers: Map[String, String], body: String): Future[String]
}

object RESTConnector {
  sealed trait HTTPMethod
  case object GET extends HTTPMethod
}
