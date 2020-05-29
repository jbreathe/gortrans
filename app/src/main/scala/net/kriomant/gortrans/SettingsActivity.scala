package net.kriomant.gortrans

import android.app.ActivityManager
import android.content.{Context, Intent}
import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.support.v7.preference.PreferenceFragmentCompat
import android.view.MenuItem
import com.google.android.gms.common.{ConnectionResult, GooglePlayServicesUtil}

object SettingsActivity {
  def createIntent(caller: Context): Intent = {
    new Intent(caller, classOf[SettingsActivity])
  }

  def isNewGMapsAvailable(context: Context): Boolean = {
    val openGlEs2Available = {
      val manager = context.getSystemService(Context.ACTIVITY_SERVICE).asInstanceOf[ActivityManager]
      manager.getDeviceConfigurationInfo.reqGlEsVersion >= 0x20000
    }

    def googlePlayServicesAvailable = {
      val status = GooglePlayServicesUtil.isGooglePlayServicesAvailable(context)
      status == ConnectionResult.SUCCESS || GooglePlayServicesUtil.isUserRecoverableError(status)
    }

    openGlEs2Available && googlePlayServicesAvailable
  }
}

class SettingsActivity extends AppCompatActivity with BaseActivity {
  override def onCreate(savedInstanceState: Bundle) {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.settings_activity)

    val actionBar = getSupportActionBar
    actionBar.setDisplayHomeAsUpEnabled(true)
  }

  override def onOptionsItemSelected(item: MenuItem): Boolean = item.getItemId match {
    case android.R.id.home =>
      finish()
      true

    case _ => super.onOptionsItemSelected(item)
  }
}

class SettingsFragment extends PreferenceFragmentCompat {
  override def onCreatePreferences(bundle: Bundle, s: String): Unit = {
    addPreferencesFromResource(R.xml.preferences)
  }
}
