package io.udash.rest

import com.avsystem.commons.serialization.GenCodec
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

    "compile recursive interface" in {
      """import io.udash.rpc.RPCName
         |case class TestRESTRecord(id: Option[Int], s: String)
         |implicit val x: GenCodec[TestRESTRecord] = null
         |
         |@REST
         |trait NotBrokenRESTInterface {
         |  def serviceOne(): NotBrokenRESTInterface
         |  def serviceTwo(@RESTName("X_AUTH_TOKEN") @Header token: String, @Header lang: String): NotBrokenRESTInterface
         |  @RESTName("service_three") def serviceThree(@URLPart arg: String): NotBrokenRESTInterface
         |  @GET @RESTName("load") @RPCName("loadAll") def load(): Future[Seq[TestRESTRecord]]
         |}
         |
         |val rest: DefaultServerREST[NotBrokenRESTInterface] = new DefaultServerREST[NotBrokenRESTInterface](connector)
       """.stripMargin should compile

      """import io.udash.rpc.RPCName
         |case class TestRESTRecord(id: Option[Int], s: String)
         |implicit val x: GenCodec[TestRESTRecord] = null
         |
         |@REST
         |trait NotBrokenRESTInterface {
         |  def serviceOne(): NotBrokenRESTInternalInterface
         |  def serviceTwo(@RESTName("X_AUTH_TOKEN") @Header token: String, @Header lang: String): NotBrokenRESTInternalInterface
         |  @RESTName("service_three") def serviceThree(@URLPart arg: String): NotBrokenRESTInternalInterface
         |}
         |
         |@REST
         |trait NotBrokenRESTInternalInterface {
         |  @RESTName("load") @RPCName("loadAll") def load(): NotBrokenRESTInterface
         |  @GET def load(@URLPart id: Int, @Query trash: String, @Query @RESTName("trash_two") trash2: String): Future[TestRESTRecord]
         |  @POST def create(@Body record: TestRESTRecord): Future[TestRESTRecord]
         |  @PUT def update(@URLPart id: Int)(@Body record: TestRESTRecord): Future[TestRESTRecord]
         |  @PATCH @RESTName("change") def modify(@URLPart id: Int)(@Body s: String): Future[TestRESTRecord]
         |  @DELETE @RPCName("remove") def delete(@URLPart id: Int): Future[TestRESTRecord]
         |}
         |
         |val rest: DefaultServerREST[NotBrokenRESTInterface] = new DefaultServerREST[NotBrokenRESTInterface](connector)
       """.stripMargin should compile
    }

    "not compile with interface without @REST annotation" in {
      """import io.udash.rpc.RPCName
         |case class TestRESTRecord(id: Option[Int], s: String)
         |implicit val x: GenCodec[TestRESTRecord] = null
         |
         |trait BrokenRESTInterface {
         |  def serviceOne(): BrokenRESTInternalInterface
         |  def serviceTwo(@RESTName("X_AUTH_TOKEN") @Header token: String, @Header lang: String): BrokenRESTInternalInterface
         |  @RESTName("service_three") def serviceThree(@URLPart arg: String): BrokenRESTInternalInterface
         |}
         |
         |@REST
         |trait BrokenRESTInternalInterface {
         |  @GET @RESTName("load") @RPCName("loadAll") def load(): Future[Seq[TestRESTRecord]]
         |  @GET def load(@URLPart id: Int, @Query trash: String, @Query @RESTName("trash_two") trash2: String): Future[TestRESTRecord]
         |  @POST def create(@Body record: TestRESTRecord): Future[TestRESTRecord]
         |  @PUT def update(@URLPart id: Int)(@Body record: TestRESTRecord): Future[TestRESTRecord]
         |  @PATCH @RESTName("change") def modify(@URLPart id: Int)(@Body s: String): Future[TestRESTRecord]
         |  @DELETE @RPCName("remove") def delete(@URLPart id: Int): Future[TestRESTRecord]
         |}
         |
         |val rest: DefaultServerREST[BrokenRESTInterface] = new DefaultServerREST[BrokenRESTInterface](connector)
       """.stripMargin shouldNot compile
    }

    "not compile with internal interface without @REST annotation" in {
      """import io.udash.rpc.RPCName
         |case class TestRESTRecord(id: Option[Int], s: String)
         |implicit val x: GenCodec[TestRESTRecord] = null
         |
         |@REST
         |trait BrokenRESTInterface {
         |  def serviceOne(): BrokenRESTInternalInterface
         |  def serviceTwo(@RESTName("X_AUTH_TOKEN") @Header token: String, @Header lang: String): BrokenRESTInternalInterface
         |  @RESTName("service_three") def serviceThree(@URLPart arg: String): BrokenRESTInternalInterface
         |}
         |
         |trait BrokenRESTInternalInterface {
         |  @GET @RESTName("load") @RPCName("loadAll") def load(): Future[Seq[TestRESTRecord]]
         |  @GET def load(@URLPart id: Int, @Query trash: String, @Query @RESTName("trash_two") trash2: String): Future[TestRESTRecord]
         |  @POST def create(@Body record: TestRESTRecord): Future[TestRESTRecord]
         |  @PUT def update(@URLPart id: Int)(@Body record: TestRESTRecord): Future[TestRESTRecord]
         |  @PATCH @RESTName("change") def modify(@URLPart id: Int)(@Body s: String): Future[TestRESTRecord]
         |  @DELETE @RPCName("remove") def delete(@URLPart id: Int): Future[TestRESTRecord]
         |}
         |
         |val rest: DefaultServerREST[BrokenRESTInterface] = new DefaultServerREST[BrokenRESTInterface](connector)
       """.stripMargin shouldNot compile
    }

    "not compile with @Body argument in getter" in {
      """import io.udash.rpc.RPCName
         |implicit val x: GenCodec[TestRESTRecord] = null
         |case class TestRESTRecord(id: Option[Int], s: String)
         |
         |@REST
         |trait BrokenRESTInterface {
         |  def serviceOne(@Body bodyArg: String): BrokenRESTInternalInterface
         |}
         |
         |@REST
         |trait BrokenRESTInternalInterface {
         |  @PUT def update(@URLPart id: Int)(@Body record: TestRESTRecord): Future[TestRESTRecord]
         |}
         |
         |val rest: DefaultServerREST[BrokenRESTInterface] = new DefaultServerREST[BrokenRESTInterface](connector)
       """.stripMargin shouldNot typeCheck
    }

    "not compile with REST method annotation on getter" in {
      """import io.udash.rpc.RPCName
         |implicit val x: GenCodec[TestRESTRecord] = null
         |case class TestRESTRecord(id: Option[Int], s: String)
         |
         |@REST
         |trait BrokenRESTInterface {
         |  @GET def serviceOne(): BrokenRESTInternalInterface
         |}
         |
         |@REST
         |trait BrokenRESTInternalInterface {
         |  @PUT def update(@URLPart id: Int)(@Body record: TestRESTRecord): Future[TestRESTRecord]
         |}
         |
         |val rest: DefaultServerREST[BrokenRESTInterface] = new DefaultServerREST[BrokenRESTInterface](connector)
       """.stripMargin shouldNot typeCheck

      """import io.udash.rpc.RPCName
         |implicit val x: GenCodec[TestRESTRecord] = null
         |case class TestRESTRecord(id: Option[Int], s: String)
         |
         |@REST
         |trait BrokenRESTInterface {
         |  @POST def serviceOne(): BrokenRESTInternalInterface
         |}
         |
         |@REST
         |trait BrokenRESTInternalInterface {
         |  @PUT def update(@URLPart id: Int)(@Body record: TestRESTRecord): Future[TestRESTRecord]
         |}
         |
         |val rest: DefaultServerREST[BrokenRESTInterface] = new DefaultServerREST[BrokenRESTInterface](connector)
       """.stripMargin shouldNot typeCheck

      """import io.udash.rpc.RPCName
         |implicit val x: GenCodec[TestRESTRecord] = null
         |case class TestRESTRecord(id: Option[Int], s: String)
         |
         |@REST
         |trait BrokenRESTInterface {
         |  @PUT def serviceOne(): BrokenRESTInternalInterface
         |}
         |
         |@REST
         |trait BrokenRESTInternalInterface {
         |  @PUT def update(@URLPart id: Int)(@Body record: TestRESTRecord): Future[TestRESTRecord]
         |}
         |
         |val rest: DefaultServerREST[BrokenRESTInterface] = new DefaultServerREST[BrokenRESTInterface](connector)
       """.stripMargin shouldNot typeCheck

      """import io.udash.rpc.RPCName
         |implicit val x: GenCodec[TestRESTRecord] = null
         |case class TestRESTRecord(id: Option[Int], s: String)
         |
         |@REST
         |trait BrokenRESTInterface {
         |  @PATCH def serviceOne(): BrokenRESTInternalInterface
         |}
         |
         |@REST
         |trait BrokenRESTInternalInterface {
         |  @PUT def update(@URLPart id: Int)(@Body record: TestRESTRecord): Future[TestRESTRecord]
         |}
         |
         |val rest: DefaultServerREST[BrokenRESTInterface] = new DefaultServerREST[BrokenRESTInterface](connector)
       """.stripMargin shouldNot typeCheck

      """import io.udash.rpc.RPCName
         |implicit val x: GenCodec[TestRESTRecord] = null
         |case class TestRESTRecord(id: Option[Int], s: String)
         |
         |@REST
         |trait BrokenRESTInterface {
         |  @DELETE def serviceOne(): BrokenRESTInternalInterface
         |}
         |
         |@REST
         |trait BrokenRESTInternalInterface {
         |  @PUT def update(@URLPart id: Int)(@Body record: TestRESTRecord): Future[TestRESTRecord]
         |}
         |
         |val rest: DefaultServerREST[BrokenRESTInterface] = new DefaultServerREST[BrokenRESTInterface](connector)
       """.stripMargin shouldNot typeCheck
    }

    "not compile with more than one @Body argument" in {
      """import io.udash.rpc.RPCName
         |implicit val x: GenCodec[TestRESTRecord] = null
         |case class TestRESTRecord(id: Option[Int], s: String)
         |
         |@REST
         |trait BrokenRESTInterface {
         |  def serviceOne(): BrokenRESTInternalInterface
         |  def serviceTwo(@RESTName("X_AUTH_TOKEN") @Header token: String, @Header lang: String): BrokenRESTInternalInterface
         |  @RESTName("service_three") def serviceThree(@URLPart arg: String): BrokenRESTInternalInterface
         |}
         |
         |@REST
         |trait BrokenRESTInternalInterface {
         |  @GET @RESTName("load") @RPCName("loadAll") def load(): Future[Seq[TestRESTRecord]]
         |  @GET def load(@URLPart id: Int, @Query trash: String, @Query @RESTName("trash_two") trash2: String): Future[TestRESTRecord]
         |  @POST def create(@Body record: TestRESTRecord, @Body record2: TestRESTRecord): Future[TestRESTRecord]
         |  @PUT def update(@URLPart id: Int)(@Body record: TestRESTRecord): Future[TestRESTRecord]
         |  @PATCH @RESTName("change") def modify(@URLPart id: Int)(@Body s: String): Future[TestRESTRecord]
         |  @DELETE @RPCName("remove") def delete(@URLPart id: Int): Future[TestRESTRecord]
         |}
         |
         |val rest: DefaultServerREST[BrokenRESTInterface] = new DefaultServerREST[BrokenRESTInterface](connector)
       """.stripMargin shouldNot typeCheck
    }

    "not compile with @Body argument in @GET annotated method" in {
      """import io.udash.rpc.RPCName
         |implicit val x: GenCodec[TestRESTRecord] = null
         |case class TestRESTRecord(id: Option[Int], s: String)
         |
         |@REST
         |trait BrokenRESTInterface {
         |  def serviceOne(): BrokenRESTInternalInterface
         |}
         |
         |@REST
         |trait BrokenRESTInternalInterface {
         |  @GET def load(@URLPart id: Int, @Query trash: String, @Body @RESTName("trash_two") trash2: String): Future[TestRESTRecord]
         |}
         |
         |val rest: DefaultServerREST[BrokenRESTInterface] = new DefaultServerREST[BrokenRESTInterface](connector)
       """.stripMargin shouldNot typeCheck
    }

    "not compile with more than one REST method annotation" in {
      """import io.udash.rpc.RPCName
         |implicit val x: GenCodec[TestRESTRecord] = null
         |case class TestRESTRecord(id: Option[Int], s: String)
         |
         |@REST
         |trait BrokenRESTInterface {
         |  def serviceOne(): BrokenRESTInternalInterface
         |}
         |
         |@REST
         |trait BrokenRESTInternalInterface {
         |  @POST @PUT def update(@URLPart id: Int)(@Body record: TestRESTRecord): Future[TestRESTRecord]
         |}
         |
         |val rest: DefaultServerREST[BrokenRESTInterface] = new DefaultServerREST[BrokenRESTInterface](connector)
       """.stripMargin shouldNot typeCheck
    }

    "not compile without REST method annotation" in {
      """import io.udash.rpc.RPCName
         |implicit val x: GenCodec[TestRESTRecord] = null
         |case class TestRESTRecord(id: Option[Int], s: String)
         |
         |@REST
         |trait BrokenRESTInterface {
         |  def serviceOne(): BrokenRESTInternalInterface
         |}
         |
         |@REST
         |trait BrokenRESTInternalInterface {
         |  def update(@URLPart id: Int)(@Body record: TestRESTRecord): Future[TestRESTRecord]
         |}
         |
         |val rest: DefaultServerREST[BrokenRESTInterface] = new DefaultServerREST[BrokenRESTInterface](connector)
       """.stripMargin shouldNot typeCheck
    }

    "not compile with more than one argument type annotation" in {
      """import io.udash.rpc.RPCName
         |implicit val x: GenCodec[TestRESTRecord] = null
         |case class TestRESTRecord(id: Option[Int], s: String)
         |
         |@REST
         |trait BrokenRESTInterface {
         |  def serviceTwo(@RESTName("X_AUTH_TOKEN") @Header token: String, @Query @Header lang: String): BrokenRESTInternalInterface
         |}
         |
         |@REST
         |trait BrokenRESTInternalInterface {
         |  @GET def load(@URLPart id: Int, @Query trash: String, @Query @RESTName("trash_two") trash2: String): Future[TestRESTRecord]
         |}
         |
         |val rest: DefaultServerREST[BrokenRESTInterface] = new DefaultServerREST[BrokenRESTInterface](connector)
       """.stripMargin shouldNot typeCheck
    }

    "not compile without argument type annotation" in {
      """import io.udash.rpc.RPCName
         |implicit val x: GenCodec[TestRESTRecord] = null
         |case class TestRESTRecord(id: Option[Int], s: String)
         |
         |@REST
         |trait BrokenRESTInterface {
         |  def serviceTwo(@RESTName("X_AUTH_TOKEN") @Header token: String, lang: String): BrokenRESTInternalInterface
         |}
         |
         |@REST
         |trait BrokenRESTInternalInterface {
         |  @GET def load(@URLPart id: Int, @Query trash: String, @Query @RESTName("trash_two") trash2: String): Future[TestRESTRecord]
         |}
         |
         |val rest: DefaultServerREST[BrokenRESTInterface] = new DefaultServerREST[BrokenRESTInterface](connector)
       """.stripMargin shouldNot typeCheck
    }

    "not compile with result type other than REST interface or Future[T]" in {
      """import io.udash.rpc.RPCName
         |implicit val x: GenCodec[TestRESTRecord] = null
         |case class TestRESTRecord(id: Option[Int], s: String)
         |
         |@REST
         |trait BrokenRESTInterface {
         |  def serviceOne(): BrokenRESTInternalInterface
         |  def serviceTwo(@RESTName("X_AUTH_TOKEN") @Header token: String, @Header lang: String): Unit
         |  @RESTName("service_three") def serviceThree(@URLPart arg: String): BrokenRESTInternalInterface
         |}
         |
         |@REST
         |trait BrokenRESTInternalInterface {
         |  @GET def load(@URLPart id: Int, @Query trash: String, @Query @RESTName("trash_two") trash2: String): Future[TestRESTRecord]
         |}
         |
         |val rest: DefaultServerREST[BrokenRESTInterface] = new DefaultServerREST[BrokenRESTInterface](connector)
       """.stripMargin shouldNot typeCheck

      """import io.udash.rpc.RPCName
         |implicit val x: GenCodec[TestRESTRecord] = null
         |case class TestRESTRecord(id: Option[Int], s: String)
         |
         |@REST
         |trait BrokenRESTInterface {
         |  def serviceOne(): BrokenRESTInternalInterface
         |  def serviceTwo(@RESTName("X_AUTH_TOKEN") @Header token: String, @Header lang: String): BrokenRESTInternalInterface
         |  @RESTName("service_three") def serviceThree(@URLPart arg: String): BrokenRESTInternalInterface
         |}
         |
         |@REST
         |trait BrokenRESTInternalInterface {
         |  @GET @RESTName("load") @RPCName("loadAll") def load(): Seq[TestRESTRecord]
         |  @GET def load(@URLPart id: Int, @Query trash: String, @Query @RESTName("trash_two") trash2: String): Future[TestRESTRecord]
         |}
         |
         |val rest: DefaultServerREST[BrokenRESTInterface] = new DefaultServerREST[BrokenRESTInterface](connector)
       """.stripMargin shouldNot typeCheck

      """import io.udash.rpc.RPCName
         |implicit val x: GenCodec[TestRESTRecord] = null
         |case class TestRESTRecord(id: Option[Int], s: String)
         |
         |@REST
         |trait BrokenRESTInterface {
         |  def serviceOne(): BrokenRESTInternalInterface
         |  def serviceTwo(@RESTName("X_AUTH_TOKEN") @Header token: String, @Header lang: String): BrokenRESTInternalInterface
         |  @RESTName("service_three") def serviceThree(@URLPart arg: String): BrokenRESTInternalInterface
         |  @GET @RESTName("load") @RPCName("loadAll") def load(): Future[Seq[TestRESTRecord]]
         |}
         |
         |@REST
         |trait BrokenRESTInternalInterface {
         |  @GET @RESTName("load") @RPCName("loadAll") def load(): Unit
         |  @GET def load(@URLPart id: Int, @Query trash: String, @Query @RESTName("trash_two") trash2: String): Future[TestRESTRecord]
         |  @POST def create(@Body record: TestRESTRecord): Future[TestRESTRecord]
         |  @PUT def update(@URLPart id: Int)(@Body record: TestRESTRecord): Future[TestRESTRecord]
         |  @PATCH @RESTName("change") def modify(@URLPart id: Int)(@Body s: String): Future[TestRESTRecord]
         |  @DELETE @RPCName("remove") def delete(@URLPart id: Int): Future[TestRESTRecord]
         |}
         |
         |val rest: DefaultServerREST[BrokenRESTInterface] = new DefaultServerREST[BrokenRESTInterface](connector)
       """.stripMargin shouldNot typeCheck

      """import io.udash.rpc.RPCName
         |implicit val x: GenCodec[TestRESTRecord] = null
         |case class TestRESTRecord(id: Option[Int], s: String)
         |
         |@REST
         |trait BrokenRESTInterface {
         |  def serviceOne(): BrokenRESTInternalInterface
         |  def serviceTwo(@RESTName("X_AUTH_TOKEN") @Header token: String, @Header lang: String): BrokenRESTInternalInterface
         |  @RESTName("service_three") def serviceThree(@URLPart arg: String): Option[BrokenRESTInternalInterface]
         |}
         |
         |@REST
         |trait BrokenRESTInternalInterface {
         |  @GET @RESTName("load") @RPCName("loadAll") def load(): Future[Seq[TestRESTRecord]]
         |  @DELETE @RPCName("remove") def delete(@URLPart id: Int): Future[TestRESTRecord]
         |}
         |
         |val rest: DefaultServerREST[BrokenRESTInterface] = new DefaultServerREST[BrokenRESTInterface](connector)
       """.stripMargin shouldNot typeCheck
    }

    "not compile with empty @RESTName or @RPCName" in {
      """import io.udash.rpc.RPCName
         |implicit val x: GenCodec[TestRESTRecord] = null
         |case class TestRESTRecord(id: Option[Int], s: String)
         |
         |@REST
         |trait BrokenRESTInterface {
         |  def serviceTwo(@RESTName("") @Header token: String, @Header lang: String): BrokenRESTInternalInterface
         |}
         |
         |@REST
         |trait BrokenRESTInternalInterface {
         |  @GET @RESTName("load") @RPCName("loadAll") def load(): Future[Seq[TestRESTRecord]]
         |}
         |
         |val rest: DefaultServerREST[BrokenRESTInterface] = new DefaultServerREST[BrokenRESTInterface](connector)
       """.stripMargin shouldNot typeCheck

      """import io.udash.rpc.RPCName
         |implicit val x: GenCodec[TestRESTRecord] = null
         |case class TestRESTRecord(id: Option[Int], s: String)
         |
         |@REST
         |trait BrokenRESTInterface {
         |  def serviceTwo(@RESTName("token_x") @Header token: String, @Header lang: String): BrokenRESTInternalInterface
         |}
         |
         |@REST
         |trait BrokenRESTInternalInterface {
         |  @GET @RESTName("load") @RPCName("") def load(): Future[Seq[TestRESTRecord]]
         |}
         |
         |val rest: DefaultServerREST[BrokenRESTInterface] = new DefaultServerREST[BrokenRESTInterface](connector)
       """.stripMargin shouldNot typeCheck
    }
  }
}