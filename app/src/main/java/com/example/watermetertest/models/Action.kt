package com.example.watermetertest.models

import java.io.Serializable

data class Action(
    var label: String? = null,
    var items: List<Command>? = null
) : Serializable
