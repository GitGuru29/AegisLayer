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
        val aiProcessor = com.aegislayer.daemon.engine.AIPromptProcessor()
        
        val rvRules = findViewById<RecyclerView>(R.id.rvRules)
        rvRules.layoutManager = LinearLayoutManager(this)
        
        adapter = RuleAdapter(repository.getAllRules())
        rvRules.adapter = adapter

        findViewById<MaterialButton>(R.id.btnAddRule).setOnClickListener {
            showAddRuleDialog()
        }

        val tilAi = findViewById<com.google.android.material.textfield.TextInputLayout>(R.id.tilAiPrompt)
        val etAi = findViewById<android.widget.EditText>(R.id.etAiPrompt)

        tilAi.setEndIconOnClickListener {
            val prompt = etAi.text.toString()
            if (prompt.isNotEmpty()) {
                val rule = aiProcessor.processPrompt(prompt)
                if (rule != null) {
                    repository.saveUserRule(rule)
                    etAi.text.clear()
                    refreshList()
                    android.widget.Toast.makeText(this, "AI: Rule '${rule.ruleId}' created!", android.widget.Toast.LENGTH_SHORT).show()
                } else {
                    android.widget.Toast.makeText(this, "AI: Sorry, I couldn't understand that rule.", android.widget.Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun showAddRuleDialog() {
        val builder = com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
        builder.setTitle("Create New Rule")
        
        val view = layoutInflater.inflate(R.layout.dialog_add_rule, null)
        builder.setView(view)

        val etId = view.findViewById<android.widget.EditText>(R.id.etRuleId)
        val spCondition = view.findViewById<android.widget.Spinner>(R.id.spCondition)
        val etCondValue = view.findViewById<android.widget.EditText>(R.id.etCondValue)
        val spAction = view.findViewById<android.widget.Spinner>(R.id.spAction)
        val etActValue = view.findViewById<android.widget.EditText>(R.id.etActValue)

        builder.setPositiveButton("Save") { _, _ ->
            val id = etId.text.toString()
            val condType = spCondition.selectedItem.toString()
            val condValRaw = etCondValue.text.toString()
            val actType = spAction.selectedItem.toString()
            val actVal = etActValue.text.toString()

            if (id.isNotEmpty()) {
                val conditionValue: Any = when (condType) {
                    "SCREEN_ON", "IS_CHARGING" -> condValRaw.toBoolean()
                    "BATTERY_LEVEL" -> condValRaw.toIntOrNull() ?: 50
                    else -> condValRaw
                }

                val actionStr = if (actVal.isNotEmpty()) "$actType:$actVal" else actType
                
                val newRule = Rule(
                    ruleId = id,
                    conditions = listOf(com.aegislayer.daemon.models.Condition(condType, conditionValue)),
                    actions = listOf(actionStr),
                    priority = 5
                )
                
                repository.saveUserRule(newRule)
                refreshList()
            }
        }
        builder.setNegativeButton("Cancel", null)
        builder.show()
    }

    private fun refreshList() {
        adapter.updateRules(repository.getAllRules())
    }

    fun showDeleteConfirm(ruleId: String) {
        com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
            .setTitle("Delete Rule?")
            .setMessage("Are you sure you want to remove '$ruleId'?")
            .setPositiveButton("Delete") { _, _ ->
                repository.deleteUserRule(ruleId)
                refreshList()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    class RuleAdapter(private var rules: List<Rule>) : RecyclerView.Adapter<RuleAdapter.ViewHolder>() {

        class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val tvId: TextView = view.findViewById(R.id.tvRuleId)
            val tvPriority: TextView = view.findViewById(R.id.tvPriority)
            val tvConditions: TextView = view.findViewById(R.id.tvConditions)
            val tvActions: TextView = view.findViewById(R.id.tvActions)
        }

        fun updateRules(newRules: List<Rule>) {
            rules = newRules
            notifyDataSetChanged()
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

            holder.itemView.setOnLongClickListener {
                (holder.itemView.context as? RuleManagerActivity)?.showDeleteConfirm(rule.ruleId)
                true
            }
        }

        override fun getItemCount() = rules.size
    }
}
