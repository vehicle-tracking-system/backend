package tracker

import org.http4s.dsl.io._

object IdQueryParamMatcher extends QueryParamDecoderMatcher[Long]("id")

object PageQueryParamMatcher extends OptionalQueryParamDecoderMatcher[Int]("page")

object PageSizeQueryParamMatcher extends OptionalQueryParamDecoderMatcher[Int]("page-size")
