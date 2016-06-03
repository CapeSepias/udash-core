package io.udash.rest

import com.avsystem.commons.rpc.{FunctionRPCFramework, GetterRPCFramework}

trait UdashRESTFramework extends GetterRPCFramework with FunctionRPCFramework {
  trait RawRPC extends GetterRawRPC with FunctionRawRPC

  def stringToRaw(string: String): RawValue
  def rawToString(raw: RawValue): String
}

