package net.kriomant.gortrans

import java.util.Calendar

import android.content.{Context, Intent}
import android.os.Bundle
import android.support.v4.content.ContextCompat
import android.support.v4.view.{PagerAdapter, ViewPager}
import android.support.v7.app.AppCompatActivity
import android.text.style.{CharacterStyle, ForegroundColorSpan}
import android.text.{SpannableString, SpannableStringBuilder, Spanned}
import android.util.Log
import android.view.{MenuItem, _}
import android.widget.{ListView, TextView}
import net.kriomant.gortrans.CursorIterator.cursorUtils
import net.kriomant.gortrans.core.{Direction, VehicleType}

object StopScheduleActivity {
  private[this] val CLASS_NAME = classOf[RouteInfoActivity].getName
  private final val EXTRA_ROUTE_ID = CLASS_NAME + ".ROUTE_ID"
  private final val EXTRA_ROUTE_NAME = CLASS_NAME + ".ROUTE_NAME"
  private final val EXTRA_VEHICLE_TYPE = CLASS_NAME + ".VEHICLE_TYPE"
  private final val EXTRA_STOP_ID = CLASS_NAME + ".STOP_ID"
  private final val EXTRA_STOP_NAME = CLASS_NAME + ".STOP_NAME"
  private final val EXTRA_DIRECTION = CLASS_NAME + ".DIRECTION"

  def createIntent(
                    caller: Context, routeId: String, routeName: String, vehicleType: VehicleType.Value,
                    stopId: Int, stopName: String, direction: Direction.Value
                  ): Intent = {
    val intent = new Intent(caller, classOf[StopScheduleActivity])
    intent.putExtra(EXTRA_ROUTE_ID, routeId)
    intent.putExtra(EXTRA_ROUTE_NAME, routeName)
    intent.putExtra(EXTRA_VEHICLE_TYPE, vehicleType.id)
    intent.putExtra(EXTRA_STOP_ID, stopId)
    intent.putExtra(EXTRA_STOP_NAME, stopName)
    intent.putExtra(EXTRA_DIRECTION, direction.id)
    intent
  }
}

class StopScheduleActivity extends AppCompatActivity with BaseActivity with ShortcutTarget {

  import StopScheduleActivity._

  private var routeId: String = _
  private var routeName: String = _
  private var vehicleType: VehicleType.Value = _
  private var stopId: Int = -1
  private var stopName: String = _
  private var direction: Direction.Value = _

  override def onCreate(bundle: Bundle) {
    super.onCreate(bundle)

    setContentView(R.layout.stop_schedule_activity)

    val intent = getIntent
    routeId = intent.getStringExtra(EXTRA_ROUTE_ID)
    routeName = intent.getStringExtra(EXTRA_ROUTE_NAME)
    vehicleType = VehicleType(intent.getIntExtra(EXTRA_VEHICLE_TYPE, -1))
    stopId = intent.getIntExtra(EXTRA_STOP_ID, -1)
    stopName = intent.getStringExtra(EXTRA_STOP_NAME)
    direction = Direction(intent.getIntExtra(EXTRA_DIRECTION, -1))

    val actionBar = getSupportActionBar
    actionBar.setTitle(RouteListBaseActivity.getRouteTitle(this, vehicleType, routeName))
    actionBar.setSubtitle(stopName)
    actionBar.setDisplayHomeAsUpEnabled(true)
  }

  override def onStart() {
    super.onStart()
    loadData()
  }

  def loadData() {
    val dataManager = getApplication.asInstanceOf[CustomApplication].dataManager

    dataManager.requestStopSchedules(
      vehicleType, routeId, stopId, direction,
      new ForegroundProcessIndicator(this, loadData),
      new ActionBarProcessIndicator(this)
    ) {
      val database = getApplication.asInstanceOf[CustomApplication].database

      val dbRouteId = database.findRoute(vehicleType, routeId)
      val cursor = database.fetchSchedules(dbRouteId, stopId, direction)

      val schedulesMap = cursor.map { c =>
        c.scheduleType -> ((c.scheduleName, c.schedule.groupBy(_._1).mapValues(_.map(_._2)).toSeq.sortBy(_._1)))
      }.toMap

      if (schedulesMap.nonEmpty) {
        // Schedules are presented as map, it is needed to order them somehow.
        // I assume 'keys' and 'values' traverse items in the same order.
        val schedules = schedulesMap.values.toSeq
        val typeToIndex = schedulesMap.keys.zipWithIndex.toMap

        // Display schedule.
        val viewPager = findViewById(R.id.schedule_tabs).asInstanceOf[ViewPager]
        viewPager.setAdapter(new SchedulePagesAdapter(StopScheduleActivity.this, schedules))

        // Select page corresponding to current day of week.
        val dayOfWeek = Calendar.getInstance.get(Calendar.DAY_OF_WEEK)
        val optIndex = (dayOfWeek match {
          case Calendar.SATURDAY | Calendar.SUNDAY => typeToIndex.get(core.ScheduleType.Holidays)
          case _ => typeToIndex.get(core.ScheduleType.Workdays)
        }).orElse(typeToIndex.get(core.ScheduleType.Daily))

        optIndex foreach { index => viewPager.setCurrentItem(index) }

        viewPager.setVisibility(View.VISIBLE)

      } else {
        findViewById(R.id.no_schedules).asInstanceOf[View].setVisibility(View.VISIBLE)
      }
    }
  }

