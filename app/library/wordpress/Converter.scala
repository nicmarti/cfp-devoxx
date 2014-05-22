/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2013 Association du Paris Java User Group.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of
 * this software and associated documentation files (the "Software"), to deal in
 * the Software without restriction, including without limitation the rights to
 * use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of
 * the Software, and to permit persons to whom the Software is furnished to do so,
 * subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
 * FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 * COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER
 * IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN
 * CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package library.wordpress

import org.joda.time.DateTime


/**
 * Generic interface
 * Created by nicolas on 22/05/2014.
 */
trait Converter[E,T] {
  def convert(fromObject: E): T
}

/**
 * Converts a Page to an XMLRPC array
 */
class PostConverter extends Converter[Page, Map[String,Object]] {

  val defaultPostType="page"
  val defaultPostStatus="draft"

  def convert(fromObject: Page): Map[String,Object] = {
    val post = scala.collection.mutable.HashMap[String,Object]()
    post.put("post_type", defaultPostType)
    post.put("post_status", defaultPostStatus)
    post.put("post_title", fromObject.title)
    post.put("post_author", "6")
    post.put("post_content", fromObject.content)
    post.put("post_date", new DateTime().toString("Y-m-d H:m:s"))
    post.put("comment_status","closed")
    post.put("ping_status","closed")
    if (fromObject.hasTaxonomies) {
      val terms = scala.collection.mutable.HashMap[String,Object]()
      fromObject.category.map{c=>
        terms.put("category", Array[String](c.name))
      }
      post.put("terms_names", terms)

      val tags: Set[Tag] = fromObject.tags
      if (!tags.isEmpty) {
        val tagTerms: Array[String] = new Array[String](tags.size)
        var i: Int = 0
        import scala.collection.JavaConversions._
        for (tag <- tags) {
          tagTerms(i) = tag.name
          i += 1
        }
        terms.put("post_tag", tagTerms)
      }
    }
     post.toMap
  }

}