package mega.privacy.android.domain.usecase.chat

import com.google.common.truth.Truth
import kotlinx.coroutines.test.runTest
import mega.privacy.android.domain.usecase.contact.GetParticipantFirstNameUseCase
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import org.mockito.kotlin.mock
import org.mockito.kotlin.reset
import org.mockito.kotlin.whenever
import java.util.stream.Stream

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class GetCustomSubtitleListUseCaseTest {

    private lateinit var underTest: GetCustomSubtitleListUseCase

    private val getParticipantFirstNameUseCase = mock<GetParticipantFirstNameUseCase>()
    private val loadUserAttributesUseCase = mock<LoadUserAttributesUseCase>()

    private val userA = "A"
    private val userB = "B"
    private val userC = "C"

    @BeforeAll
    fun setup() {
        underTest = GetCustomSubtitleListUseCase(
            getParticipantFirstNameUseCase = getParticipantFirstNameUseCase,
            loadUserAttributesUseCase = loadUserAttributesUseCase
        )
    }

    @BeforeEach
    fun resetMocks() {
        reset(getParticipantFirstNameUseCase, loadUserAttributesUseCase)
    }

    @ParameterizedTest(name = " participants {0} and result {2} and updated result {3}")
    @MethodSource("provideParameters")
    fun `test that get custom subtitle returns correctly when`(
        participantsList: List<Long>,
        namesResult: List<String?>,
        updatedNamesResult: List<String>,
        expectedResult: List<String>,
    ) = runTest {
        val chatId = 123L

        if (participantsList.isEmpty()) {
            Truth.assertThat(underTest.invoke(chatId, participantsList))
                .isEqualTo(expectedResult)

            return@runTest
        }

        val participantsCount = participantsList.size
        val pendingToGet = mutableListOf<Long>()
        val maxNamesParticipants = 3
        val maxExceeded = participantsCount > maxNamesParticipants
        val count =
            if (maxExceeded) maxNamesParticipants.minus(1)
            else participantsCount.minus(1)

        for (i in 0..count) {
            whenever(getParticipantFirstNameUseCase(participantsList[i], false))
                .thenReturn(namesResult[i])

            if (namesResult[i] == null) {
                pendingToGet.add(participantsList[i])
            }
        }

        if (pendingToGet.isNotEmpty()) {
            whenever(loadUserAttributesUseCase(chatId, pendingToGet)).thenReturn(Unit)
            pendingToGet.forEachIndexed { i, participant ->
                whenever(getParticipantFirstNameUseCase(participant, false))
                    .thenReturn(updatedNamesResult[i])
            }
        }

        Truth.assertThat(underTest.invoke(chatId, participantsList))
            .isEqualTo(expectedResult)
    }

    private fun provideParameters(): Stream<Arguments> = Stream.of(
        Arguments.of(
            emptyList<Long>(),
            emptyList<String>(),
            emptyList<String>(),
            emptyList<String>()
        ),
        Arguments.of(
            emptyList<Long>(),
            emptyList<String>(),
            emptyList<String>(),
            emptyList<String>()
        ),
        Arguments.of(listOf(1L), listOf(userA), emptyList<String>(), listOf(userA)),
        Arguments.of(listOf(1L), listOf(userA), emptyList<String>(), listOf(userA)),
        Arguments.of(listOf(1L), listOf(null), listOf(userA), listOf(userA)),
        Arguments.of(listOf(1L), listOf(null), listOf(userA), listOf(userA)),
        Arguments.of(
            listOf(1L, 2L),
            listOf(null, userB),
            listOf(userA),
            listOf(userA, userB)
        ),
        Arguments.of(
            listOf(1L, 2L),
            listOf(userA, null),
            listOf(userB),
            listOf(userA, userB)
        ),
        Arguments.of(
            listOf(1L, 2L),
            listOf(null, userB),
            listOf(userA),
            listOf(userA, userB)
        ),
        Arguments.of(
            listOf(1L, 2L),
            listOf(userA, null),
            listOf(userB),
            listOf(userA, userB)
        ),
        Arguments.of(
            listOf(1L, 2L, 3L),
            listOf(userA, userB, userC),
            emptyList<String>(),
            listOf(userA, userB, userC)
        ),
        Arguments.of(
            listOf(1L, 2L, 3L),
            listOf(null, userB, userC),
            listOf(userA),
            listOf(userA, userB, userC)
        ),
        Arguments.of(
            listOf(1L, 2L, 3L),
            listOf(null, null, userC),
            listOf(userA, userB),
            listOf(userA, userB, userC)
        ),
        Arguments.of(
            listOf(1L, 2L, 3L),
            listOf(null, null, null),
            listOf(userA, userB, userC),
            listOf(userA, userB, userC)
        ),
        Arguments.of(
            listOf(1L, 2L, 3L),
            listOf(userA, null, userC),
            listOf(userB),
            listOf(userA, userB, userC)
        ),
        Arguments.of(
            listOf(1L, 2L, 3L),
            listOf(userA, null, null),
            listOf(userB, userC),
            listOf(userA, userB, userC)
        ),
        Arguments.of(
            listOf(1L, 2L, 3L),
            listOf(userA, userB, null),
            listOf(userC),
            listOf(userA, userB, userC)
        ),
        Arguments.of(
            listOf(1L, 2L, 3L),
            listOf(userA, userB, userC),
            emptyList<String>(),
            listOf(userA, userB, userC)
        ),
        Arguments.of(
            listOf(1L, 2L, 3L),
            listOf(null, userB, userC),
            listOf(userA),
            listOf(userA, userB, userC)
        ),
        Arguments.of(
            listOf(1L, 2L, 3L),
            listOf(null, null, userC),
            listOf(userA, userB),
            listOf(userA, userB, userC)
        ),
        Arguments.of(
            listOf(1L, 2L, 3L),
            listOf(null, null, null),
            listOf(userA, userB, userC),
            listOf(userA, userB, userC)
        ),
        Arguments.of(
            listOf(1L, 2L, 3L),
            listOf(userA, null, userC),
            listOf(userB),
            listOf(userA, userB, userC)
        ),
        Arguments.of(
            listOf(1L, 2L, 3L),
            listOf(userA, null, null),
            listOf(userB, userC),
            listOf(userA, userB, userC)
        ),
        Arguments.of(
            listOf(1L, 2L, 3L),
            listOf(userA, userB, null),
            listOf(userC),
            listOf(userA, userB, userC)
        ),
        Arguments.of(
            listOf(1L, 2L, 3L, 4L),
            listOf(userA, userB, userC, "2"),
            emptyList<String>(),
            listOf(userA, userB, userC, "2"),
        ),
        Arguments.of(
            listOf(1L, 2L, 3L, 4L, 5L),
            listOf(userA, userB, userC, "3"),
            emptyList<String>(),
            listOf(userA, userB, userC, "3"),
        ),
    )
}