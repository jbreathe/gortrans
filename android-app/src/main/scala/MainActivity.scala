package net.kriomant.gortrans

import _root_.android.os.Bundle

import net.kriomant.gortrans.core.VehicleType
import android.widget._
import com.actionbarsherlock.app.ActionBar.{Tab, TabListener}
import android.support.v4.app.{FragmentPagerAdapter, ListFragment, FragmentTransaction}
import android.view.{ViewGroup, LayoutInflater, View}
import com.actionbarsherlock.app.{SherlockFragmentActivity, ActionBar}
import com.actionbarsherlock.view.Window
import android.content.{DialogInterface, Context, Intent}
import net.kriomant.gortrans.DataManager.ProcessIndicator
import android.app.{AlertDialog, ProgressDialog}
import android.util.Log
import android.support.v4.view.ViewPager.OnPageChangeListener

object MainActivity {
	val EXTRA_VEHICLE_TYPE = "vehicleType"

	def createIntent(caller: Context, vehicleType: VehicleType.Value = VehicleType.Bus): Intent = {
		val intent = new Intent(caller, classOf[MainActivity])
		intent.putExtra(EXTRA_VEHICLE_TYPE, vehicleType.id)
		intent
	}
}

class MainActivity extends SherlockFragmentActivity with TypedActivity {
	import MainActivity._

	private[this] final val TAG = "MainActivity"

	var tabFragmentsMap: Map[VehicleType.Value, RoutesListFragment] = null

  override def onCreate(bundle: Bundle) {
    super.onCreate(bundle)

	  setContentView(R.layout.main_activity)

	  val actionBar = getSupportActionBar
	  actionBar.setNavigationMode(ActionBar.NAVIGATION_MODE_TABS)
	  actionBar.setDisplayShowTitleEnabled(false)
	  actionBar.setDisplayShowHomeEnabled(false)

	  val vehicleTypeDrawables = Map(
		  VehicleType.Bus -> R.drawable.tab_bus,
		  VehicleType.TrolleyBus -> R.drawable.tab_trolleybus,
		  VehicleType.TramWay -> R.drawable.tab_tram,
		  VehicleType.MiniBus -> R.drawable.tab_minibus
	  ).mapValues(getResources.getDrawable(_))

	  val tabPager = findView(TR.tab_pager)

	  // Fix tabs order.
	  val tabsOrder = Seq(VehicleType.Bus, VehicleType.TrolleyBus, VehicleType.TramWay, VehicleType.MiniBus)
	  val tabFragments = Seq.fill(tabsOrder.size) { new RoutesListFragment }
	  tabFragmentsMap = tabsOrder.zip(tabFragments).toMap

	  tabsOrder.zipWithIndex foreach { case (vehicleType, i) =>
		  val fragment = tabFragments(i)
		  val icon = vehicleTypeDrawables(vehicleType)

			val tab = actionBar.newTab
				.setIcon(icon)
				.setTabListener(new TabListener {
					def onTabSelected(tab: Tab, ft: FragmentTransaction) {
						tabPager.setCurrentItem(i)
					}
					def onTabReselected(tab: Tab, ft: FragmentTransaction) {}
					def onTabUnselected(tab: Tab, ft: FragmentTransaction) {}
				})

			actionBar.addTab(tab)
		}

	  tabPager.setAdapter(new FragmentPagerAdapter(getSupportFragmentManager) {
		  def getCount = tabFragments.size
		  def getItem(pos: Int) = tabFragments(pos)
	  })
	  tabPager.setOnPageChangeListener(new OnPageChangeListener {
		  def onPageScrolled(p1: Int, p2: Float, p3: Int) {}
		  def onPageScrollStateChanged(p1: Int) {}

		  def onPageSelected(pos: Int) {
			  actionBar.setSelectedNavigationItem(pos)
		  }
	  })

	  if (bundle != null) {
		  // Restore index of currently selected tab.
		  actionBar.setSelectedNavigationItem(bundle.getInt("tabIndex"))
	  } else {
		  val vehicleType = VehicleType(getIntent.getIntExtra(EXTRA_VEHICLE_TYPE, VehicleType.Bus.id))
		  actionBar.setSelectedNavigationItem(tabsOrder.indexOf(vehicleType))
	  }
  }

