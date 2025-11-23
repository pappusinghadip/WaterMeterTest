package com.example.watermetertest.models

import java.io.Serializable

data class Command(
    var label: String? = null,
    var payload: String? = null,
    var parameters: Map<String, Parameter>? = null
) : Serializable {
    fun hasParameters(): Boolean {
        return parameters != null && parameters!!.isNotEmpty()
    }
}
