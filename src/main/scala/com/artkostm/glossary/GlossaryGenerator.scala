/**
  * Created by artsiom.chuiko on 28/04/2017.
  */

import java.io.IOException
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.Date

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.model._
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.stream._
import akka.stream.scaladsl.{Flow, GraphDSL, RunnableGraph, Source}
import com.norbitltd.spoiwo.model.{Row, Sheet}
import com.peoplepattern.text.StringUtil
import spray.json.DefaultJsonProtocol

import scala.collection.immutable.SortedMap
import scala.collection.mutable.ListBuffer
import scala.concurrent.{Await, Future, Promise}
import scala.concurrent.duration.Duration
import scala.io.{Source => S}
import scala.util.{Failure, Success, Try}
import scala.concurrent.duration._

case class Translation(text: String)
case class Definition(text: String, pos: Option[String], ts: Option[String], fl: Option[String], tr: List[Translation])
case class DictResp(`def`: List[Definition])

object Utils {
  val emptyString = ""

  def url(term: String, key: String): String = s"/api/v1/dicservice.json/lookup?key=$key&lang=en-ru&text=${URLEncoder.encode(term, StandardCharsets.UTF_8.name)}"

  def createFormattedLine(resp: DictResp): String =
    resp.`def`.find(d => !StringUtil.isBlank(d.text))
      .map(_.text)
      .map(t => s"$t ${findFl(resp.`def`)};[${findTs(resp.`def`)}];${createTranslations(resp.`def`)}")
      .getOrElse(emptyString)

  def createTranslations(defs: List[Definition]): String = {
    val builder = ListBuffer.empty[String]
    defs.foreach {
      case Definition(_, p, _, _, tr) => builder += s"${pos(p)} ${tr.map(_.text).mkString(", ")}"
    }
    builder.mkString("\n")
  }

  def findTs(defs: List[Definition]): String = defs.find {
    case Definition(_, _, Some(_), _, _) => true
    case _ => false
  }.flatMap(_.ts).getOrElse(emptyString)

  def findFl(defs: List[Definition]): String = defs.find {
    case Definition(_, _, _, Some(_), _) => true
    case _ => false
  }.flatMap(_.fl).map(fl => s"(v. $fl)").getOrElse(emptyString)

  def pos(pos: Option[String]): String = pos match {
    case Some("adjective") => "adj."
    case Some("noun") => "n."
    case Some("verb") => "v."
    case Some("adverb") => "adv."
    case Some("participle") => "p."
    case Some("pronoun") => "pron."
    case Some("conjunction") => "conj."
    case Some("preposition") => "prep."
    case Some("numeral") => "num."
    case Some(t) => t
    case _ => emptyString
  }
}

trait JsonSupport extends SprayJsonSupport with DefaultJsonProtocol {
  implicit val translationFormat = jsonFormat1(Translation)
  implicit val definitionFormat = jsonFormat5(Definition)
  implicit val dictRespFormat = jsonFormat1(DictResp)
}

sealed trait ApiError
case class NotFound(error: String) extends ApiError
case class UnexpectedStatusCode(status: StatusCode) extends ApiError

class DictApiClient()(implicit val system: ActorSystem, maaterializer: ActorMaterializer) {

  implicit val dispatcher = system.dispatcher

  val pool = Http().cachedHostConnectionPoolHttps[Int]("dictionary.yandex.net")

  def translate(tokens: List[String], apiKey: String) = Source.fromGraph(GraphDSL.create() { implicit builder =>
    import GraphDSL.Implicits._

    val tokenSource = Source(tokens)

    val toHttpRequest = Flow[String].map(token => HttpRequest(HttpMethods.GET, uri = Utils.url(token, apiKey)))

    val withPromise = Flow[HttpRequest].map((_, Promise[DictResp]))

    val httpsPool = Http().cachedHostConnectionPoolHttps[Promise[DictResp]]("dictionary.yandex.net")


    tokenSource ~> toHttpRequest ~> withPromise ~> httpsPool
    SourceShape(Source(tokens).shape.out)
  })

  //import com.peoplepattern.text.Implicits._
  def executeFlatten(origText: String, key: String): Future[Iterable[HttpResponse]] = {
    val tt = origText.split("\n").flatMap(_.split(" "))
    println(tt.toList)
    Source.fromIterator(() => tt.map(term => HttpRequest(HttpMethods.GET, uri = Utils.url(term, key))).zipWithIndex.toIterator)
    //.throttle(1, 3.second, 2, ThrottleMode.Shaping)
    //.via(Flow[(HttpRequest, Int)].map(tpl => {println(tpl._1); tpl}))
      .via(pool)
      //.via(Flow[(Try[HttpResponse], Int)].map(tpl => {println(tpl._1); tpl}))
      //.throttle(1, 3.second, 2, ThrottleMode.Shaping)
      .runFold(SortedMap[Int, Future[HttpResponse]]()) {
        case (m, (Success(r), idx)) => m + (idx -> Future.successful(r))
        case (m, (Failure(e), idx)) => m + (idx -> Future.failed(e))
      }.flatMap(r => {println(r);Future.sequence(r.values)})
  }
}

object EntryPoint extends App with JsonSupport {
  val lineSeparator = sys.props("line.separator")
  val apiKey = scala.util.Properties.envOrNone("DICT_API_KEY").getOrElse(S.fromFile("key").getLines().mkString)
  val originalText = S.fromFile("text.txt").getLines().mkString(lineSeparator)

  val decider: Supervision.Decider = {
    case error: Throwable =>
      error.printStackTrace()
      Supervision.Stop
  }
  implicit val system = ActorSystem()
  implicit val dispatcher = system.dispatcher
  implicit val materializer = ActorMaterializer(ActorMaterializerSettings(system).withSupervisionStrategy(decider))

  val start = System.currentTimeMillis()
  import com.norbitltd.spoiwo.natures.xlsx.Model2XlsxConversions._
  val result = for {
    responses <- new DictApiClient().executeFlatten(originalText, apiKey)
    dictionaryResponse <- Future.traverse(responses)(response => {println(response);Unmarshal(response.entity).to[DictResp].recoverWith {
      case ex =>
        Unmarshal(response.entity).to[String].flatMap { body =>
          Future.failed(new IOException(s"Failed to unmarshal with ${ex.getMessage} and response body is\n $body"))
        }
    }})
  } yield {
    println(dictionaryResponse)
    Sheet(
    name = "glossary", rows = dictionaryResponse.map(Utils.createFormattedLine)
      .filter(line => !StringUtil.isBlank(line))
      .toList.sorted
      .zipWithIndex.toMap
      .map(lineWithIndex => lineWithIndex._1.split(";") match {
        case Array(text, ts, tr) => Row().withCellValues(lineWithIndex._2 + 1, text, ts, tr)
      }).toList
  ).saveAsXlsx(s"glossary-${new Date().getTime}.xlsx")}

  result.onComplete {
    case Success(_) =>
      println(s"Done! Time: ${System.currentTimeMillis() - start}")
      system.terminate()
    case Failure(e) =>
      system.terminate()
      e.printStackTrace()
  }

  Await.result(system.whenTerminated, 1 minute)
}