	override def onStart() {
		super.onStart()

		loadRoutes()
	}

	def updateRoutesList(routes: parsing.RoutesInfo) {
		val vehicleTypeNames = Map(
			VehicleType.Bus -> R.string.bus,
			VehicleType.TrolleyBus -> R.string.trolleybus,
			VehicleType.TramWay -> R.string.tramway,
			VehicleType.MiniBus -> R.string.minibus
		).mapValues(getString)

		routes foreach {case (vehicleType, routesList) =>
			val fragment = tabFragmentsMap(vehicleType)
			fragment.setRoutes(routesList)
		}
	}

	def loadRoutes() {
		val dataManager = getApplication.asInstanceOf[CustomApplication].dataManager
		val progressBar = findView(TR.progress_bar)

		val foregroundProcessIndicator = new ForegroundProcessIndicator(this, loadRoutes)
		val backgroundProcessIndicator = new ProcessIndicator {
			def startFetch() {
				Toast.makeText(MainActivity.this, R.string.background_update_started, Toast.LENGTH_SHORT).show()
				progressBar.setVisibility(View.VISIBLE)
			}

			def stopFetch() {
				progressBar.setVisibility(View.INVISIBLE)
			}

			def onSuccess() {
				Toast.makeText(MainActivity.this, R.string.background_update_stopped, Toast.LENGTH_SHORT).show()
			}

			def onError() {
				Toast.makeText(MainActivity.this, R.string.background_update_error, Toast.LENGTH_SHORT).show()
			}
		}

		dataManager.requestRoutesList(foregroundProcessIndicator, backgroundProcessIndicator)(updateRoutesList)
	}

	override def onSaveInstanceState(outState: Bundle) {
		// Save index of currently selected tab.
		outState.putInt("tabIndex", getSupportActionBar.getSelectedNavigationIndex)
	}
}

class RoutesListFragment extends ListFragment {
	var routes: Seq[core.Route] = Seq()

	override def onCreateView(inflater: LayoutInflater, container: ViewGroup, savedInstanceState: Bundle) = {
		inflater.inflate(R.layout.routes_list_tab, container, false)
	}

	override def onCreate(savedInstanceState: Bundle) {
		super.onCreate(savedInstanceState)

		val listAdapter = new SeqAdapter with ListAdapter with EasyAdapter {
			val context = getActivity
			def items = routes
			val itemLayout = R.layout.routes_list_item
			case class SubViews(number: TextView, begin: TextView, end: TextView)

			def findSubViews(view: View) = SubViews(
				view.findViewById(R.id.route_name).asInstanceOf[TextView],
				view.findViewById(R.id.start_stop_name).asInstanceOf[TextView],
				view.findViewById(R.id.end_stop_name).asInstanceOf[TextView]
			)

			def adjustItem(position: Int, views: SubViews) {
				val route = routes(position)
				views.number.setText(route.name)
				views.begin.setText(route.begin)
				views.end.setText(route.end)
			}
		}

		setListAdapter(listAdapter)

		if (savedInstanceState != null) {
			getListView.onRestoreInstanceState(savedInstanceState.getParcelable("list"))
		}
	}

	def setRoutes(newRoutes: Seq[core.Route]) {
		routes = newRoutes
		val adapter = getListAdapter.asInstanceOf[BaseAdapter]
		if (adapter != null)
			adapter.notifyDataSetChanged()
	}

	override def onListItemClick(l: ListView, v: View, position: Int, id: Long) {
		val route = routes(position)

		val intent = RouteInfoActivity.createIntent(getActivity, route.id, route.name, route.vehicleType)
		startActivity(intent)
	}

	override def onSaveInstanceState(outState: Bundle) {
		super.onSaveInstanceState(outState)
		outState.putParcelable("list", getListView.onSaveInstanceState())
	}
}
