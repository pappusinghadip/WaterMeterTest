package com.example.watermetertest.dialogs

import android.app.Dialog
import android.content.Context
import android.text.InputType
import android.view.LayoutInflater
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import com.example.watermetertest.R
import com.example.watermetertest.models.Command
import com.example.watermetertest.models.Parameter
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.example.watermetertest.utils.Constants
import java.util.regex.Pattern

class CommandParametersDialog(
    private val context: Context,
    private val command: Command,
    private val listener: OnCommandExecuteListener
) {

    interface OnCommandExecuteListener {
        fun onCommandExecute(command: Command, finalPayload: String)
    }

    private val parameterInputs = HashMap<String, TextInputEditText>()

    fun show() {
        val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_command_parameters, null)

        val commandName: TextView = dialogView.findViewById(R.id.commandName)
        val commandPayload: TextView = dialogView.findViewById(R.id.commandPayload)
        val parametersContainer: LinearLayout = dialogView.findViewById(R.id.parametersContainer)
        val cancelButton: MaterialButton = dialogView.findViewById(R.id.cancelButton)
        val executeButton: MaterialButton = dialogView.findViewById(R.id.executeButton)

        // Set command info
        commandName.text = "Command: ${command.label ?: "Unknown"}"
        commandPayload.text = "Payload: ${command.payload ?: "N/A"}"

        // Create parameter input fields
        command.parameters?.forEach { (paramKey, parameter) ->
            val inputLayout = createParameterInput(paramKey, parameter)
            parametersContainer.addView(inputLayout)
        }

        val dialog = AlertDialog.Builder(context)
            .setView(dialogView)
            .setCancelable(true)
            .create()

        cancelButton.setOnClickListener { dialog.dismiss() }

        executeButton.setOnClickListener {
            if (validateInputs()) {
                val finalPayload = buildFinalPayload()
                android.util.Log.d("CommandDialog", "Original payload: ${command.payload}")
                android.util.Log.d("CommandDialog", "Final payload: $finalPayload")

                command.parameters?.forEach { (paramKey, parameter) ->
                    parameterInputs[paramKey]?.let { input ->
                        val inputValue = input.text.toString().trim()
                        val transformationRule = parameter.value ?: ""
                        val transformedValue = applyValueTransformation(inputValue, transformationRule)
                        android.util.Log.d("CommandDialog",
                            "Parameter $paramKey: '$inputValue' -> '$transformedValue' (rule: $transformationRule)")
                    }
                }

                listener.onCommandExecute(command, finalPayload)
                dialog.dismiss()
            } else {
                Toast.makeText(context, "Please fix the validation errors", Toast.LENGTH_SHORT).show()
            }
        }

        dialog.show()
    }

    private fun createParameterInput(paramKey: String, parameter: Parameter): TextInputLayout {
        val inputLayout = LayoutInflater.from(context).inflate(R.layout.item_dialog_input, null) as TextInputLayout
        val editText = inputLayout.findViewById<TextInputEditText>(R.id.inputEditText)

        inputLayout.hint = parameter.label ?: paramKey

        // Set input type and hints based on parameter type and transformation rule
        val transformationRule = parameter.value

        when (parameter.type) {
            Constants.PARAM_TYPE_TEXT -> editText.inputType = InputType.TYPE_CLASS_TEXT
            Constants.PARAM_TYPE_INT -> {
                editText.inputType = InputType.TYPE_CLASS_NUMBER
                if (parameter.min != null || parameter.max != null) {
                    var hint = "Enter number"
                    when {
                        parameter.min != null && parameter.max != null ->
                            hint += " (${parameter.min}-${parameter.max})"
                        parameter.min != null -> hint += " (min: ${parameter.min})"
                        parameter.max != null -> hint += " (max: ${parameter.max})"
                    }
                    inputLayout.helperText = hint
                }
            }
            Constants.PARAM_TYPE_CHECKSUM -> {
                editText.inputType = InputType.TYPE_CLASS_TEXT
                editText.setText("Auto-calculated")
                editText.isEnabled = false
            }
            else -> editText.inputType = InputType.TYPE_CLASS_TEXT
        }

        // Set specific hints based on transformation rules
        when (transformationRule) {
            Constants.RULE_IP -> {
                inputLayout.placeholderText = "e.g., 192.168.1.100"
                editText.setText("192.168.1.100") // Default IP that matches validation pattern
            }
            Constants.RULE_INT4 -> {
                inputLayout.placeholderText = "e.g., 8080"
                editText.setText("8080") // Default port
            }
            Constants.RULE_EQUAL -> {
                if (paramKey.lowercase().contains("s/n") || paramKey.lowercase().contains("serial") ||
                    parameter.label?.lowercase()?.contains("s/n") == true) {
                    inputLayout.placeholderText = "Enter 8-digit serial number"
                    editText.setText("12345678") // 8 digits that will pass validation
                    editText.inputType = InputType.TYPE_CLASS_NUMBER
                } else {
                    editText.setText("") // Leave empty for other 'equal' fields
                }
            }
        }

        parameterInputs[paramKey] = editText

        return inputLayout
    }

    private fun validateInputs(): Boolean {
        command.parameters?.forEach { (paramKey, parameter) ->
            val input = parameterInputs[paramKey]

            if (input != null && input.isEnabled) {
                val value = input.text.toString().trim()

                // Check if required field is empty
                if (!parameter.required.isNullOrEmpty()) {
                    if (value.isEmpty()) {
                        input.error = Constants.ERROR_REQUIRED
                        return false
                    }

                    // Validate with regex pattern
                    try {
                        val pattern = Pattern.compile(parameter.required)
                        if (!pattern.matcher(value).matches()) {
                            android.util.Log.d("CommandDialog",
                                "Validation failed for $paramKey: value='$value' pattern='${parameter.required}'")
                            input.error = "${Constants.ERROR_INVALID_FORMAT} (expected: ${parameter.required})"
                            return false
                        }
                    } catch (e: Exception) {
                        // Invalid regex pattern, just log it and skip validation
                        android.util.Log.w("CommandDialog",
                            "Invalid regex pattern for $paramKey: ${parameter.required}", e)
                        Toast.makeText(context, "Invalid validation pattern for $paramKey", Toast.LENGTH_SHORT).show()
                        // Don't fail validation if regex is invalid
                    }
                }

                // Validate integer parameters
                if (parameter.type == Constants.PARAM_TYPE_INT && value.isNotEmpty()) {
                    try {
                        val intValue = value.toInt()
                        if (parameter.min != null && intValue < parameter.min!!) {
                            input.error = "Value must be at least ${parameter.min}"
                            return false
                        }
                        if (parameter.max != null && intValue > parameter.max!!) {
                            input.error = "Value must be at most ${parameter.max}"
                            return false
                        }
                    } catch (e: NumberFormatException) {
                        input.error = Constants.ERROR_INVALID_NUMBER
                        return false
                    }
                }

                input.error = null
            }
        }
        return true
    }

    private fun buildFinalPayload(): String {
        var payload = command.payload ?: ""

        // First pass: Replace all non-checksum parameters
        command.parameters?.forEach { (paramKey, parameter) ->
            if (parameter.type != Constants.PARAM_TYPE_CHECKSUM) {
                val input = parameterInputs[paramKey]
                val placeholder = "{$paramKey}"
                if (input != null) {
                    var value = input.text.toString().trim()

                    // Apply value transformation if specified
                    if (!parameter.value.isNullOrEmpty()) {
                        value = applyValueTransformation(value, parameter.value!!)
                    }
                    payload = payload.replace(placeholder, value)
                }
            }
        }

        // Second pass: Calculate and replace checksums
        command.parameters?.forEach { (paramKey, parameter) ->
            if (parameter.type == Constants.PARAM_TYPE_CHECKSUM) {
                val placeholder = "{$paramKey}"
                // Calculate checksum for the current payload
                // Note: This sums all bytes currently in the payload string (excluding the placeholder)
                val value = calculateChecksum(payload.replace(placeholder, ""))
                payload = payload.replace(placeholder, value)
            }
        }

        return payload
    }

    private fun applyValueTransformation(inputValue: String, transformationRule: String): String {
        return when (transformationRule) {
            Constants.RULE_EQUAL -> inputValue
            Constants.RULE_IP -> {
                // Convert IP address to hex format
                // Example: 021.042.063.084 -> 152A3F54
                try {
                    val parts = inputValue.split(".")
                    if (parts.size == 4) {
                        parts.joinToString("") { part ->
                            val intPart = part.trim().toInt()
                            if (intPart in 0..255) {
                                String.format("%02X", intPart)
                            } else {
                                inputValue // Invalid IP part
                            }
                        }
                    } else {
                        inputValue
                    }
                } catch (e: NumberFormatException) {
                    inputValue // Invalid format
                }
            }
            Constants.RULE_INT4 -> {
                // Convert integer to 4-byte hex format
                // Example: 5013 -> 00001395
                try {
                    val intValue = inputValue.toInt()
                    String.format("%08X", intValue)
                } catch (e: NumberFormatException) {
                    inputValue
                }
            }
            else -> inputValue
        }
    }

    private fun calculateChecksum(payload: String): String {
        // CheckSum8 Modulo 256 algorithm
        // Sum all bytes and take modulo 256
        if (payload.length % 2 != 0) {
            return "00"
        }

        try {
            var sum = 0
            var i = 0
            while (i < payload.length) {
                val hexByte = payload.substring(i, i + 2)
                val byteValue = hexByte.toInt(16)
                sum += byteValue
                i += 2
            }

            // Take modulo 256 to get final checksum
            val checksum = sum % 256
            return String.format("%02X", checksum)
        } catch (e: Exception) {
            return "00"
        }
    }
}
