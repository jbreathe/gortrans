package net.kriomant.gortrans

import android.app.SearchManager
import android.content.{Context, Intent}
import android.os.{Build, Bundle}
import android.support.v7.widget
import android.view.{ActionMode, Menu, MenuItem, View}
import android.widget.AdapterView.OnItemClickListener
import android.widget._
import net.kriomant.gortrans.core.VehicleType

object MainActivity {
  val EXTRA_VEHICLE_TYPE = "vehicleType"

  def createIntent(caller: Context, vehicleType: VehicleType.Value = VehicleType.Bus): Intent = {
    val intent = new Intent(caller, classOf[MainActivity])
    intent.putExtra(EXTRA_VEHICLE_TYPE, vehicleType.id)
    intent
  }
}

class MainActivity extends MainActivityBase with HavingSidebar {
  override protected def onDrawerClosed() {
    if (tabsAreEmbedded) tabsView.setVisibility(View.VISIBLE)
  }

  override protected def onDrawerOpened() {
    if (tabsAreEmbedded) tabsView.setVisibility(View.INVISIBLE)
  }
}

class MainActivityBase extends RouteListBaseActivity with CreateGroupDialog.Listener {

  import MainActivity._

  override val layoutResource: Int = R.layout.main_activity
  var actionModeHelper: MultiListActionModeHelper = _

  override def onCreate(bundle: Bundle) {
    super.onCreate(bundle)

    val actionBar = getSupportActionBar
    actionBar.setDisplayHomeAsUpEnabled(true)

    actionModeHelper = new MultiListActionModeHelper(this, ContextActions)

    if (bundle != null) {
      // Restore index of currently selected tab.
      setSelectedTab(bundle.getInt("tabIndex"))
    } else {
      val vehicleType = VehicleType(getIntent.getIntExtra(EXTRA_VEHICLE_TYPE, VehicleType.Bus.id))
      setSelectedTab(tabsOrder.indexOf(vehicleType))
    }
  }

  override def onCreateOptionsMenu(menu: Menu): Boolean = {
    getMenuInflater.inflate(R.menu.route_list_menu, menu)

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
      val item = menu.findItem(R.id.search_route)
      val searchView = item.getActionView.asInstanceOf[widget.SearchView]
      val searchManager = getSystemService(Context.SEARCH_SERVICE).asInstanceOf[SearchManager]
      searchView.setSearchableInfo(searchManager.getSearchableInfo(getComponentName))
    }

    true
  }

  override def onOptionsItemSelected(item: MenuItem): Boolean = item.getItemId match {
    case R.id.search_route =>
      onSearchRequested()
      true

    case _ => false
  }

  override def onSaveInstanceState(outState: Bundle) {
    super.onSaveInstanceState(outState)
    // Save index of currently selected tab.
    outState.putInt("tabIndex", getSelectedTab)
  }

  /** This method is called by tab fragments when their views are created. */
  override def registerRoutesList(fragment: RoutesListFragment) {
    super.registerRoutesList(fragment)

    val listView = fragment.getListView
    val cursor = fragment.cursor

    listView.setOnItemClickListener(new OnItemClickListener {
      def onItemClick(parent: AdapterView[_], view: View, position: Int, id: Long) {
        cursor.moveToPosition(position)

        val intent = RouteInfoActivity.createIntent(MainActivityBase.this, cursor.externalId, cursor.name, cursor.vehicleType)
        startActivity(intent)
      }
    })

    actionModeHelper.attach(listView)
  }

  def onCreateGroup(dialog: CreateGroupDialog, groupId: Long) {
    actionModeHelper.finish()
  }

  object ContextActions extends ActionMode.Callback with ListSelectionActionModeCallback {
    def onCreateActionMode(mode: ActionMode, menu: Menu): Boolean = {
      val inflater = mode.getMenuInflater
      inflater.inflate(R.menu.route_list_actions, menu)
      true
    }

    def onPrepareActionMode(mode: ActionMode, menu: Menu): Boolean = {
      true
    }

    def onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean = item.getItemId match {
      case R.id.create_group =>
        val routeIds = actionModeHelper.getListViews.flatMap(_.getCheckedItemIds)
        val createGroupDialog = new CreateGroupDialog(routeIds.toSet)
        createGroupDialog.show(getSupportFragmentManager, "create_group_dialog")
        true
      case _ => false
    }

    def onDestroyActionMode(mode: ActionMode) {}

    def itemCheckedStateChanged(mode: ActionMode) {
      val count = actionModeHelper.getListViews.map(Compatibility.getCheckedItemCount).sum
      mode.setTitle(compatibility.plurals.getQuantityString(MainActivityBase.this, R.plurals.routes, count, count))
    }
  }

}

class ActionModeCallbackWrapper(callback: ActionMode.Callback) extends ActionMode.Callback {
  def onCreateActionMode(mode: ActionMode, menu: Menu): Boolean = callback.onCreateActionMode(mode, menu)

  def onPrepareActionMode(mode: ActionMode, menu: Menu): Boolean = callback.onPrepareActionMode(mode, menu)

  def onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean = callback.onActionItemClicked(mode, item)

  def onDestroyActionMode(mode: ActionMode) {
    callback.onDestroyActionMode(mode)
  }
}

