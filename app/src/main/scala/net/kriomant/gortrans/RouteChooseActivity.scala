package net.kriomant.gortrans

import android.app.Activity
import android.content.{Context, Intent}
import android.os.Bundle
import android.view.{Menu, MenuItem, View}
import android.widget.AdapterView.OnItemClickListener
import android.widget.{AbsListView, AdapterView, ListView}

import scala.collection.mutable

object RouteChooseActivity {
  val EXTRA_ROUTE_IDS = "route-ids"

  def createIntent(caller: Context, selectedRoutes: Set[Long] = Set.empty): Intent = {
    val intent = new Intent(caller, classOf[RouteChooseActivity])
    intent.putExtra(EXTRA_ROUTE_IDS, selectedRoutes.toArray)
    intent
  }

  def intentToResult(intent: Intent): Set[Long] = {
    intent.getLongArrayExtra(EXTRA_ROUTE_IDS).toSet
  }

  private def resultToIntent(routeIds: collection.Set[Long]) = {
    val intent = new Intent
    intent.putExtra(EXTRA_ROUTE_IDS, routeIds.toArray)
    intent
  }
}

class RouteChooseActivity extends RouteListBaseActivity with BaseActivity {

  import RouteChooseActivity._

  val listViews: mutable.Buffer[ListView] = mutable.Buffer[ListView]()
  var routeIds = mutable.Set.empty[Long]
  var optionsMenu: Menu = _

  override def onCreate(bundle: Bundle) {
    super.onCreate(bundle)

    val actionBar = getSupportActionBar
    actionBar.setDisplayHomeAsUpEnabled(true)

    val ids = getIntent.getLongArrayExtra(EXTRA_ROUTE_IDS)
    if (ids != null) {
      routeIds ++= ids
    }

    updateControls()
  }

  override def onCreateOptionsMenu(menu: Menu): Boolean = {
    super.onCreateOptionsMenu(menu)
    getMenuInflater.inflate(R.menu.route_choose_menu, menu)
    this.optionsMenu = menu
    true
  }

  override def onOptionsItemSelected(item: MenuItem): Boolean = item.getItemId match {
    case R.id.accept  =>
      setResult(Activity.RESULT_OK, resultToIntent(routeIds))
      finish()
      true
    case android.R.id.home =>
      finish()
      true
    case _ => super.onOptionsItemSelected(item)
  }

  /** This method is called by tab fragments when their views are created. */
  override def registerRoutesList(fragment: RoutesListFragment) {
    super.registerRoutesList(fragment)

    val listView = fragment.getListView
    listViews += listView
    listView.setChoiceMode(AbsListView.CHOICE_MODE_MULTIPLE)

    if (routeIds.nonEmpty) {
      val adapter = listView.getAdapter
      for (i <- 0 until adapter.getCount) {
        val id = adapter.getItemId(i)
        if (routeIds contains id)
          listView.setItemChecked(i, true)
      }
    }

    listView.setOnItemClickListener(new OnItemClickListener {
      def onItemClick(parent: AdapterView[_], view: View, position: Int, id: Long) {
        if (listView.isItemChecked(position))
          routeIds += id
        else
          routeIds -= id

        updateControls()
      }
    })
  }

  private def updateControls() {
    val actionBar = getSupportActionBar

    routeIds.size match {
      case 0 =>
        actionBar.setTitle(R.string.choose_routes)
      case count =>
        actionBar.setTitle(compatibility.plurals.getQuantityString(RouteChooseActivity.this, R.plurals.routes, count, count))
    }

    if (optionsMenu != null) {
      val menuItem = optionsMenu.findItem(R.id.accept)
      menuItem.setEnabled(routeIds.nonEmpty)
    }
  }
}
