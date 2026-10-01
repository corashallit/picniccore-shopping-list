package earth.cora.picnic.core

import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Tag
import kotlin.test.Test

@Tag("live")
class LiveSmokeTest {
    @Test
    fun runsOnlyWhenLive() {
        assumeTrue(System.getenv("OPENAI_API_KEY")?.isNotBlank() == true, "OPENAI_API_KEY not set")
        println("LIVE TEST RAN")
    }
}
