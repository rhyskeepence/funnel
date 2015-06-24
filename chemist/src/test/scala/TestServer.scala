package funnel
package chemist

import scalaz.concurrent.Task

object TestServer {
  def main(args: Array[String]): Unit = {
    val platform = new TestPlatform {
      val config = new TestConfig
    }
    val core = new TestChemist

    val monitoring = http.MonitoringServer.start(Monitoring.default, 5775)

    Server.unsafeStart(new Server(core, platform))

    monitoring.stop()
    dispatch.Http.shutdown()
  }
}
