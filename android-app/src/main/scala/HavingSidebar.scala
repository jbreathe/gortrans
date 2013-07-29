package net.kriomant.gortrans

import android.os.Bundle
import android.view.{ActionProvider, View, ViewGroup}
import com.actionbarsherlock.app.SherlockFragmentActivity
import com.actionbarsherlock.view.{Menu, MenuItem}
import android.util.TypedValue
import net.kriomant.gortrans.Sidebar.SidebarListener
import android.content.Intent
import android.util.Log
import android.support.v4.app.ActionBarDrawerToggle
import android.support.v4.widget.DrawerLayout
import android.content.res.Configuration
import android.view
import android.graphics.drawable.Drawable
import android.view.MenuItem.{OnActionExpandListener, OnMenuItemClickListener}
import android.support.v4.widget.DrawerLayout.DrawerListener
import android.support.v4.view.GravityCompat

trait HavingSidebar extends SherlockFragmentActivity with TypedActivity {
	var drawerLayout: DrawerLayout = null
	var drawer: View = null
	var drawerToggle: ActionBarDrawerToggle = null

	override def onCreate(savedInstanceState: Bundle) {
		super.onCreate(savedInstanceState)
	}

	override def onPrepareOptionsMenu(menu: Menu) = {
		super.onPrepareOptionsMenu(menu)
		val drawerOpen = drawerLayout.isDrawerOpen(drawer)
		for (i <- 0 until menu.size) menu.getItem(i).setVisible(!drawerOpen)
		true
	}

	protected override def onPostCreate(savedInstanceState: Bundle) {
		super.onPostCreate(savedInstanceState)

		val title = getTitle

		val actionBar = getSupportActionBar
		actionBar.setDisplayHomeAsUpEnabled(true)
		actionBar.setHomeButtonEnabled(true)

		drawerLayout = findView(TR.drawer_layout)
		drawerLayout.setDrawerShadow(R.drawable.drawer_shadow, GravityCompat.START)

		drawerToggle = new ActionBarDrawerToggle(this, drawerLayout, R.drawable.ic_drawer, R.string.open_drawer, R.string.close_drawer) {
			override def onDrawerOpened(drawerView: View) {
				actionBar.setTitle(R.string.app_name)
				supportInvalidateOptionsMenu()
			}

			override def onDrawerClosed(drawerView: View) {
				actionBar.setTitle(title)
				supportInvalidateOptionsMenu()
			}
		}
		drawerLayout.setDrawerListener(drawerToggle)

		drawer = findView(TR.navigation_drawer)
		val sidebar = new Sidebar(this, drawer, new SidebarListener {
			def onItemSelected(intent: Intent) {
				drawerLayout.setDrawerListener(new DrawerListener {
					def onDrawerSlide(p1: View, p2: Float) {}
					def onDrawerOpened(p1: View) {}
					def onDrawerStateChanged(p1: Int) {}

					def onDrawerClosed(p1: View) {
						if (! intent.filterEquals(getIntent)) {
							startActivity(intent)
							finish()
							overridePendingTransition(0, 0)
						}
					}
				})
				drawerLayout.closeDrawer(drawer)
			}
		})
	}

	override def onConfigurationChanged(newConfig: Configuration) {
		super.onConfigurationChanged(newConfig)
		drawerToggle.onConfigurationChanged(newConfig)
	}

	override def onOptionsItemSelected(item: MenuItem) = item.getItemId match {
		case android.R.id.home =>
			if (drawerLayout.isDrawerOpen(drawer))
				drawerLayout.closeDrawer(drawer)
			else
				drawerLayout.openDrawer(drawer)
			true

		case _ => super.onOptionsItemSelected(item)
	}
}