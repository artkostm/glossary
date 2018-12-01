package com.artkostm.glossary

/**
  * Created by artsiom.chuiko on 28/04/2017.
  */

import java.util.Date

import akka.actor.ActorSystem
import akka.stream._
import akka.stream.scaladsl.{Flow, Sink}
import com.artkostm.glossary.client.DictApiClient
import com.norbitltd.spoiwo.model.{Row, Sheet}

import scala.concurrent.duration._
import scala.concurrent.Await
import scala.io.{Source => S}
import scala.util.{Failure, Success}

object EntryPoint extends App with DictionaryFormatter {
  val lineSeparator = sys.props("line.separator")
  val apiKey = scala.util.Properties.envOrNone("DICT_API_KEY").getOrElse(S.fromFile("key").getLines().mkString)
  val originalText = S.fromFile("text.txt").getLines().mkString(lineSeparator)

  val decider: Supervision.Decider = {
    case error: Throwable =>
      error.printStackTrace()
      Supervision.Resume
  }
  implicit val system = ActorSystem()
  implicit val dispatcher = system.dispatcher
  implicit val mat = ActorMaterializer(
    ActorMaterializerSettings(system).withSupervisionStrategy(decider)
  )

  import com.norbitltd.spoiwo.natures.xlsx.Model2XlsxConversions._

  val result = DictApiClient()
    .translate(originalText.split("\n").flatMap(_.split(" ")).toIterator, apiKey)
    .via(
      Flow[DictResp].map(createFormattedLine).map(s => Row().withCellValues(s.split(";"): _*))
    ).runWith(Sink.seq)
    .map(rows => Sheet(name = "glossary", rows = rows.toList).saveAsXlsx(s"glossary-${new Date().getTime}.xlsx"))

  result.onComplete {
    case Success(_) => system.terminate()
    case Failure(e) => system.terminate()
      e.printStackTrace()
  }

  Await.result(system.whenTerminated, 1 minute)
}

