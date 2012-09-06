package net.kriomant.gortrans

import android.database.sqlite.{SQLiteDatabase, SQLiteOpenHelper}
import android.content.{ContentValues, Context}

import utils.closing
import android.util.Log
import android.database.{Cursor, CursorWrapper}
import net.kriomant.gortrans.core.{DirectionsEx, VehicleType}
import java.util
import utils.booleanUtils
import net.kriomant.gortrans.parsing.{RouteStop, RoutePoint}
import scala.collection.mutable

object Database {
	val TAG = getClass.getName

	val NAME = "gortrans"
	val VERSION = 2

	class Helper(context: Context) extends SQLiteOpenHelper(context, NAME, null, VERSION) {
		def onCreate(db: SQLiteDatabase) {
			Log.i(TAG, "Create database")

			transaction(db) {
				executeScript(db, "create")
			}
		}

		def onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
			transaction(db) {
				for (from <- oldVersion until newVersion) {
					Log.i(TAG, "Upgrade database from version %d to %d" format (from, from+1))
					executeScript(db, "upgrade-%d-%d" format (from, from+1))
				}
			}
		}

		override def onOpen(db: SQLiteDatabase) {
			super.onOpen(db)

			if (!db.isReadOnly) {
				db.execSQL("PRAGMA foreign_keys = ON")
				/*closing(db.rawQuery("PRAGMA foreign_keys = ON", null)) { cursor =>
					if (!(cursor.moveToNext() && cursor.getInt(0) == 1)) {
						throw new Exception("Foreign keys are not supported by database")
					}
				}*/
			}
		}

		private def executeScript(db: SQLiteDatabase, name: String) {
			val statements = closing(context.getAssets.open("db/%s.sql" format name)) { stream =>
				scala.io.Source.fromInputStream(stream).mkString.split("---x")
			}

			for (sql <- statements)
				db.execSQL(sql)
		}

