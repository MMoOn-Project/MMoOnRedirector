package org.mmoon.scalatra

import javax.activation.MimeType
import java.io.File

import scala.io.Source

import org.scalatra.{NotFound, Ok, SeeOther}

class Redirector extends MmoonredirectorStack {

  private val documentRoot = "/media/robert/work/test/"

  ////CORE
  //serve always full ontology
  get("""^/core(/[a-z]+)?/?$""".r) { redirectStaticResource("/core") }

  get("""^/core(.ttl|.html|.rdf|.owx|.owl|.owm|.jsonld|.nt)$""".r)
    { serveFile("/core", multiParams("captures").apply(0)) }

  ////SCHEMA
  //serve always full schema file
  get("""^(/[a-z]+/schema/[a-z]+)(/[a-z]+)?/?$""".r) { redirectStaticResource(multiParams("captures").apply(0)) }

  get("""^(/[a-z]+/schema/[a-z]+)(.ttl|.html|.rdf|.owx|.owl|.owm|.jsonld|.nt)$""".r)
    { serveFile(multiParams("captures").apply(0), multiParams("captures").apply(1)) }

  ////INVENTORY
  //serve full dataset
  get("""^(/[a-z]+/inventory/[a-z]+)/?$""".r) { redirectStaticResource(multiParams("captures").apply(0)) }

  get("""^(/[a-z]+/inventory/[a-z]+)(.ttl|.html|.rdf|.owx|.owl|.owm|.jsonld|.nt)$""".r)
    { serveFile(multiParams("captures").apply(0), multiParams("captures").apply(1)) }

  //serve just one resource
  get("""^(/[a-z]+/inventory/[a-z]+/[a-zA-Z-_]+)/?$""".r) { checkInventoryResource(multiParams("captures").apply(0)) match {
    case Some(t) => SeeOther("http://mmoon.org" + multiParams("captures").apply(0) + getFileExtension(t).getOrElse(""))
    case None => NotFound("Sorry, the resource could not be found")
  }}

  get("""^(/[a-z]+/inventory/[a-z]+/[a-zA-Z-_]+)(.ttl|.html|.rdf|.jsonld|.nt)$""".r)
  //get("""^(/Berlin)(.ttl|.html|.rdf|.owl|.jsonld|.nt)$""".r)
   { serveInventoryResource(multiParams("captures").apply(0), multiParams("captures").apply(1)) }

  get("""^(/lodview/[a-zA-Z/\.-_]+)$""".r)
    { Ok(Source.fromURL("http://127.0.0.1:8080/" + multiParams("captures").apply(0)).mkString) }

  post("""^(/lodview/[a-zA-Z/]+)$""".r)
    { Ok(Source.fromURL("http://127.0.0.1:8080/" + multiParams("captures").apply(0)).mkString) }


  private def redirectStaticResource(relPath : String) = {
    def checkResourceExistence(basePath : String, mimeTypes : List[MimeType]) : Option[MimeType] = {
      def checkFile(t: MimeType) : Boolean = {
        val fileExt = getFileExtension(t)
        if (fileExt.isDefined) {
          val file = new File(basePath + fileExt.get)
          file.exists && !file.isDirectory
        } else false
      }
      //Don't recompile the same regular expression on each case evaluation, but rather have it on an object.
      val x = """[a-z]+/[a-z+-]+""".r
      val y = """[a-z]+/\*""".r
      val z = """\*/\*""".r

      for (t <- mimeTypes) t.toString match {
        case x() => if(checkFile(t)) return Some(t)
        case y() => for (s <- mimeTypeMapping.map(k => new MimeType(k._2)) if t.`match`(s)) if (checkFile(s)) return Some(s)
        case z() => if(checkFile(new MimeType("text/html"))) return Some(new MimeType("text/html"))
        case _ =>
      }; None
    }
    checkResourceExistence(documentRoot + relPath, acceptedMimeTypes.sortWith(_.q > _.q).map(_.value)) match {
      case Some(mimeType) => SeeOther("http://mmoon.org" + relPath + getFileExtension(mimeType).get)
      case None => NotFound("Sorry, the file could not be found")
    }
  }

  private def serveFile(relPath : String, fileExt : String) = {
    val file = new File(documentRoot + relPath + fileExt)
    if (file.exists && !file.isDirectory) Ok(file, Map("Content-Type" -> getMimeType(fileExt).getOrElse("").toString))
    else NotFound("Sorry, the file could not be found")
  }

  private def checkInventoryResource(relPath: String) : Option[MimeType] = {
    if(Source.fromURL("http://127.0.0.1:8080/lodview" + relPath + "?output=application%2Fn-triples").mkString.length == 0) None
    else {
      val x = """[a-z]+/[a-z+-]+""".r
      val y = """[a-z]+/\*""".r
      val z = """\*/\*""".r

      val supportedMimeTypes = List("application/rdf+xml", "text/html", "text/turtle", "application/n-triples", "application/n-triples")

      for (t <- acceptedMimeTypes.sortWith(_.q > _.q).map(_.value)) t.toString match {
        case x() => if(supportedMimeTypes.contains(t.toString)) return Some(t)
        case y() => for (s <- supportedMimeTypes.map(new MimeType(_)) if t.`match`(s)) return Some(s)
        case z() => return Some(new MimeType("text/html"))
        case _ =>
      }; None
    }
  }

  private def serveInventoryResource(relPath : String, fileExt : String) = {
    val t = getMimeType(fileExt).getOrElse(new MimeType)

    //It seems, there is no ProxyPass functionality included in Scalatra. So a little workaround is necessary.
    fileExt match {
      case ".html" => Ok(Source.fromURL("http://127.0.0.1:8080/lodview" + relPath).mkString,
        Map("Content-Type" -> "text/html"))
      case _ => Ok(Source.fromURL("http://127.0.0.1:8080/lodview" + relPath + "?output=" + t.getPrimaryType + "%2F"
        + t.getSubType).mkString, Map("Content-Type" -> t.toString))
    }
  }

  private def getFileExtension(mimeType: MimeType) : Option[String] =
    mimeTypeMapping.map(_.swap).toMap.get(mimeType.toString)


  private def getMimeType(fileExt: String): Option[MimeType] = mimeTypeMapping.toMap.get(fileExt) match {
    case Some(mimeTypeStr) => Some(new MimeType(mimeTypeStr))
    case None => None
  }
}