package com.example.watermetertest.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.watermetertest.R
import com.example.watermetertest.models.Command

class CommandsAdapter(private val listener: OnCommandClickListener) :
    RecyclerView.Adapter<CommandsAdapter.CommandViewHolder>() {

    interface OnCommandClickListener {
        fun onCommandClick(command: Command)
    }

    private var commands: List<Command>? = ArrayList()

    fun setCommands(commands: List<Command>?) {
        this.commands = commands
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CommandViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_command, parent, false)
        return CommandViewHolder(view)
    }

    override fun onBindViewHolder(holder: CommandViewHolder, position: Int) {
        commands?.get(position)?.let { holder.bind(it) }
    }

    override fun getItemCount(): Int = commands?.size ?: 0

    inner class CommandViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val commandLabel: TextView = itemView.findViewById(R.id.commandLabel)
        private val commandPayload: TextView = itemView.findViewById(R.id.commandPayload)
        private val parametersIndicator: TextView = itemView.findViewById(R.id.parametersIndicator)

        fun bind(command: Command) {
            commandLabel.text = command.label ?: "Unknown Command"
            
            if (command.payload != null) {
                commandPayload.visibility = View.VISIBLE
                commandPayload.text = "Payload: ${command.payload}"
            } else {
                commandPayload.visibility = View.GONE
            }

            if (command.hasParameters()) {
                parametersIndicator.visibility = View.VISIBLE
            } else {
                parametersIndicator.visibility = View.GONE
            }

            itemView.setOnClickListener {
                listener.onCommandClick(command)
            }
        }
    }
}