		private def transaction[T](db: SQLiteDatabase)(f: => T): T = {
			db.beginTransaction()
			try {
				val result = f
				db.setTransactionSuccessful()
				result
			} finally {
				db.endTransaction()
			}
		}
	}

	def getWritable(context: Context) = new Database(new Helper(context).getWritableDatabase)

	object RoutesTable {
		val NAME = "routes"

		val ID_COLUMN = "_id"
		val VEHICLE_TYPE_COLUMN = "vehicleType"
		val EXTERNAL_ID_COLUMN = "externalId"
		val NAME_COLUMN = "name"
		val FIRST_STOP_NAME_COLUMN = "firstStopName"
		val LAST_STOP_NAME_COLUMN = "lastStopName"

		val ALL_COLUMNS = Array(
			ID_COLUMN, VEHICLE_TYPE_COLUMN, EXTERNAL_ID_COLUMN, NAME_COLUMN,
			FIRST_STOP_NAME_COLUMN, LAST_STOP_NAME_COLUMN
		)

		val ID_COLUMN_INDEX = 0
		val VEHICLE_TYPE_COLUMN_INDEX = 1
		val EXTERNAL_ID_COLUMN_INDEX = 2
		val NAME_COLUMN_INDEX = 3
		val FIRST_STOP_NAME_COLUMN_INDEX = 4
		val LAST_STOP_NAME_COLUMN_INDEX = 5

		class Cursor(cursor: android.database.Cursor) extends CursorWrapper(cursor) {
			def id = cursor.getLong(ID_COLUMN_INDEX)
			def vehicleType = VehicleType(cursor.getInt(VEHICLE_TYPE_COLUMN_INDEX))
			def externalId = cursor.getString(EXTERNAL_ID_COLUMN_INDEX)
			def name = cursor.getString(NAME_COLUMN_INDEX)
			def firstStopName = cursor.getString(FIRST_STOP_NAME_COLUMN_INDEX)
			def lastStopName = cursor.getString(LAST_STOP_NAME_COLUMN_INDEX)
		}
	}

	object RoutePointsTable {
		val NAME = "routePoints"

		val ID_COLUMN = "_id"
		val ROUTE_ID_COLUMN = "routeId"
		val INDEX_COLUMN = "idx"
		val LATITUDE_COLUMN = "latitude"
		val LONGITUDE_COLUMN = "longitude"

		val ALL_COLUMNS = Array(
			ID_COLUMN, ROUTE_ID_COLUMN, INDEX_COLUMN, LATITUDE_COLUMN, LONGITUDE_COLUMN
		)

		val ID_COLUMN_INDEX = 0
		val ROUTE_ID_COLUMN_INDEX = 1
		val INDEX_COLUMN_INDEX = 2
		val LATITUDE_COLUMN_INDEX = 3
		val LONGITUDE_COLUMN_INDEX = 4

		class Cursor(cursor: android.database.Cursor) extends CursorWrapper(cursor) {
			def routeId = cursor.getLong(ROUTE_ID_COLUMN_INDEX)
			def index = cursor.getInt(INDEX_COLUMN_INDEX)
			def latitude = cursor.getFloat(LATITUDE_COLUMN_INDEX)
			def longitude = cursor.getFloat(LONGITUDE_COLUMN_INDEX)
		}
	}

	object FoldedRouteStopsTable {
		val NAME = "foldedRouteStops"

		val ID_COLUMN = "_id"
		val ROUTE_ID_COLUMN = "routeId"
		val NAME_COLUMN = "name"
		val INDEX_COLUMN = "idx"
		val FORWARD_POINT_INDEX_COLUMN = "forwardPointIndex"
		val BACKWARD_POINT_INDEX_COLUMN = "backwardPointIndex"

		val ALL_COLUMNS = Array(
			ID_COLUMN, ROUTE_ID_COLUMN, NAME_COLUMN, INDEX_COLUMN, FORWARD_POINT_INDEX_COLUMN, BACKWARD_POINT_INDEX_COLUMN
		)

		val ID_COLUMN_INDEX = 0
		val ROUTE_ID_COLUMN_INDEX = 1
		val NAME_COLUMN_INDEX = 2
		val INDEX_COLUMN_INDEX = 3
		val FORWARD_POINT_INDEX_COLUMN_INDEX = 4
		val BACKWARD_POINT_INDEX_COLUMN_INDEX = 5

		class Cursor(cursor: android.database.Cursor) extends CursorWrapper(cursor) {
			def routeId = cursor.getLong(ROUTE_ID_COLUMN_INDEX)
			def name = cursor.getString(NAME_COLUMN_INDEX)
			def index = cursor.getInt(INDEX_COLUMN_INDEX)
			def forwardPointIndex = !cursor.isNull(FORWARD_POINT_INDEX_COLUMN_INDEX) ? cursor.getInt(FORWARD_POINT_INDEX_COLUMN_INDEX)
			def backwardPointIndex = !cursor.isNull(BACKWARD_POINT_INDEX_COLUMN_INDEX) ? cursor.getInt(BACKWARD_POINT_INDEX_COLUMN_INDEX)

			def directions = (forwardPointIndex, backwardPointIndex) match {
				case (Some(_), Some(_)) => DirectionsEx.Both
				case (Some(_), None) => DirectionsEx.Forward
				case (None, Some(_)) => DirectionsEx.Backward
				case (None, None) => throw new AssertionError
			}
		}
	}

	def escapeLikeArgument(arg: String, escapeChar: Char): String = {
		(arg
			.replace(escapeChar.toString, escapeChar.toString+escapeChar.toString)
			.replace("%", escapeChar.toString+"%")
			.replace("_", escapeChar.toString+"_")
		)
	}
}

class Database(db: SQLiteDatabase) {
	import Database._

	def fetchRoute(vehicleType: VehicleType.Value, externalId: String): RoutesTable.Cursor = {
		val cursor = db.query(
			RoutesTable.NAME, RoutesTable.ALL_COLUMNS,

			"%s=? AND %s=?" format (RoutesTable.VEHICLE_TYPE_COLUMN, RoutesTable.EXTERNAL_ID_COLUMN),
			Array(vehicleType.id.toString, externalId),

			null, null, null
		)

		if (!cursor.moveToFirst())
			throw new Exception("Route %s/%s not found" format (vehicleType, externalId))

		new RoutesTable.Cursor(cursor)
	}

