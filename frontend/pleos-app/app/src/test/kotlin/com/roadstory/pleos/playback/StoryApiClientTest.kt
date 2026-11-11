package com.roadstory.pleos.playback

import com.roadstory.pleos.platform.VehicleLocationFix
import org.junit.Assert.assertEquals
import org.junit.Test

class StoryApiClientTest {
    @Test
    fun `OpenAPI StoryDetail에서 TTS 낭독문을 읽는다`() {
        val speech = StoryApiClient.parseStorySpeech("""{"id":"story-published-1","title":"성수동 수제화 거리","fullText":"성수동 수제화 거리는 장인들의 작업장이 모인 곳입니다."}""")
        assertEquals("story-published-1", speech.id)
        assertEquals("성수동 수제화 거리는 장인들의 작업장이 모인 곳입니다.", speech.text)
    }

    @Test
    fun `주변 Story 응답의 순서와 미리듣기 정보를 유지한다`() {
        val stories = StoryApiClient.parseNearbyStories(
            """{
                "items": [
                    {"id":"story-published-1","title":"성수동 수제화 거리","placeName":"성수동","durationSeconds":60,"category":"INDUSTRY"},
                    {"id":"story-published-2","title":"두 번째 데모 이야기","placeName":"성수동","durationSeconds":12,"category":"DAILY_LIFE","familyFriendly":true}
                ]
            }"""
        )

        assertEquals(
            listOf(
                QueueStory("story-published-1", "성수동 수제화 거리", "성수동", 60, "INDUSTRY"),
                QueueStory("story-published-2", "두 번째 데모 이야기", "성수동", 12, "DAILY_LIFE", familyFriendly = true),
            ),
            stories,
        )
    }

    @Test
    fun `선택한 Story 상세를 큐 항목으로 표시할 수 있다`() {
        val speech = StoryApiClient.parseStorySpeech(
            """{"id":"story-published-2","title":"두 번째 데모 이야기","fullText":"두 번째 데모입니다.","placeName":"성수동","durationSeconds":12,"category":"DAILY_LIFE","familyFriendly":true}"""
        )

        assertEquals(
            QueueStory("story-published-2", "두 번째 데모 이야기", "성수동", 12, "DAILY_LIFE", familyFriendly = true),
            speech.toQueueStory(),
        )
    }

    @Test
    fun `추천 메타데이터가 없거나 잘못된 타입이어도 이야기 재생은 가능하다`() {
        val stories = StoryApiClient.parseNearbyStories(
            """{"items":[{"id":"legacy","title":"기존 이야기"},{"id":"bad","title":"다른 이야기","category":null,"familyFriendly":"true"}]}""",
        )

        assertEquals(listOf(QueueStory("legacy", "기존 이야기"), QueueStory("bad", "다른 이야기")), stories)
    }

    @Test
    fun `주변 조회는 전달받은 좌표와 측정한 이동 정보만 사용한다`() {
        val fix = VehicleLocationFix(37.123456, 126.234567, heading = 92.0, speedMps = 7.5)

        assertEquals(
            "/v1/stories/nearby?latitude=37.1235&longitude=126.2346&speedMps=7.5&heading=92.0",
            StoryApiClient.nearbyPath(fix),
        )
        assertEquals(
            "/v1/stories/nearby?latitude=37.1235&longitude=126.2346&speedMps=0.5",
            StoryApiClient.nearbyPath(fix.copy(speedMps = 0.5)),
        )
        assertEquals(
            "/v1/stories/nearby?latitude=37.1235&longitude=126.2346",
            StoryApiClient.nearbyPath(fix.copy(speedMps = null)),
        )
    }

    @Test
    fun `목적지 응답은 편성 기준과 서버가 선택한 순서를 표시한다`() {
        val response = """
            {
                "basis":"ROUTE",
                "totalAudioDurationSeconds":110,
                "items":[
                    {"id":"first-stop","title":"첫 장소 이야기","durationSeconds":50},
                    {"id":"second-stop","title":"두 번째 장소 이야기","durationSeconds":60}
                ]
            }
        """.trimIndent()

        val plan = StoryApiClient.parseDestination(response)
        assertEquals(PlaylistBasis.ROUTE, plan.basis)
        assertEquals(110, plan.totalAudioDurationSeconds)
        assertEquals(listOf("first-stop", "second-stop"), plan.stories.map(QueueStory::id))
        assertEquals(
            PlaylistBasis.DESTINATION_NEARBY,
            StoryApiClient.parseDestination(response.replace("ROUTE", "DESTINATION_NEARBY")).basis,
        )
    }

    @Test
    fun `공개 코스의 첫 정류장과 중복 없는 이야기 순서를 표시한다`() {
        val response = """
            {
                "id":"public-course",
                "title":"공개 코스",
                "description":"코스 소개",
                "durationSeconds":1800,
                "distanceMeters":3000,
                "stopCount":3,
                "storyCount":2,
                "familyFriendly":true,
                "offlineAvailable":false,
                "stops":[
                    {
                        "sequence":3,"placeName":"세 번째 장소",
                        "coordinates":{"latitude":37.12,"longitude":126.13},
                        "stories":[
                            {"id":"shared","title":"첫 장소에서 이미 들은 이야기"},
                            {"id":"last-story","title":"마지막 이야기"}
                        ]
                    },
                    {
                        "sequence":1,"placeName":"첫 장소",
                        "coordinates":{"latitude":37.14,"longitude":126.15},
                        "stories":[{"id":"shared","title":"첫 장소 이야기","durationSeconds":35}]
                    },
                    {
                        "sequence":2,"placeName":"두 번째 장소",
                        "coordinates":{"latitude":37.16,"longitude":126.17},
                        "storyIds":["excluded"]
                    }
                ]
            }
        """.trimIndent()

        val course = StoryApiClient.parseCourse(response)
        val summaries = StoryApiClient.parseCourses("""{"items":[$response]}""")
        assertEquals(listOf(course.summary), summaries)
        assertEquals("public-course", course.summary.id)
        assertEquals(listOf(1, 2, 3), course.stops.map(CourseStop::sequence))
        assertEquals("첫 장소", course.stops.first().placeName)
        assertEquals(37.14, course.stops.first().latitude, 0.0)
        assertEquals(126.15, course.stops.first().longitude, 0.0)
        assertEquals(listOf("shared", "last-story"), course.stories.map(QueueStory::id))
        assertEquals("첫 장소 이야기", course.stories.first().title)
    }
}
