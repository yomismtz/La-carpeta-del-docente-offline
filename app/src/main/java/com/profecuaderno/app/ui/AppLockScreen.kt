package com.profecuaderno.app.ui

import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.profecuaderno.app.security.AppSecurityManager

@Composable
fun AppLockScreen(onUnlocked: () -> Unit) {
    val context = LocalContext.current
    val activity = context as? FragmentActivity
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    val biometricEnabled = remember { AppSecurityManager.isBiometricEnabled(context) }

    fun launchBiometric() {
        if (activity == null) return
        val manager = BiometricManager.from(context)
        val authenticators = BiometricManager.Authenticators.BIOMETRIC_STRONG
        if (manager.canAuthenticate(authenticators) != BiometricManager.BIOMETRIC_SUCCESS) {
            error = "La biometría no está disponible. Usa tu PIN."
            return
        }

        val executor = ContextCompat.getMainExecutor(context)
        val prompt = BiometricPrompt(activity, executor, object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                onUnlocked()
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                if (errorCode != BiometricPrompt.ERROR_NEGATIVE_BUTTON && errorCode != BiometricPrompt.ERROR_USER_CANCELED) {
                    error = errString.toString()
                }
            }

            override fun onAuthenticationFailed() {
                error = "No se reconoció la biometría."
            }
        })

        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Desbloquear ProfeCuaderno")
            .setSubtitle("Usa tu biometría o vuelve para escribir tu PIN.")
            .setAllowedAuthenticators(authenticators)
            .setNegativeButtonText("Usar PIN")
            .build()
        prompt.authenticate(info)
    }

    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        ElevatedCard(Modifier.widthIn(max = 460.dp)) {
            Column(
                Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(Icons.Default.Lock, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(48.dp))
                Text("ProfeCuaderno bloqueado", style = MaterialTheme.typography.titleLarge)
                Text("Ingresa tu PIN para proteger los datos docentes.", style = MaterialTheme.typography.bodySmall)

                OutlinedTextField(
                    value = pin,
                    onValueChange = { pin = it.filter(Char::isDigit).take(8) },
                    label = { Text("PIN") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Button(
                    enabled = pin.length in 4..8,
                    onClick = {
                        if (AppSecurityManager.verifyPin(context, pin)) {
                            error = null
                            onUnlocked()
                        } else {
                            error = "PIN incorrecto."
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Desbloquear") }

                if (biometricEnabled) {
                    OutlinedButton(onClick = { launchBiometric() }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.Fingerprint, null)
                        Spacer(Modifier.width(6.dp))
                        Text("Usar biometría")
                    }
                }

                error?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}
