/**
  * Created by artsiom.chuiko on 28/04/2017.
  */
import java.io.{BufferedWriter, File, FileWriter}
import java.nio.charset.StandardCharsets

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.common.{EntityStreamingSupport, JsonEntityStreamingSupport}
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.model._
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.Source
import com.peoplepattern.text.StringUtil
import spray.json.DefaultJsonProtocol

import scala.collection.immutable.SortedMap
import scala.collection.mutable.ListBuffer
import scala.concurrent.{Await, Future}
import scala.concurrent.duration.Duration
import scala.io.{Source => S}
import scala.util.{Failure, Success}

case class Translation(text: String)
case class Definition(text: String, pos: Option[String], ts: Option[String], tr: List[Translation])
case class DictResp(`def`: List[Definition])

object Utils {
  def url(term: String, key: String): String = s"/api/v1/dicservice.json/lookup?key=$key&lang=en-ru&text=$term"

  def createFormattedLine(resp: DictResp): String = {
    val text = resp.`def`.find(d => !StringUtil.isBlank(d.text)).map(_.text)
    text.map(t => s"$t;[${findTs(resp.`def`)}];${createTranslations(resp.`def`)}").getOrElse("")
  }

  def createTranslations(defs: List[Definition]): String = {
    val builder = ListBuffer.empty[String]
    defs.foreach {
      case Definition(_, p, _, tr) => builder += s"${pos(p)} ${tr.map(_.text).mkString(",")}|"
    }
    builder.mkString
  }

  def findTs(defs: List[Definition]): String = defs.find {
    case Definition(_, _, Some(_), _) => true
  }.flatMap(_.ts).getOrElse("")

  def pos(pos: Option[String]): String = pos match {
    case Some("adjective") => "adj"
    case Some("noun") => "n"
    case Some("verb") => "v"
    case Some(t) => t
    case _ => ""
  }

  def writeToFile(bw: BufferedWriter, text: String): Unit = {
    bw.newLine()
    bw.write(text)
  }
}

trait JsonSupport extends SprayJsonSupport with DefaultJsonProtocol {
  implicit val translationFormat = jsonFormat1(Translation)
  implicit val definitionFormat = jsonFormat4(Definition)
  implicit val dictRespFormat = jsonFormat1(DictResp)
}

sealed trait ApiError
case class NotFound(error: String) extends ApiError
case class UnexpectedStatusCode(status: StatusCode) extends ApiError

class DictApiClient()(implicit val system: ActorSystem = ActorSystem()) extends JsonSupport {

  implicit val dispatcher = system.dispatcher
  implicit val materializer = ActorMaterializer()

  val pool = Http().cachedHostConnectionPoolHttps[Int]("dictionary.yandex.net")

  import com.peoplepattern.text.Implicits._
  def executeFlatten(origText: String, key: String): Future[Iterable[HttpResponse]] =
    Source(origText.terms.map(term => HttpRequest(HttpMethods.GET, uri = Utils.url(term, key))).zipWithIndex.toMap)
      .via(pool)
      .runFold(SortedMap[Int, Future[HttpResponse]]()) {
        case (m, (Success(r), idx)) => m + (idx -> Future.successful(r))
        case (m, (Failure(e), idx)) => m + (idx -> Future.failed(e))
      }.flatMap(r => Future.sequence(r.values))
}

object EntryPoint extends App with JsonSupport{
  val apiKey = S.fromFile("key").getLines().mkString
  val originalText = S.fromFile("text.txt").getLines().mkString

  implicit val system = ActorSystem()
  implicit val dispatcher = system.dispatcher
  implicit val materializer = ActorMaterializer()

  val file = new File("output")
  val bw = new BufferedWriter(new FileWriter(file))

  new DictApiClient().executeFlatten(originalText, apiKey).onComplete {
    case Success(responses) => responses.map { response =>
      response.status match {
        case StatusCodes.OK => Unmarshal(response.entity).to[DictResp].map(d => Utils.createFormattedLine(d)).foreach(t => Utils.writeToFile(bw, t))
        case StatusCodes.BadRequest => println(s"Bad request: ${response.entity.toString}")
        case _ => println(s"Unexpected status: ${response.status}")
      }
    }
    case Failure(e) => e.printStackTrace()
  }

  Thread.sleep(2000000)
  bw.close()
  Await.result(system.terminate(), Duration.Inf)
}

