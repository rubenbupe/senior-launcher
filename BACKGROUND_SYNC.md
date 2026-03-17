# Sincronización en Segundo Plano

## Resumen de Cambios

Se ha implementado un servicio en primer plano (Foreground Service) que permite a Senior Launcher mantener la conexión WebSocket con el servidor backend activa **incluso cuando**:

- La app está en segundo plano
- La pantalla del dispositivo está bloqueada
- El dispositivo está en modo de bajo consumo

**Optimización de batería:** El servicio **NO utiliza WakeLock permanente**. Android mantiene automáticamente activos los Foreground Services, por lo que no se requiere consumo adicional de batería para mantener la CPU despierta.

## Componentes Nuevos

### 1. `SyncForegroundService.kt`
Servicio principal que:
- Mantiene una conexión WebSocket persistente con el servidor
- Utiliza un WakeLock parcial para mantener la CPU activa
- Muestra una notificación persistente (requerido por Android para foreground services)
- Se reconecta automáticamente si se pierde la conexión
- Envía el estado del dispositivo periódicamente

### 2. `SyncUtils.kt`
Funciones de utilidad compartidas:
- `toWebSocketBaseUrl()`: Convierte URLs HTTP/HTTPS a WS/WSS
- `buildDeviceInfoJson()`: Construye información del dispositivo (batería, versión, etc.)
- `getAvailableApps()`: Obtiene lista de apps instaladas

### 3. `BootReceiver.kt`
BroadcastReceiver que:
- Inicia automáticamente el servicio al arrancar el dispositivo
- Solo se activa si la sincronización está habilitada en preferencias

## Permisos Nuevos

Se agregaron los siguientes permisos al `AndroidManifest.xml`:

```xml
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_DATA_SYNC" />
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
<uses-permission android:name="android.permission.RECEIVE_BOOT_COMPLETED" />
```

### Explicación de permisos:
- **FOREGROUND_SERVICE**: Permite ejecutar el servicio en primer plano
- **FOREGROUND_SERVICE_DATA_SYNC**: Tipo específico para sincronización de datos (API 34+)
- **POST_NOTIFICATIONS**: Mostrar notificación persistente (API 33+)
- **RECEIVE_BOOT_COMPLETED**: Iniciar servicio al arrancar el dispositivo

## Funcionamiento

### Inicio Automático
El servicio se inicia automáticamente cuando:
1. El usuario habilita la sincronización en Configuración
2. El dispositivo se reinicia (si la sincronización está habilitada)

### Notificación Persistente
El usuario verá una notificación con el estado de la conexión:
- "Conectando al servidor..." - Al iniciar
- "Conectado y sincronizando" - Cuando está activo
- "Desconectado, reconectando..." - Si pierde conexión
- "Sincronización pausada" - Si está deshabilitada

### Detención
El servicio se detiene cuando:
1. El usuario deshabilita la sincronización en Configuración
2. El usuario fuerza el cierre desde Configuración del sistema
3. El sistema Android lo termina por falta de recursos (START_STICKY lo reiniciará)

## Consumo de Recursos

### CPU y Procesamiento
- Android mantiene automáticamente activos los Foreground Services
- **No se utiliza WakeLock**, el sistema gestiona el ciclo de vida del servicio
- Solo consume CPU durante transmisión de datos WebSocket
- OkHttpClient reutilizable (instanciado una sola vez)

### Batería
El impacto en batería es mínimo:
- La conexión WebSocket usa pings cada 20 segundos
- El envío de estado es cada 60 segundos
- No mantiene la pantalla encendida
- **Consumo estimado:** Similar a WhatsApp Web en segundo plano (~1-3% por hora)

### Red
- Uso de red constante pero mínimo (pocos KB por minuto)
- Compatible con conexiones móviles y WiFi

## Pruebas Recomendadas

1. **Segundo Plano**:
   - Activar sincronización
   - Presionar "Home" y usar otras apps
   - Verificar que sigue conectado en el panel web

2. **Pantalla Bloqueada**:
   - Activar sincronización
   - Bloquear el dispositivo
   - Enviar una acción remota desde el panel web
   - Desbloquear y verificar que se ejecutó

3. **Reinicio**:
   - Activar sincronización
   - Reiniciar el dispositivo
   - Verificar que el servicio se inicia automáticamentey aparece la notificación

4. **Reconexión**:
   - Detener el servidor backend
   - La notificación debe mostrar "reconectando..."
   - Reiniciar el servidor
   - Debe reconectarse automáticamente

## Limitaciones Conocidas

1. **Datos SMS completos**: El servicio envía datos SMS simplificados. Para datos completos, la app debe estar abierta.

2. **Acciones complejas**: Algunas acciones como crear contactos o gestionar SMS requieren que la app esté en primer plano para gestionar permisos Android.

3. **Doze Mode**: En Android 6+, el sistema puede retrasar el servicio en modo "Doze". El servicio se recupera automáticamente al salir de este modo.

4. **Optimización de batería**: Si el usuario agrega la app a la lista de "optimización de batería", el sistema podría terminar el servicio más frecuentemente.

## Solución de Problemas

### El servicio no se inicia
- Verificar que la sincronización está habilitada
- Verificar que servidor URL y Device ID están configurados
- Revisar logs con: `adb logcat -s SyncForegroundService`

### El servicio se detiene constantemente
- Desactivar optimización de batería para Senior Launcher
- En Configuración > Apps > Senior Launcher > Batería > "Sin restricción"

### No se ejecutan acciones remotas
- Verificar que la app tiene los permisos necesarios
- Algunas acciones requieren que la app esté abierta

## Logs de Depuración

```bash
# Ver logs del servicio
adb logcat -s SyncForegroundService

# Ver logs del boot receiver
adb logcat -s BootReceiver

# Ver logs generales de sincronización
adb logcat -s BackendSync
```

## Cambios en MainActivity

La lógica de conexión WebSocket se movió completamente al servicio. MainActivity ahora solo:
- Inicia el servicio cuando se habilita la sincronización
- Detiene el servicio cuando se deshabilita
- No gestiona directamente la conexión WebSocket

Esto mejora la arquitectura y permite que la conexión persista independientemente del ciclo de vida de la Activity.
