package com.artkostm.glossary.client

import java.net.URLEncoder

import akka.NotUsed
import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.model.{HttpMethods, HttpRequest, HttpResponse}
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.stream.{ActorMaterializer, SourceShape}
import akka.stream.scaladsl.{Balance, Flow, GraphDSL, Merge, Source}
import akka.util.ByteString
import com.artkostm.glossary.{Definition, DictResp, Translation}
import spray.json.DefaultJsonProtocol

import scala.collection.Iterator
import scala.concurrent.{Future, Promise}
import scala.util.{Failure, Success, Try}

trait DictApiClient extends SprayJsonSupport with DefaultJsonProtocol {
  implicit val translationFormat = jsonFormat1(Translation)
  implicit val definitionFormat = jsonFormat5(Definition)
  implicit val dictRespFormat = jsonFormat1(DictResp)

  def translate(tokens: Iterator[String], apiKey: String)
               (implicit system: ActorSystem, materializer: ActorMaterializer): Source[DictResp, NotUsed] =
    Source.fromGraph(GraphDSL.create() { implicit builder =>
      import GraphDSL.Implicits._

      implicit val dispatcher = system.dispatcher

      val tokenSource = builder.add(Source.fromIterator(() => tokens))

      val toHttpRequest = builder.add(Flow[String].map(token => HttpRequest(HttpMethods.GET, uri = url(token, apiKey))))

      val withPromise = builder.add(Flow[HttpRequest].map((_, Promise[DictResp])))

      val httpsPool = Http().cachedHostConnectionPoolHttps[Promise[DictResp]]("dictionary.yandex.net")

      val router = builder.add(Balance[(Try[HttpResponse], Promise[DictResp])](2))

      val merge = builder.add(Merge[DictResp](2))

      val toDictResp = Flow[(Try[HttpResponse], Promise[DictResp])].mapAsyncUnordered(4) {
        case (Success(response), _) => Unmarshal(response.entity).to[DictResp]
        case (Failure(error), _) => Future.failed(error)
      }

      val nonEmpty = builder.add(Flow[DictResp].filter(_.`def`.nonEmpty))

      tokenSource ~> toHttpRequest ~> withPromise ~> httpsPool.async ~> router.in
                                                                        router.out(0) ~> toDictResp ~> merge.in(0)
                                                                        router.out(1) ~> toDictResp ~> merge.in(1)

      merge.out ~> nonEmpty.in
      SourceShape(nonEmpty.out)
    })

  protected def url(term: String, key: String): String =
    s"/api/v1/dicservice.json/lookup?key=$key&lang=en-ru&text=${URLEncoder.encode(term, ByteString.UTF_8)}"
}

object DictApiClient {
  def apply(): DictApiClient = new DictApiClient() {}
}
