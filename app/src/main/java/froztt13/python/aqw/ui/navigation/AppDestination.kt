package froztt13.python.aqw.ui.navigation

import kotlinx.serialization.Serializable

/**
 * Type-Safe Navigation Destinations for AQW Android Bot.
 */
sealed interface AppDestination {

    @Serializable
    data object Dashboard : AppDestination

    @Serializable
    data object Temple : AppDestination

    @Serializable
    data object Eclipse : AppDestination

    @Serializable
    data object WeeklyDoom : AppDestination

    @Serializable
    data object Slavery : AppDestination

    @Serializable
    data object GeneralBot : AppDestination

    @Serializable
    data object UltraBoss : AppDestination

    @Serializable
    data object UltraGramiel : AppDestination

    @Serializable
    data object UltraMalgor : AppDestination

    @Serializable
    data object UltraDrakath : AppDestination

    @Serializable
    data object PlayerState : AppDestination
}
