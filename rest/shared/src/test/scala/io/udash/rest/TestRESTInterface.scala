package io.udash.rest

import scala.concurrent.Future

case class TestRESTRecord(id: Option[Int], s: String)

@REST
trait TestRESTInterface {
  def serviceOne(): TestRESTInternalInterface
  def serviceTwo(token: String @RESTName("X_AUTH_TOKEN") @Header): TestRESTInternalInterface
  @RESTName("service_three") def serviceThree(arg: String @URLPart): TestRESTInternalInterface
}

@REST
trait TestRESTInternalInterface {
  @GET def load(): Future[Seq[TestRESTRecord]]
  @GET def load(id: Int @URLPart, trash: String @Query): Future[TestRESTRecord]
  @POST def create(record: TestRESTRecord @Body): Future[TestRESTRecord]
  @PUT def update(id: Int @URLPart)(record: TestRESTRecord @Body): Future[TestRESTRecord]
  @PATCH @RESTName("change") def modify(id: Int @URLPart)(s: String @Body): Future[TestRESTRecord]
  @DELETE def delete(id: Int @URLPart): Future[TestRESTRecord]
}
