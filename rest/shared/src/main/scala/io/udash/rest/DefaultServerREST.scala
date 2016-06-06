package io.udash.rest

import com.avsystem.commons.rpc.RPCMetadata
import io.udash.rest.internal.RESTConnector.HTTPMethod
import io.udash.rest.internal.{RESTConnector, UsesREST}

import scala.concurrent.Future

class DefaultServerREST[ServerRPCType](override protected val connector: RESTConnector)
                                      (implicit override val remoteRpcAsReal: DefaultRESTFramework.AsRealRPC[ServerRPCType],
                                       override val rpcMetadata: RPCMetadata[ServerRPCType],
                                       validRest: DefaultRESTFramework.ValidREST[ServerRPCType])
  extends UsesREST[ServerRPCType] {

  override val framework = DefaultRESTFramework

  def rawToHeaderArgument(raw: framework.RawValue): String =
    stripQuotes(framework.rawToString(raw))
  def rawToQueryArgument(raw: framework.RawValue): String =
    stripQuotes(framework.rawToString(raw))
  def rawToURLPart(raw: framework.RawValue): String =
    stripQuotes(framework.rawToString(raw))

  private def stripQuotes(s: String): String =
    s.stripPrefix("\"").stripSuffix("\"")
}

object DefaultServerREST {
  /** Creates [[io.udash.rest.DefaultServerREST]] for provided REST interfaces. */
  def apply[ServerRPCType](serverUrl: String = "/api/")
                          (implicit serverRpcAsReal: DefaultRESTFramework.AsRealRPC[ServerRPCType],
                           rpcMetadata: RPCMetadata[ServerRPCType],
                           validRest: DefaultRESTFramework.ValidREST[ServerRPCType]): ServerRPCType = {

    val serverConnector = new RESTConnector {
      override def send(url: String, method: HTTPMethod, queryArguments: Map[String, String], headers: Map[String, String], body: String): Future[String] =
        Future.successful("")
    }
    lazy val serverRPC: DefaultServerREST[ServerRPCType] = new DefaultServerREST[ServerRPCType](serverConnector)
    serverRPC.remoteRpc
  }
}