private fun startOdometerTracking() {
    stopOdometerTracking()
    odometerListener = LocationHelper.startOdometerTracking(this) { totalMeters, _ ->
        odometerMeters = totalMeters
    }
}

private fun stopOdometerTracking() {
    LocationHelper.stopOdometerTracking(this, odometerListener)
    odometerListener = null
}

private fun hasLocationPermission(): Boolean =
    ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

private fun formatOdometerKm(meters: Float): String =
    String.format(Locale.US, "%,.0f", meters / 1000f)

private fun openRearCamera() {
    val saved = getSharedPreferences("launcher", MODE_PRIVATE).getString("camera_pkg", null)
    if (!Apps.launchAny(this, Config.CAMARA + listOfNotNull(saved))) pickRearCamera()
}

private fun pickRearCamera() {
    pickTarget = "camera_pkg"
    toast("Toca la app de cámara trasera / AV-IN de tu radio")
    showApps()
}

private fun openRadioApp() {
    val saved = Apps.savedPackage(this, "radio_pkg")
    if (!Apps.launchAny(this, listOfNotNull(saved) + Config.RADIO)) pickRadioApp()
}

private fun pickRadioApp() {
    pickTarget = "radio_pkg"
    toast("Toca la app de Radio FM de tu radio")
    showApps()
}

private fun savePickedApp(key: String, pkg: String) {
    try {
        getSharedPreferences("launcher", MODE_PRIVATE).edit().putString(key, pkg).apply()
    } catch (_: Exception) {
        // No-op: if it cannot be saved, the user will be prompted again later.
    }
}

private fun showApps() {
    if (loadingApps) return
    loadingApps = true
    Thread {
        val list = try {
            Apps.installed(this)
        } catch (_: Exception) {
            emptyList()
        }
        runOnUiThread {
            drawerApps = list
            loadingApps = false
        }
    }.start()
}

private fun open(nombre: String, opened: Boolean) {
    if (!opened) toast("$nombre: app no encontrada (ver Config.kt)")
}

private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
