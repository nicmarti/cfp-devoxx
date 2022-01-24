package filters

import play.api.Play
import play.api.mvc.{EssentialAction, RequestHeader}
import play.filters.gzip.GzipFilter

class CustomGzipFilter extends GzipFilter {

  override def apply(next: EssentialAction) = {
    if (Play.current.configuration.getBoolean("cfp.activateGZIP").getOrElse(true)) {
      super.apply(next);
    } else {
      new EssentialAction {
        def apply(request: RequestHeader) = {
          next(request)
        }
      }
    }
  }

}
