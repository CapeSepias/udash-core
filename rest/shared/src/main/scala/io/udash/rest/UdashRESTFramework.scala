package io.udash.rest

import com.avsystem.commons.rpc.{FunctionRPCFramework, GetterRPCFramework}

trait UdashRESTFramework extends GetterRPCFramework with FunctionRPCFramework {
  trait RawRPC extends GetterRawRPC with FunctionRawRPC

  def stringToRaw(string: String): RawValue
  def rawToString(raw: RawValue): String

  trait ValidREST[T]

  object ValidREST {
    def apply[T](implicit validREST: ValidREST[T]): ValidREST[T] = validREST
  }

  implicit def materializeValidREST[T]: ValidREST[T] = macro macros.RESTMacros.asValidRest[T]
}

