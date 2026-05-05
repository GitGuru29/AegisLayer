package com.aegislayer.daemon

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.aegislayer.daemon.engine.RuleRepository
import com.aegislayer.daemon.models.Rule
import com.google.android.material.button.MaterialButton

class RuleManagerActivity : AppCompatActivity() {

    private lateinit var repository: RuleRepository
    private lateinit var adapter: RuleAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_rule_manager)

        repository = RuleRepository(this)
        
        val rvRules = findViewById<RecyclerView>(R.id.rvRules)
        rvRules.layoutManager = LinearLayoutManager(this)
        
        adapter = RuleAdapter(repository.getAllRules())
        rvRules.adapter = adapter

        findViewById<MaterialButton>(R.id.btnAddRule).setOnClickListener {
            // TODO: Open New Rule Dialog
            android.widget.Toast.makeText(this, "Rule creation coming soon!", android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    class RuleAdapter(private val rules: List<Rule>) : RecyclerView.Adapter<RuleAdapter.ViewHolder>() {

        class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val tvId: TextView = view.findViewById(R.id.tvRuleId)
            val tvPriority: TextView = view.findViewById(R.id.tvPriority)
            val tvConditions: TextView = view.findViewById(R.id.tvConditions)
            val tvActions: TextView = view.findViewById(R.id.tvActions)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_rule, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val rule = rules[position]
            holder.tvId.text = rule.ruleId
            holder.tvPriority.text = "P: ${rule.priority}"
            holder.tvConditions.text = "IF: " + rule.conditions.joinToString(", ") { "${it.type}=${it.value}" }
            holder.tvActions.text = "THEN: " + rule.actions.joinToString(", ")
        }

        override fun getItemCount() = rules.size
    }
}
