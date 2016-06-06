package io.udash.rest

import io.udash.rest.internal.RESTConnector
import io.udash.rest.internal.RESTConnector.HTTPMethod
import io.udash.testing.UdashSharedTest

import scala.concurrent.Future

class DefaultServerRESTTest extends UdashSharedTest {
  class ConnectorMock extends RESTConnector {
    var url: String = null
    var method: HTTPMethod = null
    var queryArguments: Map[String, String] = null
    var headers: Map[String, String] = null
    var body: String = null

    var response = "{}"

    override def send(url: String, method: HTTPMethod, queryArguments: Map[String, String], headers: Map[String, String], body: String): Future[String] = {
      println(s"$url $method $queryArguments $headers $body")
      this.url = url
      this.method = method
      this.queryArguments = queryArguments
      this.headers = headers
      this.body = body
      Future.successful(response)
    }
  }

  "DefaultServerREST" should {
    val r = TestRESTRecord(None, "Bla bla")
    val r2 = TestRESTRecord(Some(2), "Bla bla 2")
    val r3 = TestRESTRecord(Some(3), "Bla bla 3")

    val connector = new ConnectorMock
    val rest: DefaultServerREST[TestRESTInterface] = new DefaultServerREST[TestRESTInterface](connector)
    val restServer = rest.remoteRpc

    "send valid REST requests via RESTConnector" in {
      restServer.serviceOne().create(r)
      connector.url should be("/serviceOne/create")
      connector.method should be(RESTConnector.POST)
      connector.queryArguments should be(Map.empty)
      connector.headers should be(Map.empty)
      rest.framework.read[TestRESTRecord](connector.body) should be(r)

      restServer.serviceOne().update(r2.id.get)(r2)
      connector.url should be(s"/serviceOne/update/${r2.id.get}")
      connector.method should be(RESTConnector.PUT)
      connector.queryArguments should be(Map.empty)
      connector.headers should be(Map.empty)
      rest.framework.read[TestRESTRecord](connector.body) should be(r2)

      restServer.serviceOne().modify(r2.id.get)("test")
      connector.url should be(s"/serviceOne/change/${r2.id.get}")
      connector.method should be(RESTConnector.PATCH)
      connector.queryArguments should be(Map.empty)
      connector.headers should be(Map.empty)
      rest.framework.read[String](connector.body) should be("test")

      restServer.serviceOne().delete(r2.id.get)
      connector.url should be(s"/serviceOne/remove/${r2.id.get}")
      connector.method should be(RESTConnector.DELETE)
      connector.queryArguments should be(Map.empty)
      connector.headers should be(Map.empty)
      connector.body should be(null)
    }

    "handle overloaded methods" in {
      restServer.serviceOne().load()
      connector.url should be(s"/serviceOne/load")
      connector.method should be(RESTConnector.GET)
      connector.queryArguments should be(Map.empty)
      connector.headers should be(Map.empty)
      connector.body should be(null)
    }

    "handle query arguments" in {
      restServer.serviceOne().load(r3.id.get, "trashValue", "thrashValue 123")
      connector.url should be(s"/serviceOne/load/${r3.id.get}")
      connector.method should be(RESTConnector.GET)
      connector.queryArguments("trash") should be("trashValue")
      connector.queryArguments("trash_two") should be("thrashValue 123")
      connector.headers should be(Map.empty)
      connector.body should be(null)
    }

    "handle header arguments" in {
      restServer.serviceTwo("token_123", "pl").create(r)
      connector.url should be("/serviceTwo/create")
      connector.method should be(RESTConnector.POST)
      connector.queryArguments should be(Map.empty)
      connector.headers("X_AUTH_TOKEN") should be("token_123")
      connector.headers("lang") should be("pl")
      rest.framework.read[TestRESTRecord](connector.body) should be(r)
    }

    "handle overrided method name" in {
      restServer.serviceThree("abc").create(r)
      connector.url should be("/service_three/abc/create")
      connector.method should be(RESTConnector.POST)
      connector.queryArguments should be(Map.empty)
      connector.headers should be(Map.empty)
      rest.framework.read[TestRESTRecord](connector.body) should be(r)
    }
  }
}
