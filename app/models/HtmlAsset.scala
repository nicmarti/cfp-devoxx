package models

import play.twirl.api.Html

sealed trait AssetKind {
  def toHtml(url: String): Html;
}
case object Stylesheet extends AssetKind {
  def toHtml(url: String): Html = Html(s"""<link href="${url}" rel="stylesheet"></style>""")
}
case object Javascript extends AssetKind {
  def toHtml(url: String): Html = Html(s"""<script src="${url}" type="text/javascript"></script>""")
}

case class HtmlAsset(url: String, kind: AssetKind) {
  def toHtml(): Html = kind.toHtml(url)
}
