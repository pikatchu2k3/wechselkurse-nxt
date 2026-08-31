package de.salomax.currencies.view.main

import android.content.Intent
import android.icu.util.Calendar
import android.icu.util.TimeZone
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.DatePicker
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.SearchView
import androidx.core.text.HtmlCompat
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.color.MaterialColors
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.switchmaterial.SwitchMaterial
import de.salomax.currencies.R
import de.salomax.currencies.model.Currency
import de.salomax.currencies.util.toRelativeTimeString
import de.salomax.currencies.view.BaseActivity
import de.salomax.currencies.view.preference.PreferenceActivity
import de.salomax.currencies.viewmodel.main.RatesListViewModel
import java.io.File
import java.time.LocalDate

class RatesListActivity : BaseActivity() {

    companion object {
        const val ARG_TAPPED_CURRENCY = "ARG_TAPPED_CURRENCY"
    }

    private lateinit var viewModel: RatesListViewModel
    private lateinit var adapter: RatesListAdapter
    private lateinit var refreshIndicator: LinearProgressIndicator
    private lateinit var swipeRefresh: SwipeRefreshLayout
    private var menuItemRefresh: MenuItem? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // general layout
        setContentView(R.layout.activity_rates_list)

        // model
        this.viewModel = ViewModelProvider(
            this,
            RatesListViewModel.Factory(this.application)
        )[RatesListViewModel::class.java]

        // views
        this.refreshIndicator = findViewById(R.id.refreshIndicator)
        this.swipeRefresh = findViewById(R.id.swipeRefresh)

        // action bar: title + leading hamburger (placeholder for a drawer in a later phase)
        supportActionBar?.apply {
            title = getString(R.string.rates_list_title)
            setDisplayHomeAsUpEnabled(true)
            setHomeAsUpIndicator(R.drawable.ic_menu)
        }

        // recycler view
        this.adapter = RatesListAdapter { currency -> openCurrency(currency) }
        val listRates = findViewById<RecyclerView>(R.id.listRates)
        listRates.layoutManager = LinearLayoutManager(this)
        listRates.adapter = adapter

        // swipe-to-refresh: color scheme (not accessible in xml)
        swipeRefresh.setColorSchemeColors(MaterialColors.getColor(this, R.attr.colorOnPrimary, null))
        swipeRefresh.setProgressBackgroundColorSchemeColor(MaterialColors.getColor(this, R.attr.colorPrimary, null))

        // swipe to refresh
        swipeRefresh.setOnRefreshListener {
            // update
            viewModel.forceUpdateExchangeRate()
            swipeRefresh.isRefreshing = false
        }

