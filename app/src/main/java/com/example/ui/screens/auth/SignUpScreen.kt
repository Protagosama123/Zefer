package com.example.ui.screens.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.ui.theme.BrushScriptFontFamily
import com.example.ui.theme.InterFontFamily
import com.example.ui.theme.ZeferBg
import com.example.ui.theme.ZeferPrimary
import com.example.ui.theme.ZeferSurface
import com.example.ui.theme.ZeferText
import com.example.ui.theme.ZeferTextDim
import com.example.ui.theme.ZeferTheme
import com.example.ui.theme.ZeferWhite

@Composable
fun SignUpScreen(
    viewModel: AuthViewModel,
    onNavigateToLogin: () -> Unit,
    onSignUpSuccess: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()

    SignUpScreenContent(
        email = uiState.email,
        isLoading = uiState.isLoading,
        errorMessage = uiState.errorMessage,
        onEmailChange = viewModel::onEmailChanged,
        onCreateAccountClick = { viewModel.login(onSignUpSuccess) },
        onGoogleClick = { viewModel.continueWithGoogle(onSignUpSuccess) },
        onNavigateToLogin = onNavigateToLogin,
        modifier = modifier
    )
}

@Composable
fun SignUpScreenContent(
    email: String,
    isLoading: Boolean,
    errorMessage: String?,
    onEmailChange: (String) -> Unit,
    onCreateAccountClick: () -> Unit,
    onGoogleClick: () -> Unit,
    onNavigateToLogin: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(ZeferBg)
            .padding(horizontal = 24.dp)
            .testTag("signup_screen_root"),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxWidth()
        ) {
            // Wordmark centered in upper-middle
            Text(
                text = stringResource(id = R.string.wordmark),
                fontFamily = BrushScriptFontFamily,
                style = MaterialTheme.typography.headlineLarge,
                modifier = Modifier.testTag("signup_wordmark_text")
            )

            Spacer(modifier = Modifier.height(12.dp))

            // "Already have an account ? Sign in"
            val signInAnnotated = buildAnnotatedString {
                append(stringResource(id = R.string.already_have_account))
                append(" ")
                withStyle(
                    style = SpanStyle(
                        fontWeight = FontWeight.Bold,
                        textDecoration = TextDecoration.Underline,
                        color = ZeferText
                    )
                ) {
                    append(stringResource(id = R.string.sign_in))
                }
            }

            Text(
                text = signInAnnotated,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier
                    .clickable { onNavigateToLogin() }
                    .padding(8.dp)
                    .testTag("signup_login_link")
            )

            Spacer(modifier = Modifier.height(28.dp))

            // Label "Email"
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = stringResource(id = R.string.email_label),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = ZeferText,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                // Single "Email" field (#D9D9D9, 12dp radius, passwordless auth)
                OutlinedTextField(
                    value = email,
                    onValueChange = onEmailChange,
                    placeholder = {
                        Text(
                            text = stringResource(id = R.string.email_placeholder),
                            color = ZeferTextDim,
                            style = MaterialTheme.typography.bodyLarge
                        )
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = ZeferSurface,
                        unfocusedContainerColor = ZeferSurface,
                        disabledContainerColor = ZeferSurface,
                        focusedBorderColor = Color.Transparent,
                        unfocusedBorderColor = Color.Transparent,
                        cursorColor = ZeferPrimary,
                        focusedTextColor = ZeferText,
                        unfocusedTextColor = ZeferText
                    ),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Email,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(onDone = { onCreateAccountClick() }),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .testTag("signup_email_input")
                )

                if (errorMessage != null) {
                    Text(
                        text = errorMessage,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Black Create Account button (white bold text)
            Button(
                onClick = onCreateAccountClick,
                enabled = !isLoading,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = ZeferPrimary,
                    contentColor = ZeferWhite
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("signup_submit_button")
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        color = ZeferWhite,
                        modifier = Modifier.size(24.dp)
                    )
                } else {
                    Text(
                        text = stringResource(id = R.string.create_account),
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        fontFamily = InterFontFamily
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Hairline divider with "Or"
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                HorizontalDivider(
                    modifier = Modifier.weight(1f),
                    thickness = 0.5.dp,
                    color = ZeferSurface
                )
                Text(
                    text = stringResource(id = R.string.or_divider),
                    style = MaterialTheme.typography.bodyMedium,
                    color = ZeferTextDim,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
                HorizontalDivider(
                    modifier = Modifier.weight(1f),
                    thickness = 0.5.dp,
                    color = ZeferSurface
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Gray Continue with Google button (#D9D9D9 fill, Google glyph)
            Button(
                onClick = onGoogleClick,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = ZeferSurface,
                    contentColor = ZeferText
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("signup_google_button")
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    GoogleGlyph(size = 20.dp)
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = stringResource(id = R.string.continue_with_google),
                        fontWeight = FontWeight.Medium,
                        fontSize = 15.sp,
                        color = ZeferText,
                        fontFamily = InterFontFamily
                    )
                }
            }

            Spacer(modifier = Modifier.height(36.dp))

            // Footer: "By clicking continue, you agree to our Terms of Service and Privacy Policy."
            val legalText = buildAnnotatedString {
                append("By clicking continue, you agree to our ")
                withStyle(
                    style = SpanStyle(
                        textDecoration = TextDecoration.Underline,
                        color = ZeferTextDim,
                        fontWeight = FontWeight.Medium
                    )
                ) {
                    append(stringResource(id = R.string.terms_of_service))
                }
                append(" and ")
                withStyle(
                    style = SpanStyle(
                        textDecoration = TextDecoration.Underline,
                        color = ZeferTextDim,
                        fontWeight = FontWeight.Medium
                    )
                ) {
                    append(stringResource(id = R.string.privacy_policy))
                }
                append(".")
            }

            Text(
                text = legalText,
                style = MaterialTheme.typography.labelSmall,
                color = ZeferTextDim,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .testTag("signup_legal_footer")
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun SignUpScreenPreview() {
    ZeferTheme {
        SignUpScreenContent(
            email = "test@example.com",
            isLoading = false,
            errorMessage = null,
            onEmailChange = {},
            onCreateAccountClick = {},
            onGoogleClick = {},
            onNavigateToLogin = {}
        )
    }
}
