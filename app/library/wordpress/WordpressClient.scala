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

import org.apache.xmlrpc.client.{XmlRpcClient, XmlRpcClientConfigImpl}
import java.net.URL

/**
 * A simple wordpress client that is able to post pages with XMLRPC.
 * Created by nicolas martignole on 22/05/2014.
 */
trait WordpressClient {
  def newPost(page: Page)
}

object WordpressClient {

  val blogId ="0"
  val postConverter = new PostConverter()

  def apply(user: String, password: String, xmlURL: String): WordpressClient = {
    new WordpressClientImpl(user, password, xmlURL)
  }

  private class WordpressClientImpl(user: String, password: String, xmlURL: String) extends WordpressClient {
    override def newPost(page: Page): Unit = {
      val convertedPage = postConverter.convert(page)

      println("new page called in ClientIMPL")
      val client = new XmlRpcClient()

      val xmlrpcConfig = new XmlRpcClientConfigImpl()
      xmlrpcConfig.setBasicUserName(user)
      xmlrpcConfig.setBasicPassword(password)
      xmlrpcConfig.setServerURL(new URL(xmlURL))
      xmlrpcConfig.setEnabledForExtensions(true)
      xmlrpcConfig.setEnabledForExceptions(true)

      client.setConfig(xmlrpcConfig)

      val params = Array[Object](blogId, user, password, convertedPage)
      val result = client.execute("wp.newPost", params)

      println("Got a result : " + result)

    }
  }

}
