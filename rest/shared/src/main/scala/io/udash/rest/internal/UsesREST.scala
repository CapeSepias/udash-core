package io.udash.rest.internal

import com.avsystem.commons.rpc.{MetadataAnnotation, RPCMetadata}
import io.udash.rest._
import io.udash.rest.internal.RESTConnector.{HTTPMethod, POST}
import io.udash.rpc.UsesRemoteRPC

import scala.collection.mutable
import scala.concurrent.{Future, Promise}

private[rest] abstract class UsesREST[ServerRPCType](implicit val rpcMetadata: RPCMetadata[ServerRPCType]) {
  val framework: UdashRESTFramework

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

  def rawToHeaderArgument(raw: RawValue): String
  def rawToQueryArgument(raw: RawValue): String
  def rawToURLPart(raw: RawValue): String

  private val pendingCalls = mutable.Map.empty[String, Promise[RawValue]]
  private var cid: Int = 0

  private def newCallId() = {
    cid += 1
    cid.toString
  }

  protected[rest] def callRemote(callId: String, getterChain: List[RawInvocation], invocation: RawInvocation): Unit = {
    val urlBuilder = Seq.newBuilder[String]
    val queryArgsBuilder = Map.newBuilder[String, String]
    val headersArgsBuilder = Map.newBuilder[String, String]
    var body: String = null

    def findRestName(annotations: Seq[MetadataAnnotation]): Option[String] =
      annotations.find(_.isInstanceOf[RESTName]).map(_.asInstanceOf[RESTName].restName)

    def parseInvocation(inv: framework.RawInvocation, metadata: RPCMetadata[_]): Unit = {
      val rpcMethodName: String = inv.rpcName
      val methodMetadata = metadata.signatures(rpcMethodName)

      urlBuilder += findRestName(methodMetadata.annotations).getOrElse(rpcMethodName)
      methodMetadata.paramMetadata.zip(inv.argLists).foreach { case (params, values) =>
        params.zip(values).foreach { case (param, value) =>
          val paramName: String = findRestName(param.annotations).getOrElse(param.name)
          val argTypeAnnotations = param.annotations.filter(_.isInstanceOf[ArgumentType])
          if (argTypeAnnotations.size > 1) throw new RuntimeException(s"Too many parameter type annotations! ($argTypeAnnotations)")
          argTypeAnnotations.headOption match {
            case Some(_: Header) =>
              headersArgsBuilder.+=((paramName, rawToHeaderArgument(value)))
            case Some(_: Query) =>
              queryArgsBuilder.+=((paramName, rawToQueryArgument(value)))
            case Some(_: URLPart) =>
              urlBuilder += rawToURLPart(value)
            case Some(_: Body) =>
              body = rawToString(value)
            case _ => throw new RuntimeException(s"Missing `${param.name}` parameter type annotations! ($argTypeAnnotations)")
          }
        }
      }
    }

    def findRestMethod(inv: framework.RawInvocation, metadata: RPCMetadata[_]): RESTConnector.HTTPMethod = {
      val rpcMethodName: String = inv.rpcName
      val methodMetadata = metadata.signatures(rpcMethodName)
      val methodAnnotations = methodMetadata.annotations.filter(_.isInstanceOf[RESTMethod])
      if (methodAnnotations.size > 1) throw new RuntimeException(s"Too many method type annotations! ($methodAnnotations)")
      methodAnnotations.headOption match {
        case Some(_: GET) => RESTConnector.GET
        case Some(_: POST) => RESTConnector.POST
        case Some(_: PATCH) => RESTConnector.PATCH
        case Some(_: PUT) => RESTConnector.PUT
        case Some(_: DELETE) => RESTConnector.DELETE
        case _ => throw new RuntimeException(s"Missing method type annotations! ($methodAnnotations)")
      }
    }

    var metadata: RPCMetadata[_] = rpcMetadata
    getterChain.foreach(inv => {
      parseInvocation(inv, metadata)
      metadata = metadata.getterResults(inv.rpcName)
    })
    parseInvocation(invocation, metadata)

    val restMethod: HTTPMethod = findRestMethod(invocation, metadata)

    connector.send(s"/${urlBuilder.result().mkString("/")}", restMethod, queryArgsBuilder.result(), headersArgsBuilder.result(), body)
  }

  protected class RawRemoteRPC(getterChain: List[RawInvocation]) extends RawRPC {
    def call(rpcName: String, argLists: List[List[RawValue]]): Future[RawValue] = {
      val callId = newCallId()
      val promise = Promise[RawValue]()
      pendingCalls.put(callId, promise)
      callRemote(callId, getterChain, RawInvocation(rpcName, argLists))
      promise.future
    }

    def get(rpcName: String, argLists: List[List[RawValue]]): RawRPC =
      new RawRemoteRPC(RawInvocation(rpcName, argLists) :: getterChain)
  }
}
