package com.example.watermetertest.fragments

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.watermetertest.databinding.FragmentCommandsBinding
import com.example.watermetertest.BluetoothSettingsActivity
import com.example.watermetertest.R
import com.example.watermetertest.adapters.CommandsAdapter
import com.example.watermetertest.dialogs.CommandParametersDialog
import com.example.watermetertest.models.Action
import com.example.watermetertest.models.Command
import com.example.watermetertest.presentation.viewmodels.CommandsViewModel

import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class CommandsFragment : Fragment() {
    
    companion object {
        private const val ARG_ACTION = "action"

        fun newInstance(action: Action): CommandsFragment {
            val fragment = CommandsFragment()
            val args = Bundle().apply {
                putSerializable(ARG_ACTION, action)
            }
            fragment.arguments = args
            return fragment
        }
    }

    private var _binding: FragmentCommandsBinding? = null
    private val binding get() = _binding!!
    
    private lateinit var adapter: CommandsAdapter
    private var action: Action? = null
    private val commandsViewModel: CommandsViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            action = it.getSerializable(ARG_ACTION) as? Action
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentCommandsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        action?.let { commandsViewModel.setAction(it) }

        setupRecyclerView()
        observeViewModel()
        updateUI()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun setupRecyclerView() {
        binding.commandsRecyclerView.layoutManager = LinearLayoutManager(context)
        adapter = CommandsAdapter(object : CommandsAdapter.OnCommandClickListener {
            override fun onCommandClick(command: Command) {
                // Handle command click
                if (command.hasParameters()) {
                    // Show parameter input dialog
                    showParameterDialog(command)
                } else {
                    // Execute command directly
                    executeCommand(command, command.payload)
                }
            }
        })
        binding.commandsRecyclerView.adapter = adapter
    }

    private fun updateUI() {
        if (action?.items?.isNotEmpty() == true) {
            adapter.setCommands(action?.items)
            binding.commandsRecyclerView.visibility = View.VISIBLE
            binding.emptyStateText.visibility = View.GONE
        } else {
            binding.commandsRecyclerView.visibility = View.GONE
            binding.emptyStateText.visibility = View.VISIBLE
        }
    }

    private fun showParameterDialog(command: Command) {
        val dialog = CommandParametersDialog(
            requireContext(),
            command,
            object : CommandParametersDialog.OnCommandExecuteListener {
                override fun onCommandExecute(command: Command, finalPayload: String) {
                    executeCommand(command, finalPayload)
                }
            }
        )
        dialog.show()
    }

    private fun executeCommand(command: Command, payload: String?) {
        commandsViewModel.executeCommand(command, payload)
    }

    private fun observeViewModel() {
        commandsViewModel.getConnectedDevice().observe(viewLifecycleOwner) { device ->
            // Update UI based on connected device if needed
        }

        commandsViewModel.getIsConnected().observe(viewLifecycleOwner) { isConnected ->
            if (isConnected != true) {
                // Show message or update UI to indicate no device connected
            }
        }

        commandsViewModel.getBluetoothError().observe(viewLifecycleOwner) { error ->
            error?.let {
                Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            }
        }

        commandsViewModel.getDataSent().observe(viewLifecycleOwner) { dataSent ->
            if (dataSent == true) {
                Toast.makeText(context, "Command sent successfully", Toast.LENGTH_SHORT).show()
            }
        }

        commandsViewModel.getCommandResult().observe(viewLifecycleOwner) { result ->
            result?.let {
                if (it.contains("No Bluetooth device connected")) {
                    // Show option to go to Bluetooth settings
                    Toast.makeText(context, it, Toast.LENGTH_LONG).show()
                    val intent = Intent(requireContext(), BluetoothSettingsActivity::class.java)
                    startActivity(intent)
                }
            }
        }
    }
}
