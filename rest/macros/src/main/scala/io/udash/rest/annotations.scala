package io.udash.rest

import com.avsystem.commons.rpc.MetadataAnnotation

import scala.annotation.StaticAnnotation

/** Marker trait for REST interfaces. */
trait REST extends StaticAnnotation
/** Forces name of a method or an argument used in the interface to REST mapping. */
class RESTName(val name: String) extends StaticAnnotation

sealed trait RESTMethod
/** Annotated method will be send using `GET` HTTP method. */
trait GET extends MetadataAnnotation with RESTMethod
/** Annotated method will be send using `POST` HTTP method. */
trait POST extends MetadataAnnotation with RESTMethod
/** Annotated method will be send using `PATCH` HTTP method. */
trait PATCH extends MetadataAnnotation with RESTMethod
/** Annotated method will be send using `PUT` HTTP method. */
trait PUT extends MetadataAnnotation with RESTMethod
/** Annotated method will be send using `DELETE` HTTP method. */
trait DELETE extends MetadataAnnotation with RESTMethod

sealed trait ArgumentType
/** Annotated argument will be send as an URL part, eg. /method/{value}. */
trait URLPart extends MetadataAnnotation with ArgumentType
/** Annotated argument will be send as a query argument, eg. /method/?arg=value. */
trait Query extends MetadataAnnotation with ArgumentType
/** Annotated argument will be send as a body part, eg. /method/ {arg: value}. */
trait Body extends MetadataAnnotation with ArgumentType
/** Annotated argument will be send as a header. */
trait Header extends MetadataAnnotation with ArgumentType