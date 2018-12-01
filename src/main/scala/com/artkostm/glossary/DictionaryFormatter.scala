package com.artkostm.glossary

import com.peoplepattern.text.StringUtil

import scala.collection.mutable.ListBuffer

trait DictionaryFormatter {
  val emptyString = ""

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
