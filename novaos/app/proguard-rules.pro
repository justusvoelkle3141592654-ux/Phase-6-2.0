# kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.json.** { kotlinx.serialization.KSerializer serializer(...); }
-keep,includedescriptorclasses class app.novaos.**$$serializer { *; }
-keepclassmembers class app.novaos.** { *** Companion; }
-keepclasseswithmembers class app.novaos.** { kotlinx.serialization.KSerializer serializer(...); }
-keep class app.novaos.model.** { *; }
-keep class app.novaos.data.WeatherCache { *; }
-keep class app.novaos.data.HourForecast { *; }
-keep class app.novaos.data.DayForecast { *; }
# Dienste und Empfänger, die vom System gestartet werden
-keep class app.novaos.system.NovaNotificationService
-keep class app.novaos.system.NovaDeviceAdmin
-keep class app.novaos.system.ReminderReceiver
