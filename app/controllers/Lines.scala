package controllers

import authes.AuthConfigImpl
import authes.Role.{Administrator, NormalUser}
import caches.LineStationsCache
import com.github.tototoshi.play2.json4s.Json4s
import com.google.inject.Inject
import jp.t2v.lab.play2.auth.AuthElement
import models.{Line, LineStation, Station, StationRankSerializer}
import org.json4s._
import play.api.mvc.Controller
import queries.{CreateLine, Paging}
import responses.{Page, WithPage}
import scalikejdbc._

import scala.concurrent.ExecutionContext

class Lines @Inject() (json4s: Json4s, _ec: ExecutionContext) extends Controller with AuthElement with AuthConfigImpl {
  import Lines._
  import Responses._
  import json4s._
  implicit val formats = DefaultFormats + StationRankSerializer
  implicit val ec = _ec

  val optionalPaging = parse.using { req =>
    if (req.getQueryString("page").isDefined) parse.form(Paging.form).map(Some(_))
    else parse.ignore(None)
  }

  def list() = StackAction(optionalPaging, AuthorityKey -> NormalUser) { implicit req =>
    import models.DefaultAliases.l
    val result = req.body.fold[Any] {
      Line.findAll(Seq(l.id.asc))
    } { paging =>
      val where = paging.q.fold(sqls"true") { q => sqls.like(l.name, s"%${q}%") }
      val data = Line.findAllByWithPagination(where, paging.pagination, Seq(l.id.desc))
      val count = Line.countBy(where)
      WithPage(Page(count, paging.size, paging.page), data)
    }
    Ok(Extraction.decompose(result))
  }

  def create() = StackAction(json, AuthorityKey -> Administrator) { implicit req =>
    req.body.extractOpt[CreateLine].fold(JSONParseError) { line =>
      createLine(line)
      LineStationsCache.clear()
      Success
    }
  }

  def lineStations(lineId: Long) = StackAction(AuthorityKey -> NormalUser) { implicit req =>
    import models.DefaultAliases.ls
    val stations = LineStation.joins(LineStation.stationRef).findAllBy(sqls.eq(ls.lineId, lineId), Seq(ls.km.asc))
    Ok(Extraction.decompose(stations))
  }
}

object Lines {
  private def createLine(line: CreateLine): Long = {
    DB localTx { implicit session =>
      val lineId = line.line.save()
      val stations = line.stations
        .flatMap { st => st.station.map(st -> _) }
        .map { case (key, st) => key -> upsertStation(st) }
      stations.foreach {
        case (create, stId) =>
          LineStation(0L, lineId, stId, create.km).save()
      }
      lineId
    }
  }

  private[this] def upsertStation(st: Station)(implicit session: DBSession): Long =
    Station.findByName(st.name).fold(st.save())(_.id)
}
