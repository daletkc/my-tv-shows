package com.dalethomas.mytvshows

import com.dalethomas.mytvshows.data.Show
import com.dalethomas.mytvshows.domain.Services
import com.dalethomas.mytvshows.domain.autoPickApp
import com.dalethomas.mytvshows.domain.groupByService
import com.dalethomas.mytvshows.domain.whichAppQuestion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant

class AppsTest {
    private val hulu = Services.byPackage("com.hulu.livingroomplus")!!
    private val peacock = Services.byPackage("com.peacocktv.peacockandroid")!!
    private val paramount = Services.byPackage("com.cbs.ott")!!
    private val tubi = Services.otherApp("Tubi", "com.tubitv")
    private val now = Instant.parse("2026-11-20T12:00:00Z")

    @Test fun picksTheProviderWhenItIsInstalled() =
        assertEquals(hulu, autoPickApp("Hulu", listOf(peacock, hulu, tubi)))

    @Test fun asksWhenTheProviderIsNotInstalled() {
        assertNull(autoPickApp("Peacock", listOf(hulu, tubi)))
        assertEquals("TVMaze lists it on Peacock, which isn't on this TV. Which app do you use?",
            whichAppQuestion("Peacock", listOf(hulu, tubi)))
    }

    @Test fun asksWhenTheNetworkIsUnknownOrAmbiguous() {
        assertNull(autoPickApp("ABC", listOf(hulu)))
        assertEquals("TVMaze lists it on ABC. Which app do you watch it on?", whichAppQuestion("ABC", listOf(hulu)))
        assertNull(autoPickApp("CBS (next-day Hulu/Paramount+)", listOf(hulu, paramount)))
        assertEquals("It's on more than one of your apps. Which one do you use?",
            whichAppQuestion("CBS (next-day Hulu/Paramount+)", listOf(hulu, paramount)))
    }

    @Test fun showOpensInThePickedAppEvenIfItIsNotRecognized() {
        val show = Show(id = "s", title = "S", platform = "Tubi", appPackage = "com.tubitv")
        assertEquals(listOf(tubi), Services.forShow(show, listOf(hulu, tubi)))
        assertEquals(emptyList<Any>(), Services.forShow(show, listOf(hulu)))
    }

    @Test fun byServiceIncludesOtherAppsThatHaveShows() {
        val shows = listOf(
            Show(id = "a", title = "A", platform = "Hulu", statusGroup = "watching", appPackage = hulu.packageName),
            Show(id = "b", title = "B", platform = "Tubi", statusGroup = "watching", appPackage = "com.tubitv"),
        )
        val columns = groupByService(shows, now, others = listOf(tubi))
        assertEquals(listOf("Hulu", "Tubi"), columns.map { it.service.name })
        assertEquals(listOf("b"), columns.last().shows.map { it.id })
    }
}
