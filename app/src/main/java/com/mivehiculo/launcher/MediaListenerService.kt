package com.mivehiculo.launcher

import android.service.notification.NotificationListenerService

/**
 * Servicio vacío a propósito. Su único trabajo es existir: Android solo deja leer las sesiones
 * de medios de otras apps a quien tenga activado "Acceso a notificaciones", y ese permiso
 * se concede a un NotificationListenerService.
 */
class MediaListenerService : NotificationListenerService()
