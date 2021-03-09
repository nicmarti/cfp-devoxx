package library.search

import com.sksamuel.elastic4s.{ElasticClient, ElasticProperties}
import com.sksamuel.elastic4s.http.{JavaClient, NoOpRequestConfigCallback}
import org.apache.http.auth.{AuthScope, UsernamePasswordCredentials}
import org.apache.http.impl.client.BasicCredentialsProvider
import org.apache.http.impl.nio.client.HttpAsyncClientBuilder
import org.elasticsearch.client.RestClientBuilder.HttpClientConfigCallback
import play.api.Play

object ESClient {
  val host: String = Play.current.configuration.getString("elasticsearch.host").getOrElse("http://localhost")
  val port: String = Play.current.configuration.getString("elasticsearch.port").getOrElse("9200")
  val isHttps: Boolean = Play.current.configuration.getString("elasticsearch.isHTTPS").forall(s => s.equalsIgnoreCase("true"))
  val username: String = Play.current.configuration.getString("elasticsearch.username").getOrElse("")
  val password: String = Play.current.configuration.getString("elasticsearch.password").getOrElse("")

  lazy val elasticURI = isHttps match {
    case false =>
      "http://" + host + ":" + port
    case _ =>
      "https://" + host + ":" + port
  }

  lazy val callback = new HttpClientConfigCallback {
    override def customizeHttpClient(httpClientBuilder: HttpAsyncClientBuilder): HttpAsyncClientBuilder = {
      val creds = new BasicCredentialsProvider()
      creds.setCredentials(AuthScope.ANY, new UsernamePasswordCredentials(username, password))
      httpClientBuilder.setDefaultCredentialsProvider(creds)
    }
  }

  def open(): ElasticClient = {
    ElasticClient(JavaClient(ElasticProperties(elasticURI), requestConfigCallback = NoOpRequestConfigCallback, httpClientConfigCallback = callback))
  }
}