	def fetchRoutes(): RoutesTable.Cursor = {
		new RoutesTable.Cursor(db.query(RoutesTable.NAME, RoutesTable.ALL_COLUMNS, null, null, null, null, null))
	}

	def fetchRoutes(vehicleType: VehicleType.Value): RoutesTable.Cursor = {
		new RoutesTable.Cursor(db.query(
			RoutesTable.NAME, RoutesTable.ALL_COLUMNS,
			RoutesTable.VEHICLE_TYPE_COLUMN formatted "%s=?", Array(vehicleType.id.toString),
			null, null, RoutesTable.NAME_COLUMN formatted "CAST(%s AS INTEGER)"
		))
	}

	def addOrUpdateRoute(vehicleType: VehicleType.Value, externalId: String, name: String, firstStopName: String, lastStopName: String) {
		val values = new ContentValues(5)
		values.put(RoutesTable.NAME_COLUMN, name)
		values.put(RoutesTable.FIRST_STOP_NAME_COLUMN, firstStopName)
		values.put(RoutesTable.LAST_STOP_NAME_COLUMN, lastStopName)

		val affected = db.update(
			RoutesTable.NAME, values,
			"%s=? AND %s=?" format (RoutesTable.VEHICLE_TYPE_COLUMN, RoutesTable.EXTERNAL_ID_COLUMN),
			Array(vehicleType.id.toString, externalId)
		)
		if (affected == 0) {
			values.put(RoutesTable.VEHICLE_TYPE_COLUMN, vehicleType.id.asInstanceOf[java.lang.Integer])
			values.put(RoutesTable.EXTERNAL_ID_COLUMN, externalId)
			db.insertOrThrow(RoutesTable.NAME, null, values)
		}

	}

	def deleteRoute(vehicleType: VehicleType.Value, externalId: String) {
		db.delete(RoutesTable.NAME, "vehicleType=? AND externalId=?", Array(vehicleType.id.toString, externalId))
	}

	def searchRoutes(query: String): RoutesTable.Cursor = {
		new RoutesTable.Cursor(db.query(
			RoutesTable.NAME, RoutesTable.ALL_COLUMNS,

			RoutesTable.NAME_COLUMN formatted "%s LIKE ? ESCAPE '~'",
			Array("%%%s%%" format escapeLikeArgument(query, '~')),

			null, null, RoutesTable.NAME_COLUMN formatted "CAST(%s AS INTEGER)"
		))
	}

	def getLastUpdateTime(code: String): Option[util.Date] = {
		closing(db.query("updateTimes", Array("lastUpdateTime"), "code=?", Array(code), null, null, null)) { cursor =>
			cursor.moveToNext() ? new util.Date(cursor.getLong(0))
		}
	}

	def updateLastUpdateTime(code: String, lastUpdateTime: util.Date) {
		val values = new ContentValues
		values.put("code", code)
		values.put("lastUpdateTime", lastUpdateTime.getTime.asInstanceOf[java.lang.Long])
		db.replaceOrThrow("updateTimes", null, values)
	}

	def findRoute(vehicleType: VehicleType.Value, externalRouteId: String): Long = {
		val cursor = db.query(
			RoutesTable.NAME, Array(RoutesTable.ID_COLUMN),

			"%s=? AND %s=?" format (RoutesTable.VEHICLE_TYPE_COLUMN, RoutesTable.EXTERNAL_ID_COLUMN),
			Array(vehicleType.id.toString, externalRouteId.toString),

			null, null, null
		)

		closing(cursor) { _ =>
			if (cursor.moveToNext())
				cursor.getLong(RoutesTable.ID_COLUMN_INDEX)
			else
				throw new Exception("Unknown route")
		}
	}

	def clearStopsAndPoints(routeId: Long) {
		db.delete(FoldedRouteStopsTable.NAME, "%s=?" format FoldedRouteStopsTable.ROUTE_ID_COLUMN, Array(routeId.toString))
		db.delete(RoutePointsTable.NAME, "%s=?" format RoutePointsTable.ROUTE_ID_COLUMN, Array(routeId.toString))
	}

