package io.udash.rest

import com.avsystem.commons.rpc.RPCMetadata
import io.udash.rpc.UsesRemoteRPC
import io.udash.rpc.internals.ServerConnector

import scala.util.Success

private[rest] abstract class UsesREST[ServerRPCType](implicit val rpcMetadata: RPCMetadata[ServerRPCType]) extends UsesRemoteREST[ServerRPCType] {
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

  protected val connector: RESTConnector

  protected[rest] def fireRemote(getterChain: List[RawInvocation], invocation: RawInvocation): Unit = {
    val urlBuilder = Seq.newBuilder[String]
    val queryArgsBuilder = Map.newBuilder[String, String]
    val headersArgsBuilder = Map.newBuilder[String, String]
    var body: String = ???
    var method: RESTConnector.HTTPMethod = ???

    var metadata: RPCMetadata[_] = rpcMetadata
    getterChain.foreach(inv => {
      val methodName: String = inv.rpcName
      urlBuilder += methodName

      val methodMetadata = metadata.methodsByRpcName(methodName)
      val paramsMetadata = methodMetadata.signature.paramMetadata
      paramsMetadata.zip(inv.argLists).foreach(paramsList => {
        paramsList._1.zip(paramsList._2).foreach({case (param, value) =>
          val argTypeAnnotations = param.annotations.filter(_.isInstanceOf[ArgumentType])
          if (argTypeAnnotations.size > 1) throw new RuntimeException("Too many parameter type annotations!")
          argTypeAnnotations.headOption match {
            case Some(_: Header) =>
              headersArgsBuilder.+=((param.name, value))
            case Some(_: Query) =>
              queryArgsBuilder.+=((param.name, value))
            case _ =>
              urlBuilder += value
          }
        })
      })

      //TODO invocation

      metadata = metadata.getterResultMetadata(methodName)
    })



    connector.send(s"/${urlBuilder.result().mkString("/")}", method, queryArgsBuilder.result(), headersArgsBuilder.result(), body)
  }

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
