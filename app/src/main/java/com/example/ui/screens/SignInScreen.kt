package com.example.ui.screens

import android.app.Activity
import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.content.ContextWrapper
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.NoCredentialException
import com.example.BuildConfig
import com.example.R
import com.example.ui.theme.AppShapes
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.io.IOException
import java.net.UnknownHostException
import android.util.Log
import kotlinx.coroutines.withTimeoutOrNull

private tailrec fun Context.findActivity(): Activity? = when (this) {
  is Activity -> this
  is ContextWrapper -> baseContext.findActivity()
  else -> null
}

@Composable
fun SignInScreen(
  onSignInSuccess: (FirebaseUser) -> Unit,
  modifier: Modifier = Modifier
) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()

  var isLoading by remember { mutableStateOf(false) }
  var errorMessage by remember { mutableStateOf<String?>(null) }

  val handleGoogleSignIn: () -> Unit = {
    scope.launch {
      isLoading = true
      errorMessage = null
      try {
        val serverClientId = try {
          context.getString(R.string.default_web_client_id)
        } catch (_: Exception) {
          BuildConfig.GOOGLE_OAUTH_CLIENT_ID
        }

        val activity = context.findActivity() ?: (context as? Activity) ?: context
        val activityCredentialManager = CredentialManager.create(activity)

        // Standard interactive Sign-in with Google option for button clicks
        val signInWithGoogleOption = GetSignInWithGoogleOption.Builder(serverClientId)
          .build()

        val request = GetCredentialRequest.Builder()
          .addCredentialOption(signInWithGoogleOption)
          .build()

        val result = withTimeoutOrNull(25_000L) {
          activityCredentialManager.getCredential(request = request, context = activity)
        }

        if (result == null) {
          errorMessage = "Sign-in timed out. Please tap Continue with Google to try again."
          return@launch
        }

        val credential = result.credential
        val googleIdTokenCredential = when {
          credential is CustomCredential -> {
            try {
              GoogleIdTokenCredential.createFrom(credential.data)
            } catch (e: Exception) {
              Log.e("SignInScreen", "Failed to parse GoogleIdTokenCredential", e)
              null
            }
          }
          else -> null
        }

        val idToken = googleIdTokenCredential?.idToken
        if (!idToken.isNullOrBlank()) {
          val authCredential = GoogleAuthProvider.getCredential(idToken, null)
          val authResult = FirebaseAuth.getInstance().signInWithCredential(authCredential).await()
          val user = authResult.user
          if (user != null) {
            onSignInSuccess(user)
          } else {
            errorMessage = "Sign-in completed but user profile was not found."
          }
        } else {
          errorMessage = "Could not retrieve Google account credentials. Please try again."
        }
      } catch (e: GetCredentialCancellationException) {
        // User dismissed the account chooser
        Log.d("SignInScreen", "User dismissed account chooser")
        errorMessage = null
      } catch (e: NoCredentialException) {
        Log.w("SignInScreen", "NoCredentialException during sign in", e)
        errorMessage = "Google Sign-In could not find an account. Please ensure Google Play Services is updated and try again."
      } catch (e: Exception) {
        Log.e("SignInScreen", "Sign-in failed", e)
        when {
          e is FirebaseNetworkException || e is UnknownHostException || e is IOException -> {
            errorMessage = "No internet connection. Please check your network and try again."
          }
          e.message?.contains("network", ignoreCase = true) == true ||
          e.message?.contains("offline", ignoreCase = true) == true -> {
            errorMessage = "No internet connection. Please check your network and try again."
          }
          else -> {
            errorMessage = e.localizedMessage ?: "Failed to sign in with Google. Please try again."
          }
        }
      } finally {
        isLoading = false
      }
    }
  }

  Surface(
    modifier = modifier
      .fillMaxSize()
      .testTag("signin_screen"),
    color = MaterialTheme.colorScheme.background
  ) {
    Box(
      modifier = Modifier
        .fillMaxSize()
        .padding(24.dp),
      contentAlignment = Alignment.Center
    ) {
      Column(
        modifier = Modifier
          .widthIn(max = 400.dp)
          .fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
      ) {
        // App Logo Icon
        Box(
          modifier = Modifier
            .size(96.dp)
            .clip(CircleShape)
            .background(
              Brush.linearGradient(
                colors = listOf(
                  MaterialTheme.colorScheme.primaryContainer,
                  MaterialTheme.colorScheme.primary
                )
              )
            ),
          contentAlignment = Alignment.Center
        ) {
          Image(
            painter = painterResource(id = R.drawable.ic_launcher_fg),
            contentDescription = "P Money Logo",
            modifier = Modifier.size(72.dp)
          )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // App Title
        Text(
          text = "P Money",
          style = MaterialTheme.typography.headlineLarge.copy(
            fontWeight = FontWeight.Bold,
            letterSpacing = (-0.5).sp
          ),
          color = MaterialTheme.colorScheme.onBackground,
          textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Tagline
        Text(
          text = "Personal Budget & Wealth Planner",
          style = MaterialTheme.typography.bodyLarge,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(44.dp))

        // Continue with Google Button (Full width within 400.dp, min height 48.dp, Google icon on start, label centered)
        Button(
          onClick = {
            if (!isLoading) {
              handleGoogleSignIn()
            }
          },
          enabled = !isLoading,
          modifier = Modifier
            .fillMaxWidth()
            .height(50.dp)
            .testTag("google_signin_btn"),
          shape = AppShapes.Button,
          colors = ButtonDefaults.buttonColors(
            containerColor = Color.White,
            contentColor = Color(0xFF1F1F1F),
            disabledContainerColor = Color.White.copy(alpha = 0.6f),
            disabledContentColor = Color(0xFF1F1F1F).copy(alpha = 0.5f)
          ),
          border = BorderStroke(1.dp, Color(0xFFDADCE0)),
          elevation = ButtonDefaults.buttonElevation(
            defaultElevation = 2.dp,
            pressedElevation = 4.dp
          )
        ) {
          Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center
          ) {
            if (isLoading) {
              Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
              ) {
                CircularProgressIndicator(
                  modifier = Modifier.size(20.dp),
                  strokeWidth = 2.dp,
                  color = Color(0xFF4285F4)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                  text = "Signing in...",
                  style = MaterialTheme.typography.bodyLarge.copy(
                    fontWeight = FontWeight.SemiBold
                  ),
                  color = Color(0xFF1F1F1F)
                )
              }
            } else {
              Icon(
                painter = painterResource(id = R.drawable.ic_google_logo),
                contentDescription = "Google Logo",
                tint = Color.Unspecified,
                modifier = Modifier
                  .align(Alignment.CenterStart)
                  .size(22.dp)
              )
              Text(
                text = "Continue with Google",
                style = MaterialTheme.typography.bodyLarge.copy(
                  fontWeight = FontWeight.SemiBold
                ),
                color = Color(0xFF1F1F1F),
                textAlign = TextAlign.Center
              )
            }
          }
        }

        // Error Banner centered below the button
        AnimatedVisibility(
          visible = errorMessage != null,
          enter = fadeIn(),
          exit = fadeOut()
        ) {
          if (errorMessage != null) {
            Column(
              horizontalAlignment = Alignment.CenterHorizontally,
              modifier = Modifier.fillMaxWidth()
            ) {
              Spacer(modifier = Modifier.height(16.dp))
              Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.errorContainer,
                shape = AppShapes.Card
              ) {
                Row(
                  modifier = Modifier
                    .padding(14.dp)
                    .fillMaxWidth(),
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.Center
                ) {
                  Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = "Warning",
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(20.dp)
                  )
                  Spacer(modifier = Modifier.width(10.dp))
                  Text(
                    text = errorMessage ?: "",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f)
                  )
                }
              }
            }
          }
        }

        Spacer(modifier = Modifier.height(32.dp))

        // Security note
        Text(
          text = "Your budget is encrypted and locked to your personal Google account with real-time cloud sync.",
          style = MaterialTheme.typography.labelMedium,
          color = MaterialTheme.colorScheme.outline,
          textAlign = TextAlign.Center,
          modifier = Modifier.padding(horizontal = 16.dp)
        )
      }
    }
  }
}
