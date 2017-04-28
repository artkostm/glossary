/**
  * Created by artsiom.chuiko on 28/04/2017.
  */
import java.nio.charset.StandardCharsets

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.common.{EntityStreamingSupport, JsonEntityStreamingSupport}
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.model.{HttpMethods, HttpRequest}
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Sink, Source}
import com.peoplepattern.text.Implicits._
import spray.json.DefaultJsonProtocol

import scala.concurrent.Await
import scala.io.{Source => S}
import scala.util.{Failure, Success}

object EntryPoint extends App with JsonSupport {
  val apiKey = S.fromFile("key").getLines().mkString
  val originalText = S.fromFile("text.txt").getLines().mkString

  implicit val system = ActorSystem()
  implicit val dispatcher = system.dispatcher
  implicit val materializer = ActorMaterializer()

  implicit val jsonStreamingSupport: JsonEntityStreamingSupport = EntityStreamingSupport.json()

  import scala.concurrent.duration._
  Source.single(HttpRequest(HttpMethods.GET, uri = url("time")) -> 42).via(Http().cachedHostConnectionPoolHttps[Int]("dictionary.yandex.net")).runWith(Sink.head).onComplete {
      case Success(response) => response match {
        case (Success(value), n) => value.entity.toStrict(5 seconds).map(_.data.decodeString(StandardCharsets.UTF_8.name)).onComplete {
          case Success(string) => println(string)
          case Failure(throwable) => throwable.printStackTrace()
        }
        case (Failure(th), n) => th.printStackTrace()
      }
      case Failure(t) => t.printStackTrace()
  }

  Await.result(system.terminate(), Duration.Inf)

  def url(term: String): String = s"/api/v1/dicservice.json/lookup?key=$apiKey&lang=en-ru&text=$term"
}

case class Translation(text: String)
case class Definition(text: String, pos: String, ts: Option[String], tr: Array[Translation])
case class DictResp(`def`: Array[Option[Definition]])

trait JsonSupport extends SprayJsonSupport with DefaultJsonProtocol {
  implicit val translationFormat = jsonFormat1(Translation)
  implicit val definitionFormat = jsonFormat4(Definition)
  implicit val dictRespFormat = jsonFormat1(DictResp)
}