	def addRoutePoint(routeId: Long, index: Int, point: RoutePoint) {
		val values = new ContentValues
		values.put(RoutePointsTable.ROUTE_ID_COLUMN, routeId.asInstanceOf[java.lang.Long])
		values.put(RoutePointsTable.INDEX_COLUMN, index.asInstanceOf[java.lang.Integer])
		values.put(RoutePointsTable.LATITUDE_COLUMN, point.latitude)
		values.put(RoutePointsTable.LONGITUDE_COLUMN, point.longitude)
		db.insert(RoutePointsTable.NAME, null, values)
	}

	def addRouteStop(routeId: Long, name: String, index: Int, forwardPointIndex: Option[Int], backwardPointIndex: Option[Int]) {
		require(forwardPointIndex.isDefined || backwardPointIndex.isDefined)

		val values = new ContentValues
		values.put(FoldedRouteStopsTable.ROUTE_ID_COLUMN, routeId.asInstanceOf[java.lang.Long])
		values.put(FoldedRouteStopsTable.NAME_COLUMN, name)
		values.put(FoldedRouteStopsTable.INDEX_COLUMN, index.asInstanceOf[java.lang.Integer])
		forwardPointIndex match {
			case Some(idx) => values.put(FoldedRouteStopsTable.FORWARD_POINT_INDEX_COLUMN, idx.asInstanceOf[java.lang.Integer])
			case None => values.putNull(FoldedRouteStopsTable.FORWARD_POINT_INDEX_COLUMN)
		}
		backwardPointIndex match {
			case Some(idx) => values.put(FoldedRouteStopsTable.BACKWARD_POINT_INDEX_COLUMN, idx.asInstanceOf[java.lang.Integer])
			case None => values.putNull(FoldedRouteStopsTable.BACKWARD_POINT_INDEX_COLUMN)
		}
		db.insertOrThrow(FoldedRouteStopsTable.NAME, null, values)
	}

	def fetchRouteStops(routeId: Long): FoldedRouteStopsTable.Cursor = {
		new FoldedRouteStopsTable.Cursor(db.query(
			FoldedRouteStopsTable.NAME, FoldedRouteStopsTable.ALL_COLUMNS,
			FoldedRouteStopsTable.ROUTE_ID_COLUMN formatted "%s=?", Array(routeId.toString),
			null, null, FoldedRouteStopsTable.INDEX_COLUMN
		))
	}

	def fetchRouteStops(vehicleType: VehicleType.Value, externalRouteId: String): FoldedRouteStopsTable.Cursor =
		fetchRouteStops(findRoute(vehicleType, externalRouteId))

	def fetchRoutePoints(routeId: Long): RoutePointsTable.Cursor = {
		new RoutePointsTable.Cursor(db.query(
			RoutePointsTable.NAME, RoutePointsTable.ALL_COLUMNS,
			RoutePointsTable.ROUTE_ID_COLUMN formatted "%s=?", Array(routeId.toString),
			null, null, RoutePointsTable.INDEX_COLUMN
		))
	}

	def fetchRoutePoints(vehicleType: VehicleType.Value, externalRouteId: String): RoutePointsTable.Cursor =
		fetchRoutePoints(findRoute(vehicleType, externalRouteId))

	def fetchLegacyRoutePoints(vehicleType: VehicleType.Value, externalRouteId: String): Seq[RoutePoint] = {
		val routeId = findRoute(vehicleType, externalRouteId)

		val pointIndexToStopName = new mutable.HashMap[Int, RouteStop]
		closing(fetchRouteStops(routeId)) { cursor =>
			while (cursor.moveToNext()) {
				cursor.forwardPointIndex.foreach(idx => pointIndexToStopName(idx) = RouteStop(cursor.name, 0))
				cursor.backwardPointIndex.foreach(idx => pointIndexToStopName(idx) = RouteStop(cursor.name, 0))
			}
		}

		import CursorIterator.cursorUtils
		val points = closing(fetchRoutePoints(routeId)) { cursor =>
			cursor.map(c => RoutePoint(pointIndexToStopName.get(c.index), c.latitude, c.longitude)).toIndexedSeq
		}

		points :+ points.head
	}

	def close() { db.close() }

	def transaction[T](f: => T) {
		db.beginTransaction()
		try {
			val result = f
			db.setTransactionSuccessful()
			result
		} finally {
			db.endTransaction()
		}
	}
}