  override def onOptionsItemSelected(item: MenuItem): Boolean = item.getItemId match {
    case android.R.id.home =>
      val intent = RouteStopInfoActivity.createIntent(this, routeId, routeName, vehicleType, stopId, stopName)
      startActivity(intent)
      true
    case _ => super.onOptionsItemSelected(item)
  }

  def getShortcutNameAndIcon: (String, Int) = {
    val vehicleShortName = getString(vehicleType match {
      case VehicleType.Bus => R.string.bus_short
      case VehicleType.TrolleyBus => R.string.trolleybus_short
      case VehicleType.TramWay => R.string.tramway_short
      case VehicleType.MiniBus => R.string.minibus_short
    })
    val name = getString(R.string.stop_schedule_shortcut_format, vehicleShortName, routeName, stopName)
    (name, R.drawable.route_stop_schedule)
  }

  class SchedulePagesAdapter(context: Context, schedules: Seq[(String, Seq[(Int, Seq[Int])])]) extends PagerAdapter {
    def getCount: Int = schedules.length

    override def getPageTitle(position: Int): CharSequence = schedules(position)._1

    override def instantiateItem(container: ViewGroup, position: Int): AnyRef = {
      val inflater = context.getSystemService(Context.LAYOUT_INFLATER_SERVICE).asInstanceOf[LayoutInflater]
      val view = inflater.inflate(R.layout.stop_schedule_tab, container, false).asInstanceOf[ListView]
      container.addView(view)

      val stopSchedule = schedules(position)._2
      Log.d("StopScheduleActivity", stopSchedule.length.toString)

      val calendar = Calendar.getInstance()
      val currentHour = calendar.get(Calendar.HOUR_OF_DAY)
      val currentMinute = calendar.get(Calendar.MINUTE)

      def formatTimes(hour: Int, minutes: Seq[Int]) = {
        val hourStr = new SpannableString(hour.toString)
        val minBuilder = new SpannableStringBuilder

        if (hour < currentHour) {
          val span = new ForegroundColorSpan(ContextCompat.getColor(context, R.color.schedule_past))

          hourStr.setSpan(CharacterStyle.wrap(span), 0, hourStr.length, Spanned.SPAN_INCLUSIVE_EXCLUSIVE)

          minBuilder.append(minutes.mkString(" "))
          minBuilder.setSpan(span, 0, minBuilder.length, Spanned.SPAN_INCLUSIVE_EXCLUSIVE)

        } else if (hour > currentHour) {
          val span = new ForegroundColorSpan(ContextCompat.getColor(context, R.color.schedule_future))

          hourStr.setSpan(CharacterStyle.wrap(span), 0, hourStr.length, Spanned.SPAN_INCLUSIVE_EXCLUSIVE)

          minBuilder.append(minutes.mkString(" "))
          minBuilder.setSpan(span, 0, minBuilder.length, Spanned.SPAN_INCLUSIVE_EXCLUSIVE)

        } else {
          val (before, after) = minutes.span(_ < currentMinute)

          val spanPast = new ForegroundColorSpan(ContextCompat.getColor(context, R.color.schedule_past))
          val spanFuture = new ForegroundColorSpan(ContextCompat.getColor(context, R.color.schedule_future))

          hourStr.setSpan(CharacterStyle.wrap(spanFuture), 0, hourStr.length, Spanned.SPAN_INCLUSIVE_EXCLUSIVE)

          minBuilder.append(before.mkString(" "))
          minBuilder.setSpan(spanPast, 0, minBuilder.length, Spanned.SPAN_INCLUSIVE_EXCLUSIVE)

          minBuilder.append(" ")

          val mark = minBuilder.length
          minBuilder.append(after.mkString(" "))
          minBuilder.setSpan(spanFuture, mark, minBuilder.length, Spanned.SPAN_INCLUSIVE_EXCLUSIVE)
        }

        (hourStr, minBuilder)
      }

      val adapter: SeqAdapter with EasyAdapter = new SeqAdapter with EasyAdapter {

        case class SubViews(hour: TextView, minutes: TextView)

        val context: Context = StopScheduleActivity.this
        val itemLayout: Int = R.layout.stop_schedule_item

        def items: Seq[(Int, Seq[Int])] = stopSchedule

        def findSubViews(view: View): SubViews = SubViews(
          view.findViewById(R.id.hour).asInstanceOf[TextView],
          view.findViewById(R.id.minutes).asInstanceOf[TextView]
        )

        def adjustItem(position: Int, views: SubViews) {
          val (hour, minutes) = items(position)
          val (hourText, minutesText) = formatTimes(hour, minutes)
          views.hour.setText(hourText)
          views.minutes.setText(minutesText)
        }
      }
      view.setAdapter(adapter)

      view
    }

    override def destroyItem(container: ViewGroup, position: Int, `object`: AnyRef) {
      container.removeView(`object`.asInstanceOf[View])
    }

    override def setPrimaryItem(container: ViewGroup, position: Int, `object`: AnyRef) {}

    def isViewFromObject(p1: View, p2: AnyRef): Boolean = p1 == p2.asInstanceOf[View]
  }

}

