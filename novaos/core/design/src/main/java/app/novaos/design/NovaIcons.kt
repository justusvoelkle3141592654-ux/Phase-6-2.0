package app.novaos.design

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.filled.Message
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Coffee
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Eco
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Flight
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Pets
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Photo
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material.icons.filled.Whatshot
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.Work
import androidx.compose.ui.graphics.vector.ImageVector

/** Symbolnamen aus [app.novaos.model.CreatorIcons] → Vektorgrafik. */
object NovaIcons {
    private val map: Map<String, ImageVector> = mapOf(
        "star" to Icons.Filled.Star, "heart" to Icons.Filled.Favorite, "bolt" to Icons.Filled.Bolt, "home" to Icons.Filled.Home,
        "work" to Icons.Filled.Work, "music" to Icons.Filled.MusicNote, "phone" to Icons.Filled.Phone, "message" to Icons.AutoMirrored.Filled.Message,
        "mail" to Icons.Filled.Email, "camera" to Icons.Filled.CameraAlt, "calendar" to Icons.Filled.CalendarMonth, "clock" to Icons.Filled.Schedule,
        "alarm" to Icons.Filled.Alarm, "battery" to Icons.Filled.BatteryFull, "wifi" to Icons.Filled.Wifi, "bluetooth" to Icons.Filled.Bluetooth,
        "cloud" to Icons.Filled.Cloud, "sun" to Icons.Filled.WbSunny, "moon" to Icons.Filled.DarkMode, "fire" to Icons.Filled.Whatshot,
        "map" to Icons.Filled.Map, "navigation" to Icons.Filled.Navigation, "game" to Icons.Filled.SportsEsports, "book" to Icons.AutoMirrored.Filled.MenuBook,
        "code" to Icons.Filled.Code, "terminal" to Icons.Filled.Terminal, "settings" to Icons.Filled.Settings, "search" to Icons.Filled.Search,
        "person" to Icons.Filled.Person, "group" to Icons.Filled.Group, "shopping" to Icons.Filled.ShoppingCart, "coffee" to Icons.Filled.Coffee,
        "fitness" to Icons.Filled.FitnessCenter, "flight" to Icons.Filled.Flight, "car" to Icons.Filled.DirectionsCar, "pets" to Icons.Filled.Pets,
        "leaf" to Icons.Filled.Eco, "lock" to Icons.Filled.Lock, "photo" to Icons.Filled.Photo, "video" to Icons.Filled.Videocam,
    )

    fun get(name: String): ImageVector = map[name] ?: Icons.Filled.Star
    val names: List<String> get() = map.keys.toList()
}
