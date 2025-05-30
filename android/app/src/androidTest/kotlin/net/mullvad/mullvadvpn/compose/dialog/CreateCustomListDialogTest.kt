package net.mullvad.mullvadvpn.compose.dialog

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import de.mannodermaus.junit5.compose.ComposeContext
import io.mockk.MockKAnnotations
import io.mockk.mockk
import io.mockk.verify
import net.mullvad.mullvadvpn.compose.createEdgeToEdgeComposeExtension
import net.mullvad.mullvadvpn.compose.setContentWithTheme
import net.mullvad.mullvadvpn.compose.state.CreateCustomListUiState
import net.mullvad.mullvadvpn.lib.model.CustomListAlreadyExists
import net.mullvad.mullvadvpn.lib.model.UnknownCustomListError
import net.mullvad.mullvadvpn.lib.ui.tag.CREATE_CUSTOM_LIST_DIALOG_INPUT_TEST_TAG
import net.mullvad.mullvadvpn.usecase.customlists.CreateWithLocationsError
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

class CreateCustomListDialogTest {
    @OptIn(ExperimentalTestApi::class)
    @JvmField
    @RegisterExtension
    val composeExtension = createEdgeToEdgeComposeExtension()

    @BeforeEach
    fun setup() {
        MockKAnnotations.init(this)
    }

    private fun ComposeContext.initDialog(
        state: CreateCustomListUiState = CreateCustomListUiState(),
        createCustomList: (String) -> Unit = {},
        onInputChanged: () -> Unit = {},
        onDismiss: () -> Unit = {},
    ) {
        setContentWithTheme {
            CreateCustomListDialog(
                state = state,
                createCustomList = createCustomList,
                onInputChanged = onInputChanged,
                onDismiss = onDismiss,
            )
        }
    }

    @Test
    fun givenNoErrorShouldShowNoErrorMessage() =
        composeExtension.use {
            // Arrange
            val state = CreateCustomListUiState(error = null)
            initDialog(state)

            // Assert
            onNodeWithText(NAME_EXIST_ERROR_TEXT).assertDoesNotExist()
            onNodeWithText(OTHER_ERROR_TEXT).assertDoesNotExist()
        }

    @Test
    fun givenCustomListExistsShouldShowCustomListExitsErrorText() =
        composeExtension.use {
            // Arrange
            val state =
                CreateCustomListUiState(
                    error = CreateWithLocationsError.Create(CustomListAlreadyExists)
                )
            initDialog(state)

            // Assert
            onNodeWithText(NAME_EXIST_ERROR_TEXT).assertExists()
            onNodeWithText(OTHER_ERROR_TEXT).assertDoesNotExist()
        }

    @Test
    fun givenOtherCustomListErrorShouldShowAnErrorOccurredErrorText() =
        composeExtension.use {
            // Arrange
            val state =
                CreateCustomListUiState(
                    error = CreateWithLocationsError.Create(UnknownCustomListError(Throwable()))
                )
            initDialog(state)

            // Assert
            onNodeWithText(NAME_EXIST_ERROR_TEXT).assertDoesNotExist()
            onNodeWithText(OTHER_ERROR_TEXT).assertExists()
        }

    @Test
    fun whenCancelIsClickedShouldDismissDialog() =
        composeExtension.use {
            // Arrange
            val mockedOnDismiss: () -> Unit = mockk(relaxed = true)
            val state = CreateCustomListUiState()
            initDialog(state, onDismiss = mockedOnDismiss)

            // Act
            onNodeWithText(CANCEL_BUTTON_TEXT).performClick()

            // Assert
            verify { mockedOnDismiss.invoke() }
        }

    @Test
    fun givenEmptyTextInputWhenSubmitIsClickedThenShouldNotCallOnCreate() =
        composeExtension.use {
            // Arrange
            val mockedCreateCustomList: (String) -> Unit = mockk(relaxed = true)
            val state = CreateCustomListUiState()
            initDialog(state, createCustomList = mockedCreateCustomList)

            // Act
            onNodeWithText(CREATE_BUTTON_TEXT).performClick()

            // Assert
            verify(exactly = 0) { mockedCreateCustomList.invoke(any()) }
        }

    @Test
    fun givenValidTextInputWhenSubmitIsClickedThenShouldCallOnCreate() =
        composeExtension.use {
            // Arrange
            val mockedCreateCustomList: (String) -> Unit = mockk(relaxed = true)
            val inputText = "NEW LIST"
            val state = CreateCustomListUiState()
            initDialog(state, createCustomList = mockedCreateCustomList)

            // Act
            onNodeWithTag(CREATE_CUSTOM_LIST_DIALOG_INPUT_TEST_TAG).performTextInput(inputText)
            onNodeWithText(CREATE_BUTTON_TEXT).performClick()

            // Assert
            verify { mockedCreateCustomList.invoke(inputText) }
        }

    @Test
    fun whenInputIsChangedShouldCallOnInputChanged() =
        composeExtension.use {
            // Arrange
            val mockedOnInputChanged: () -> Unit = mockk(relaxed = true)
            val inputText = "NEW LIST"
            val state = CreateCustomListUiState()
            initDialog(state, onInputChanged = mockedOnInputChanged)

            // Act
            onNodeWithTag(CREATE_CUSTOM_LIST_DIALOG_INPUT_TEST_TAG).performTextInput(inputText)

            // Assert
            verify { mockedOnInputChanged.invoke() }
        }

    companion object {
        private const val NAME_EXIST_ERROR_TEXT = "Name is already taken."
        private const val OTHER_ERROR_TEXT = "An error occurred."
        private const val CANCEL_BUTTON_TEXT = "Cancel"
        private const val CREATE_BUTTON_TEXT = "Create"
    }
}
