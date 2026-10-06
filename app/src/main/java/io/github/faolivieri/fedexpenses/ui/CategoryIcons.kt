package io.github.faolivieri.fedexpenses.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.graphics.vector.ImageVector

/** Icons selectable for categories and accounts, keyed by the string stored in the database. */
object CategoryIcons {
    val all: List<Pair<String, ImageVector>> = listOf(
        "shopping_cart" to Icons.Filled.ShoppingCart,
        "shopping_bag" to Icons.Filled.ShoppingBag,
        "restaurant" to Icons.Filled.Restaurant,
        "lunch" to Icons.Filled.LunchDining,
        "fastfood" to Icons.Filled.Fastfood,
        "coffee" to Icons.Filled.LocalCafe,
        "local_bar" to Icons.Filled.LocalBar,
        "icecream" to Icons.Filled.Icecream,
        "nightlife" to Icons.Filled.Nightlife,
        "home" to Icons.Filled.Home,
        "bolt" to Icons.Filled.Bolt,
        "water" to Icons.Filled.WaterDrop,
        "wifi" to Icons.Filled.Wifi,
        "phone" to Icons.Filled.PhoneAndroid,
        "receipt" to Icons.AutoMirrored.Filled.ReceiptLong,
        "bus" to Icons.Filled.DirectionsBus,
        "train" to Icons.Filled.Train,
        "flight" to Icons.Filled.Flight,
        "car" to Icons.Filled.DirectionsCar,
        "fuel" to Icons.Filled.LocalGasStation,
        "bike" to Icons.AutoMirrored.Filled.DirectionsBike,
        "beach" to Icons.Filled.BeachAccess,
        "hotel" to Icons.Filled.Hotel,
        "luggage" to Icons.Filled.Luggage,
        "work" to Icons.Filled.Work,
        "fitness" to Icons.Filled.FitnessCenter,
        "sports" to Icons.Filled.SportsSoccer,
        "games" to Icons.Filled.SportsEsports,
        "ticket" to Icons.Filled.ConfirmationNumber,
        "movie" to Icons.Filled.Movie,
        "music" to Icons.Filled.MusicNote,
        "book" to Icons.AutoMirrored.Filled.MenuBook,
        "school" to Icons.Filled.School,
        "medical" to Icons.Filled.MedicalServices,
        "medication" to Icons.Filled.Medication,
        "spa" to Icons.Filled.Spa,
        "checkroom" to Icons.Filled.Checkroom,
        "gift" to Icons.Filled.CardGiftcard,
        "favorite" to Icons.Filled.Favorite,
        "child" to Icons.Filled.ChildCare,
        "pets" to Icons.Filled.Pets,
        "devices" to Icons.Filled.Devices,
        "build" to Icons.Filled.Build,
        "park" to Icons.Filled.Park,
        "celebration" to Icons.Filled.Celebration,
        "church" to Icons.Filled.Church,
        "payments" to Icons.Filled.Payments,
        "savings" to Icons.Filled.Savings,
        "wallet" to Icons.Filled.AccountBalanceWallet,
        "bank" to Icons.Filled.AccountBalance,
        "card" to Icons.Filled.CreditCard,
        "trending_up" to Icons.AutoMirrored.Filled.TrendingUp,
        "category" to Icons.Filled.Category,
        "help" to Icons.Filled.QuestionMark,
    )

    private val byKey = all.toMap()

    fun get(key: String): ImageVector = byKey[key] ?: Icons.Filled.Category
}
