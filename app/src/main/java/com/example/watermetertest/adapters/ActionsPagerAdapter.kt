package com.example.watermetertest.adapters

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.example.watermetertest.fragments.CommandsFragment
import com.example.watermetertest.models.Action

class ActionsPagerAdapter(fragmentActivity: FragmentActivity) : FragmentStateAdapter(fragmentActivity) {

    private var actions: List<Action> = ArrayList()

    fun setActions(actions: List<Action>?) {
        this.actions = actions ?: ArrayList()
        notifyDataSetChanged()
    }

    fun getActions(): List<Action> = actions

    override fun createFragment(position: Int): Fragment {
        return CommandsFragment.newInstance(actions[position])
    }

    override fun getItemCount(): Int = actions.size

    fun getPageTitle(position: Int): String {
        return if (position in actions.indices) {
            val action = actions[position]
            action.label ?: "Tab ${position + 1}"
        } else {
            "Tab ${position + 1}"
        }
    }
}
