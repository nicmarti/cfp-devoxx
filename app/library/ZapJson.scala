package library

import play.api.data.validation.ValidationError
import play.api.libs.json.JsPath

/**
 * Helper for some missing function in Play2 JSON.
 *
 * Author: nicolas
 * Created: 06/05/2013 15:31
 */
object ZapJson {

  def showError(errors: Seq[(JsPath, Seq[ValidationError])]): String = {
    errors.map {
      case (jsPath, seqValidationErrors) =>
        "Error with " + jsPath + " due to " + seqValidationErrors.map(_.message).mkString(",")
    }.mkString("\n")
  }
}