        // heavy lifting
        observe()
    }

    override fun onResume() {
        super.onResume()
        updateSubtitle()
        // rebind rows: pick up amounts edited via the "change amount" screen
        adapter.refresh()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.rates_list, menu)
        this.menuItemRefresh = menu.findItem(R.id.refresh)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_add -> {
                showAddCurrencyDialog()
                true
            }
            R.id.refresh -> {
                viewModel.forceUpdateExchangeRate()
                true
            }
            R.id.settings -> {
                startActivity(Intent(this, PreferenceActivity::class.java))
                true
            }
            R.id.date_picker -> {
                showHistoricalDateDialog()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    // hamburger: placeholder navigation, until a proper drawer exists
    override fun onSupportNavigateUp(): Boolean {
        startActivity(Intent(this, PreferenceActivity::class.java))
        return true
    }

    private fun openCurrency(currency: Currency) {
        startActivity(
            Intent(this, MainActivity::class.java).apply {
                putExtra(ARG_TAPPED_CURRENCY, currency.iso4217Alpha())
            }
        )
    }

    private fun showAddCurrencyDialog() {
        val rates = viewModel.getExchangeRates().value?.rates ?: return

        // view
        val view = layoutInflater.inflate(R.layout.dialog_add_currency, null)
        val chipGroup: ChipGroup = view.findViewById(R.id.chipGroup)
        val searchView: SearchView = view.findViewById(R.id.searchView)
        val btnSearch: FloatingActionButton = view.findViewById(R.id.btnSearch)
        val listView: RecyclerView = view.findViewById(R.id.listView)

        // list: only the selected category's entries + live search
        val adapter = AddCurrencyDialogAdapter(this)
        listView.layoutManager = LinearLayoutManager(this)
        listView.adapter = adapter
        adapter.onItemToggled = { currency -> viewModel.toggleCurrencyStar(currency) }
        adapter.setRates(rates)

        // category chips: hide categories without entries, preselect the first available one
        val chips = mapOf(
            AddCurrencyDialogAdapter.AddGroup.CURRENCIES to view.findViewById<Chip>(R.id.chipCurrencies),
            AddCurrencyDialogAdapter.AddGroup.CRYPTO to view.findViewById<Chip>(R.id.chipCrypto),
            AddCurrencyDialogAdapter.AddGroup.COMMODITIES to view.findViewById<Chip>(R.id.chipCommodities),
            AddCurrencyDialogAdapter.AddGroup.METALS to view.findViewById<Chip>(R.id.chipMetals)
        )
        // category tabs: ALWAYS visible, so the user sees the full tab structure
        // (Währungen/Krypto/Rohstoffe/Edelmetalle) even when a category momentarily has
        // no entries (e.g. a supplementary source failed to load). An empty category
        // simply shows no entries instead of hiding the tab.
        chips.values.forEach { it.visibility = View.VISIBLE }
        val initialGroup = adapter.getCategories().firstOrNull()
            ?: AddCurrencyDialogAdapter.AddGroup.CURRENCIES
        chips.getValue(initialGroup).isChecked = true
        adapter.setCategory(initialGroup)

        // chip tap: show only the selected category (an open search stays open and keeps filtering)
        chipGroup.setOnCheckedStateChangeListener { _, checkedIds ->
            chips.entries.firstOrNull { it.value.id in checkedIds }
                ?.let { adapter.setCategory(it.key) }
        }

        // search FAB: toggles the search field, which live-filters the current category
        btnSearch.setOnClickListener {
            val opening = searchView.visibility != View.VISIBLE
            searchView.visibility = if (opening) View.VISIBLE else View.GONE
            btnSearch.setImageResource(if (opening) R.drawable.ic_close else R.drawable.ic_search)
            if (opening) {
                searchView.requestFocus()
            } else {
                searchView.setQuery(null, false)
                adapter.filter(null)
                searchView.clearFocus()
            }
        }

        // live search: filter the selected category as the user types
        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextChange(query: String?): Boolean {
                adapter.filter(query)
                return true
            }

            override fun onQueryTextSubmit(query: String?): Boolean {
                searchView.clearFocus()
                return true
            }
        })
        searchView.clearFocus()

        // keep the selected state in sync while the dialog is open
        val starObserver = Observer<Set<Currency>> { adapter.setStars(it) }
        viewModel.getStarredCurrencies().observe(this, starObserver)

        // build dialog
        val dialog = AlertDialog.Builder(this)
            .setTitle(R.string.add_currency)
            .setView(view)
            .setPositiveButton(android.R.string.ok, null)
            .create()
        // the rates list re-renders on its own (it observes stars via getRows())
        dialog.setOnDismissListener {
            viewModel.getStarredCurrencies().removeObserver(starObserver)
        }
        dialog.show()
        dialog.window?.setLayout(
            (resources.displayMetrics.widthPixels * 0.9f).toInt(),
            (resources.displayMetrics.heightPixels * 0.6f).toInt()
        )
    }

    private fun showHistoricalDateDialog() {
        // allow historical rates back until 2010-01-01, as every API at least provides a subset of rates since then
        val startDate = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
            .apply { this.set(2010, Calendar.JANUARY, 1) }
            .timeInMillis

        // load all the views
        val layout = layoutInflater.inflate(R.layout.main_dialog_historical_rates, null)
        val toggle: SwitchMaterial = layout.findViewById(R.id.toggle)
        val datePicker: DatePicker = layout.findViewById(R.id.date_picker)
        val border: View = layout.findViewById(R.id.border)

        val historicalDate = viewModel.getHistoricalDate()

        // enables/disables the date picker and the border on top of it
        fun showDatePicker(show: Boolean) {
            datePicker.visibility = if (show) View.VISIBLE else View.GONE
            border.visibility = if (show) View.VISIBLE else View.GONE
        }
        // initial dialog state
        showDatePicker(historicalDate != null)
        // configure the date picker
        datePicker.apply {
            minDate = startDate
            maxDate = Calendar.getInstance().timeInMillis
            firstDayOfWeek = Calendar.getInstance().firstDayOfWeek
            historicalDate?.let {
                updateDate(it.year, it.monthValue - 1, it.dayOfMonth)
            }
        }
        // configure the toggle button
        toggle.apply {
            setOnCheckedChangeListener { _, enabled -> showDatePicker(enabled) }
            isChecked = historicalDate != null
        }
        // finally, build the dialog and show it
        AlertDialog.Builder(this)
            .setTitle(R.string.historical_rates_dialog_title)
            .setView(layout)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                viewModel.setHistoricalDate(
                    // use historical
                    if (toggle.isChecked) LocalDate.of(
                        datePicker.year,
                        datePicker.month + 1,
                        datePicker.dayOfMonth
                    )
                    // use current
                    else null
                )
            }
            .setNegativeButton(android.R.string.cancel, null)
            .create()
            .show()
    }

    private fun observe() {
        // rows changed
        viewModel.getRows().observe(this) {
            adapter.setItems(it.rates, it.base, it.baseRateValue)
            updateSubtitle()
        }

        // the amount the row amounts are derived from
        viewModel.getCurrentBaseValueAsNumber().observe(this) {
            adapter.setBaseValue(it)
        }

        // rates are updating
        viewModel.isUpdating().observe(this) { isRefreshing ->
            refreshIndicator.visibility = if (isRefreshing) View.VISIBLE else View.GONE
            // disable manual refresh, while refreshing
            swipeRefresh.isEnabled = isRefreshing.not()
            menuItemRefresh?.isEnabled = isRefreshing.not()
            if (!isRefreshing) updateSubtitle()
        }

        // something bad happened
        viewModel.getError().observe(this) {
            it?.let {
                Snackbar.make(this, findViewById(R.id.snackbar_top_position), HtmlCompat.fromHtml(it, HtmlCompat.FROM_HTML_MODE_LEGACY), Snackbar.LENGTH_INDEFINITE) // show for 5s
                    .setBackgroundTint(MaterialColors.getColor(this, R.attr.colorError, null))
                    .setTextColor(MaterialColors.getColor(this, R.attr.colorOnError, null))
                    .setActionTextColor(MaterialColors.getColor(this, R.attr.colorOnError, null))
                    .setAction(android.R.string.ok) { /* onClick dismisses, by default */ }
                    .setTextMaxLines(20)
                    .show()
            }
        }
    }

    private fun updateSubtitle() {
        val lastUpdate = File(applicationInfo.dataDir, "shared_prefs/rates.xml")
            .takeIf { it.exists() }
            ?.lastModified() ?: 0L
        findViewById<TextView>(R.id.textUpdated).apply {
            if (lastUpdate == 0L) {
                visibility = View.GONE
            } else {
                visibility = View.VISIBLE
                text = lastUpdate.toRelativeTimeString(this@RatesListActivity)
            }
        }
    }

}
