package oncue.svc.funnel
package zeromq

import argonaut.EncodeJson
import scalaz.stream.async.mutable.Signal
import scalaz.stream.Process
import scalaz.concurrent.Task
import scalaz.{-\/,\/-}
import java.net.URI

object Publish {
  import http.JSON._
  import ZeroMQ.{link,log,write}
  import scalaz.stream.async.signalOf

  private[zeromq] val UTF8 = java.nio.charset.Charset.forName("UTF-8")
  private[zeromq] val alive: Signal[Boolean] = signalOf[Boolean](true)
  val defaultUnixSocket = "/var/run/funnel.socket"

  /////////////////////////////// USAGE ///////////////////////////////////

  // unsafe!
  def to(endpoint: Endpoint)(signal: Signal[Boolean], instance: Monitoring): Unit =
    link(endpoint)(signal)(socket =>
      fromMonitoring(instance)(m => log.debug(m))
        .through(write(socket))
        .onComplete(Process.eval(stop(signal)))
    ).run.runAsync(_ match {
      case -\/(err) =>
        log.error(s"Unable to stream monitoring events to the socket ${endpoint.location.uri}")
        log.error(s"Error was: $err")

      case \/-(win) =>
        log.info(s"Streaming monitoring datapoints to the socket at ${endpoint.location.uri}")
    })

  import sockets._

  def toUnixSocket(
    path: String = defaultUnixSocket,
    signal: Signal[Boolean] = alive,
    instance: Monitoring = Monitoring.default
  ): Unit =
    if(Ø.isEnabled){
      Endpoint(push &&& connect, new URI(s"ipc://$path")) match {
        case \/-(e) => to(e)(signal, instance)
        case -\/(f) => sys.error(s"Unable to create endpoint; the specified URI is likley malformed: $f")
      }
    } else Ø.log.warn("ZeroMQ binaries not installed. No Funnel telemetry will be published.")

  /////////////////////////////// INTERNALS ///////////////////////////////////

  // TODO: implement binary serialisation here rather than using the JSON from `http` module
  private def dataEncode[A](a: A)(implicit A: EncodeJson[A]): String =
    A(a).nospaces

  private def datapointToWireFormat(d: Datapoint[Any]): Array[Byte] =
    s"${dataEncode(d)(EncodeDatapoint[Any])}\n".getBytes(UTF8)

  def fromMonitoring(M: Monitoring)(implicit log: String => Unit): Process[Task, Array[Byte]] =
    Monitoring.subscribe(M)(Key.StartsWith("previous")).map(datapointToWireFormat)

}
