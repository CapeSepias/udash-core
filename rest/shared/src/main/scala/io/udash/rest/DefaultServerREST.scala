package io.udash.rest

import com.avsystem.commons.rpc.RPCMetadata
import io.udash.rest.internal.{RESTConnector, UsesREST}

import scala.concurrent.ExecutionContext

/** Default REST usage mechanism using [[io.udash.rest.DefaultRESTFramework]]. */
class DefaultServerREST[ServerRPCType](override protected val connector: RESTConnector)
                                      (implicit override val remoteRpcAsReal: DefaultRESTFramework.AsRealRPC[ServerRPCType],
                                       override val rpcMetadata: RPCMetadata[ServerRPCType],
                                       validRest: DefaultRESTFramework.ValidREST[ServerRPCType],
                                       ec: ExecutionContext)
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
  /** Creates [[io.udash.rest.DefaultServerREST]] with [[io.udash.rest.DefaultRESTConnector]] for provided REST interfaces. */
  def apply[ServerRPCType](host: String, port: Int, pathPrefix: String = "")
                          (implicit serverRpcAsReal: DefaultRESTFramework.AsRealRPC[ServerRPCType],
                           rpcMetadata: RPCMetadata[ServerRPCType],
                           validRest: DefaultRESTFramework.ValidREST[ServerRPCType],
                           ec: ExecutionContext): ServerRPCType = {

    val serverConnector = new DefaultRESTConnector(host, port, pathPrefix)
    val serverRPC: DefaultServerREST[ServerRPCType] = new DefaultServerREST[ServerRPCType](serverConnector)
    serverRPC.remoteRpc
  }
}