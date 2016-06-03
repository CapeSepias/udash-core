package io.udash.rest.internal

import com.avsystem.commons.rpc.RPCMetadata
import io.udash.rest._
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

    var metadata: RPCMetadata[_] = rpcMetadata
    getterChain.foreach(inv => {
      val methodName: String = inv.rpcName
      urlBuilder += methodName

      val methodMetadata = metadata.signatures(methodName)
      val paramsMetadata = methodMetadata.paramMetadata
      paramsMetadata.zip(inv.argLists).foreach(paramsList => {
        paramsList._1.zip(paramsList._2).foreach({case (param, value) =>
          val argTypeAnnotations = param.annotations.filter(_.isInstanceOf[ArgumentType])
          if (argTypeAnnotations.size > 1) throw new RuntimeException("Too many parameter type annotations!")
          argTypeAnnotations.headOption match {
            case Some(_: Header) =>
              headersArgsBuilder.+=((param.name, rawToHeaderArgument(value)))
            case Some(_: Query) =>
              queryArgsBuilder.+=((param.name, rawToQueryArgument(value)))
            case Some(_: URLPart) =>
              urlBuilder += rawToURLPart(value)
            case _ => throw new RuntimeException("Missing parameter type annotations!")
          }
        })
      })

      metadata = metadata.getterResults(methodName)
    })

    urlBuilder += invocation.rpcName
    val methodMetadata = metadata.signatures(invocation.rpcName)
    val paramsMetadata = methodMetadata.paramMetadata
    paramsMetadata.zip(invocation.argLists).foreach(paramsList => {
      paramsList._1.zip(paramsList._2).foreach({case (param, value) =>
        val argTypeAnnotations = param.annotations.filter(_.isInstanceOf[ArgumentType])
        if (argTypeAnnotations.size > 1) throw new RuntimeException("Too many parameter type annotations!")
        argTypeAnnotations.headOption match {
          case Some(_: Header) =>
            headersArgsBuilder += ((param.name, rawToHeaderArgument(value)))
          case Some(_: Query) =>
            queryArgsBuilder += ((param.name, rawToQueryArgument(value)))
          case Some(_: URLPart) =>
            urlBuilder += rawToURLPart(value)
          case Some(_: Body) =>
            body = rawToString(value)
          case _ => throw new RuntimeException(s"Missing `${param.name}` parameter type annotations!")
        }
      })
    })

    val methodAnnotations = methodMetadata.annotations.filter(_.isInstanceOf[RESTMethod])
    if (methodAnnotations.size > 1) throw new RuntimeException("Too many method type annotations!")
    val method: RESTConnector.HTTPMethod = methodAnnotations.headOption match {
      case Some(_: GET) => RESTConnector.GET
      case Some(_: POST) => RESTConnector.POST
      case Some(_: PATCH) => RESTConnector.PATCH
      case Some(_: PUT) => RESTConnector.PUT
      case Some(_: DELETE) => RESTConnector.DELETE
      case _ => throw new RuntimeException("Missing method type annotations!")
    }

    connector.send(s"/${urlBuilder.result().mkString("/")}", method, queryArgsBuilder.result(), headersArgsBuilder.result(), body)
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
