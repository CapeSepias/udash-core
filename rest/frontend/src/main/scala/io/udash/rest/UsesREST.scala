package io.udash.rest

import com.avsystem.commons.rpc.RPCMetadata
import io.udash.rpc.UsesRemoteRPC
import io.udash.rpc.internals.ServerConnector

private[rest] abstract class UsesREST[ServerRPCType](implicit val rpcMetadata: RPCMetadata[ServerRPCType]) extends UsesRemoteRPC[ServerRPCType] {
  import framework._

  /**
    * Proxy for remote RPC implementation. Use this to perform RPC calls.
    */
  lazy val remoteRpc = remoteRpcAsReal.asReal(new RawRemoteRPC(Nil))

  /**
    * This allows for generation of proxy which translates RPC calls into raw calls that
    * can be sent through the network.
    */
  protected def remoteRpcAsReal: AsRealRPC[ServerRPCType]

  protected val connector: ServerConnector[RPCRequest]

  protected[rest] def fireRemote(getterChain: List[RawInvocation], invocation: RawInvocation): Unit =
    sendRPCRequest(RPCFire(invocation, getterChain))

  protected[rest] def callRemote(callId: String, getterChain: List[RawInvocation], invocation: RawInvocation): Unit =
    sendRPCRequest(RPCCall(invocation, getterChain, callId))

  private def sendRPCRequest(request: RPCRequest) =
    connector.sendRPCRequest(request)

  def handleResponse(response: RPCResponse) = {
    response match {
      case RPCResponseSuccess(r, callId) =>
        returnRemoteResult(callId, r)
      case RPCResponseFailure(cause, error, callId) =>
        reportRemoteFailure(callId, cause, error)
    }
  }
}
