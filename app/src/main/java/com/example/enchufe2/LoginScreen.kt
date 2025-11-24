package com.example.enchufe2.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun LoginScreen(
    onUserLoginSuccess: () -> Unit,
    onAdminLoginSuccess: () -> Unit
) {
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text("🔐 Iniciar Sesión", fontSize = 26.sp)

        Spacer(modifier = Modifier.height(24.dp))

        OutlinedTextField(
            value = username,
            onValueChange = {
                username = it
                errorMessage = ""
            },
            label = { Text("Usuario") },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = password,
            onValueChange = {
                password = it
                errorMessage = ""
            },
            label = { Text("Contraseña") },
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = {
                if (username == "usuario" && password == "1234") {
                    onUserLoginSuccess()
                } else {
                    errorMessage = "Credenciales de usuario incorrectas"
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Entrar como Usuario")
        }

        Spacer(modifier = Modifier.height(12.dp))

        Button(
            onClick = {
                if (username == "cuidador" && password == "admin") {
                    onAdminLoginSuccess()
                } else {
                    errorMessage = "Credenciales de cuidador incorrectas"
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Entrar como Cuidador")
        }

        if (errorMessage.isNotEmpty()) {
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = errorMessage,
                color = MaterialTheme.colorScheme.error,
                fontSize = 14.sp
            )
        }
    }
}
