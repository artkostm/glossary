package com.artkostm

import akka.http.scaladsl.model.StatusCode

package object glossary {
  case class Translation(text: String)

  case class Definition(text: String, pos: Option[String], ts: Option[String], fl: Option[String], tr: List[Translation])

  case class DictResp(`def`: List[Definition])


  sealed trait ApiError

  case class NotFound(error: String) extends ApiError

  case class UnexpectedStatusCode(status: StatusCode) extends ApiError
}
