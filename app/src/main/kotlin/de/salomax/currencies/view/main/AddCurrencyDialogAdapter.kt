package de.salomax.currencies.view.main

import android.annotation.SuppressLint
import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.imageview.ShapeableImageView
import de.salomax.currencies.R
import de.salomax.currencies.model.Currency
import de.salomax.currencies.model.Rate

/**
 * Adapter of the add-currency dialog: shows all available rates grouped into sections
 * (currencies / precious metals / crypto / commodities), each with its own header,
 * and supports live-filtering by full name or ISO code while the user types.
 */
@SuppressLint("NotifyDataSetChanged")
class AddCurrencyDialogAdapter(private val context: Context) :
    RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    // listeners
    var onItemToggled: ((Currency) -> Unit)? = null

    // the section an entry belongs to (order = display order)
    private enum class AddGroup {
        CURRENCIES, METALS, CRYPTO, COMMODITIES
    }

    // the flat list of rows the dialog shows: section headers + pickable entries
    private sealed class Row {
        class Header(val title: String) : Row()
        class Entry(val currency: Currency) : Row()
    }

    private var groups: Map<AddGroup, List<Currency>> = emptyMap()
    private var rows: List<Row> = emptyList()
    private var stars: Set<Currency> = emptySet()
    private var filterText: String? = null

    private val drawableStar = ContextCompat.getDrawable(context, R.drawable.ic_favorite)
    private val drawableStarEmpty = ContextCompat.getDrawable(context, R.drawable.ic_favorite_empty)

    override fun getItemViewType(position: Int): Int {
        return if (rows[position] is Row.Header) 0 else 1
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(context)
        return when (viewType) {
            // section header
            0 -> ViewHolderHeader(inflater.inflate(R.layout.row_add_currency_header, parent, false))
            // pickable entry
            else -> ViewHolderEntry(inflater.inflate(R.layout.row_currency_dropdown, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val row = rows[position]) {
            is Row.Header -> (holder as ViewHolderHeader).textView.text = row.title
            is Row.Entry -> (holder as ViewHolderEntry).bind(row.currency)
        }
    }

    override fun getItemCount(): Int {
        return rows.size
    }

    fun setRates(rates: List<Rate>?) {
        groups = (rates?.map { it.currency } ?: emptyList())
            // XPD/XPT have no data source: keep them hidden
            .filter { it != Currency.XPD && it != Currency.XPT }
            .distinct()
            .groupBy { groupOf(it) }
            .mapValues { (_, currencies) -> currencies.sortedBy { it.iso4217Alpha() } }
        rebuild()
    }

    fun setStars(stars: Set<Currency>?) {
        this.stars = stars ?: emptySet()
        rebuild()
    }

    fun filter(query: String?) {
        filterText = query?.trim()?.takeIf { it.isNotEmpty() }
        rebuild()
    }

    // live search: substring match against the localized full name AND the ISO code
    private fun matches(currency: Currency): Boolean {
        val query = filterText ?: return true
        return currency.fullName(context).contains(query, ignoreCase = true)
            || currency.iso4217Alpha().contains(query, ignoreCase = true)
    }

    // rebuild the visible rows: every group keeps its header, but only if it has matching entries
    private fun rebuild() {
        val newRows = mutableListOf<Row>()
        for (group in AddGroup.entries) {
            val entries = groups[group].orEmpty().filter { matches(it) }
            if (entries.isNotEmpty()) {
                newRows.add(Row.Header(groupTitle(group)))
                entries.forEach { newRows.add(Row.Entry(it)) }
            }
        }
        rows = newRows
        notifyDataSetChanged()
    }

    private fun groupTitle(group: AddGroup): String {
        return context.getString(
            when (group) {
                AddGroup.CURRENCIES -> R.string.add_currency_section_currencies
                AddGroup.METALS -> R.string.add_currency_section_metals
                AddGroup.CRYPTO -> R.string.add_currency_section_crypto
                AddGroup.COMMODITIES -> R.string.add_currency_section_commodities
            }
        )
    }

    /**
     * classify a [Currency] into its picker section: gold/silver → metals, bitcoin → crypto,
     * brent oil → commodities, everything else → fiat currencies.
     * XPD/XPT are already filtered out (no data source), but would belong to METALS.
     */
    private fun groupOf(currency: Currency): AddGroup {
        return when (currency) {
            Currency.XAU, Currency.XAG -> AddGroup.METALS
            Currency.BTC -> AddGroup.CRYPTO
            Currency.XBZ -> AddGroup.COMMODITIES
            else -> AddGroup.CURRENCIES
        }
    }

    inner class ViewHolderHeader(itemView: View) : RecyclerView.ViewHolder(itemView) {

        val textView: TextView = itemView.findViewById(R.id.text_header)
    }

    inner class ViewHolderEntry(itemView: View) : RecyclerView.ViewHolder(itemView) {

        val ivFlag: ShapeableImageView = itemView.findViewById(R.id.image)
        val tvCode: TextView = itemView.findViewById(R.id.text2)
        val tvName: TextView = itemView.findViewById(R.id.text)
        val btnStar: ImageButton = itemView.findViewById(R.id.btn_fav)

        fun bind(currency: Currency) {
            // asset icons (bitcoin/gold/silver/oil) instead of the gray flag_unknown globe
            ivFlag.setImageDrawable(currency.icon(context))
            // ISO 4217 currency code ("XAU")
            tvCode.text = currency.iso4217Alpha()
            // full name ("Gold Ounce")
            tvName.text = currency.fullName(context)
            // selected state: starred = added to the rates list
            btnStar.setImageDrawable(
                if (currency in stars) drawableStar else drawableStarEmpty
            )
        }

        init {
            // tapping the row or the star toggles the currency (add/remove)
            val toggle = {
                rows.getOrNull(layoutPosition)
                    ?.let { it as? Row.Entry }
                    ?.let { onItemToggled?.invoke(it.currency) }
            }
            itemView.setOnClickListener { toggle() }
            btnStar.setOnClickListener { toggle() }
        }
    }

}
