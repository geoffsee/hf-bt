package ltd.gsio.hfbt

import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.TestPropertySource

@SpringBootTest
@TestPropertySource(properties = ["webseed.base-url="])
class HfBtApplicationTests {

    @Test
    fun contextLoads() {
    }

}
