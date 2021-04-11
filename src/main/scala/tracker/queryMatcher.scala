package tracker

import org.http4s.dsl.io._

object IdQueryParamMatcher extends QueryParamDecoderMatcher[Long]("id")

object OptionalVehicleQueryParamMatcher extends OptionalQueryParamDecoderMatcher[Long]("vehicleId")

object PageQueryParamMatcher extends OptionalQueryParamDecoderMatcher[Int]("page")

object PageSizeQueryParamMatcher extends OptionalQueryParamDecoderMatcher[Int]("pageSize")

object MonthQueryParamMatcher extends QueryParamDecoderMatcher[Int]("month")

object YearQueryParamMatcher extends QueryParamDecoderMatcher[Int]("year")
