package io.udash.rest

import io.udash.rpc.UsesRemoteRPC

import scala.collection.mutable
import scala.concurrent.{Future, Promise}

/**
 * Base trait for anything that uses remote REST interface.
 */
trait UsesRemoteREST[T] extends UsesRemoteRPC[T] {
  val framework: UdashRESTFramework
}
