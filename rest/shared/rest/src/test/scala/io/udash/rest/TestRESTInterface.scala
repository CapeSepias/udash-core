package io.udash.rest

import scala.concurrent.Future

case class TestRESTRecord(id: Option[Int], s: String)

@REST
trait TestRESTInterface {
  def serviceOne(): TestRESTInternalInterface
  def serviceTwo(@RESTName("X_AUTH_TOKEN") @Header token: String): TestRESTInternalInterface
  @RESTName("service_three") def serviceThree(@URLPart arg: String): TestRESTInternalInterface
}

@REST
trait TestRESTInternalInterface {
  // TODO remove REST name
  @GET @RESTName("loadAll") def load(): Future[Seq[TestRESTRecord]]
  @GET def load(@URLPart id: Int, @Query trash: String): Future[TestRESTRecord]
  @POST def create(@Body record: TestRESTRecord): Future[TestRESTRecord]
  @PUT def update(@URLPart id: Int)(@Body record: TestRESTRecord): Future[TestRESTRecord]
  @PATCH @RESTName("change") def modify(@URLPart id: Int)(@Body s: String): Future[TestRESTRecord]
  @DELETE def delete(@URLPart id: Int): Future[TestRESTRecord]
}
