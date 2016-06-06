package io.udash.rest.macros

import com.avsystem.commons.macros.MacroCommons
import com.avsystem.commons.macros.rpc.RPCMacros

import scala.reflect.macros.blackbox

class RESTMacros(override val c: blackbox.Context) extends RPCMacros(c) {

  import c.universe._

  val RestPackage = q"io.udash.rest"

  val RESTFrameworkType = getType(tq"$RestPackage.UdashRESTFramework")
  val ValidRESTCls = tq"$FrameworkObj.ValidREST"

  val RestMethodCls = tq"$RestPackage.RESTMethod"
  val GetCls = tq"$RestPackage.GET"
  val PostCls = tq"$RestPackage.POST"
  val PutCls = tq"$RestPackage.PUT"
  val PatchCls = tq"$RestPackage.PATCH"
  val DeleteCls = tq"$RestPackage.DELETE"

  val ArgumentTypeCls = tq"$RestPackage.ArgumentType"
  val BodyCls = tq"$RestPackage.Body"
  val HeaderCls = tq"$RestPackage.Header"
  val QueryCls = tq"$RestPackage.Query"
  val URLPartCls = tq"$RestPackage.URLPart"

  def hasAnnotation(annotations: List[Annotation], annotation: Type) = annotations.exists(_.tree.tpe <:< annotation)
  def hasRestMethodAnnotation(annotations: List[Annotation]) = hasAnnotation(annotations, getType(RestMethodCls))
  def hasArgumentTypeAnnotation(annotations: List[Annotation]) = hasAnnotation(annotations, getType(ArgumentTypeCls))

  def countAnnotation(annotations: List[Annotation], annotation: Type) = annotations.count(_.tree.tpe <:< annotation)
  def countRestMethodAnnotation(annotations: List[Annotation]) = countAnnotation(annotations, getType(RestMethodCls))
  def countArgumentTypeAnnotation(annotations: List[Annotation]) = countAnnotation(annotations, getType(ArgumentTypeCls))

  def asValidRest[T: c.WeakTypeTag]: c.Tree = {
    val restType = weakTypeOf[T]
    val proxyables: List[ProxyableMember] = proxyableMethods(restType)
    val subinterfaces = proxyables.filter(proxyable => hasRpcAnnot(proxyable.returnType))
    val methods = proxyables.filterNot(proxyable => hasRpcAnnot(proxyable.returnType))

    val subinterfacesImplicits = subinterfaces.map(getter => {
      if (hasRestMethodAnnotation(getter.method.annotations)) {
        abort(s"Subinterface getter cannot be annotated with REST method annotation, ${getter.rpcName} in $restType does.")
      }

      getter.paramLists.foreach(paramsList => {
        paramsList.foreach(param => {
          if (countArgumentTypeAnnotation(param.annotations) != 1) {
            abort(s"REST method argument has to be annotated with exactly one argument type annotation, ${param.name} from ${getter.rpcName} in $restType has not.")
          }

          if (hasAnnotation(param.annotations, getType(BodyCls))) {
            abort(s"Subinterface getter cannot contain arguments annotated with @Body annotation, ${getter.rpcName} in $restType does.")
          }
        })
      })

      q"""implicitly[$ValidRESTCls[${getter.returnType}]]"""
    })

    val methodsImplicits = methods.map(method => {
      var alreadyContainsBodyArgument = false

      if (countRestMethodAnnotation(method.method.annotations) != 1) {
        abort(s"REST method has to be annotated with exactly one REST method annotation, ${method.rpcName} in $restType has not.")
      }

      method.paramLists.foreach(paramsList => {
        paramsList.foreach(param => {
          if (countArgumentTypeAnnotation(param.annotations) != 1) {
            abort(s"REST method argument has to be annotated with exactly one argument type annotation, ${param.name} from ${method.rpcName} in $restType has not.")
          }

          if (hasAnnotation(param.annotations, getType(BodyCls))) {
            if (alreadyContainsBodyArgument) {
              abort(s"REST method cannot contain more than one argument annotated with @Body annotation, ${method.rpcName} in $restType does.")
            }
            if (hasAnnotation(method.method.annotations, getType(GetCls))) {
              abort(s"GET HTTP request cannot contain body argument, ${method.rpcName} in $restType does.")
            }
            alreadyContainsBodyArgument = true
          }
        })
      })
      q"""null"""
    })

    q"""
      new $ValidRESTCls[$restType] {
        val subInterfaces = Seq(..$subinterfacesImplicits)
        val methods = Seq(..$methodsImplicits)
      }
      """
  }
}
