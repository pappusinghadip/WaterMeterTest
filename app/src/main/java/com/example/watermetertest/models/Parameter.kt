package com.example.watermetertest.models

import java.io.Serializable

data class Parameter(
    var label: String? = null,
    var type: String? = null,
    var value: String? = null,
    var required: String? = null,
    var min: Int? = null,
    var max: Int? = null
) : Serializable
